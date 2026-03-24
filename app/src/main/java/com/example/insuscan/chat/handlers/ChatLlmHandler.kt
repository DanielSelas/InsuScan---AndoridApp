package com.example.insuscan.chat.handlers

import com.example.insuscan.chat.ActionButton
import com.example.insuscan.chat.ChatMessage
import com.example.insuscan.chat.ChatState
import com.example.insuscan.chat.ChipStyle
import com.example.insuscan.chat.ConversationManager
import com.example.insuscan.chat.FreeTextParser
import com.example.insuscan.meal.FoodItem
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.network.dto.ChatParseResponseDto

class ChatLlmHandler(private val manager: ConversationManager) {

    fun handleFreeText(text: String) {
        val textToProcess = if (manager.pendingClarificationContext != null) {
            val combined = "${manager.pendingClarificationContext}, $text"
            manager.pendingClarificationContext = null
            combined
        } else {
            text
        }
        manager.lastUserText = text

        val parsed = FreeTextParser.parse(textToProcess, manager.currentState)

        when (manager.currentState) {
            ChatState.REVIEWING_FOOD -> {
                when (parsed) {
                    is FreeTextParser.ParseResult.Confirm -> manager.foodHandler.onFoodConfirmed()
                    else -> {
                        manager.callback?.onRequestEditMealSheet()
                        manager.clearActions()
                        manager.callback?.onNeedLlmParse(textToProcess, manager.currentState)
                    }
                }
            }
            ChatState.REVIEWING_MEDICAL -> {
                when (parsed) {
                    is FreeTextParser.ParseResult.Confirm -> manager.medicalHandler.onMedicalConfirmed()
                    else -> {
                        manager.callback?.onRequestEditMedicalSheet()
                        manager.clearActions()
                        manager.callback?.onNeedLlmParse(textToProcess, manager.currentState)
                    }
                }
            }
            ChatState.EDITING_MEDICAL -> {
                manager.clearActions()
                manager.callback?.onNeedLlmParse(textToProcess, manager.currentState)
            }
            ChatState.ASKING_GLUCOSE -> {
                handleGlucoseText(text)
            }
            ChatState.ASKING_ACTIVITY -> {
                handleActivityText(text)
            }
            ChatState.SHOWING_RESULT -> {
                val lower = text.lowercase().trim()
                if (lower == "save" || lower == "yes" || lower == "confirm") {
                    manager.callback?.onBotMessage(ChatMessage.BotText(text = "Use the 💾 Save Meal button to save."))
                    manager.resultHandler.showResultActions()
                } else {
                    manager.callback?.onBotMessage(ChatMessage.BotText(text = "Adjust settings with the buttons below, or tap 💾 Save Meal."))
                    manager.resultHandler.showResultActions()
                }
            }
            ChatState.CLARIFYING -> {
                manager.clearActions()
                manager.callback?.onNeedLlmParse(textToProcess, manager.currentState)
            }
            ChatState.AWAITING_IMAGE -> {
                manager.clearActions()
                manager.callback?.onNeedLlmParse(textToProcess, manager.currentState)
            }
            ChatState.DONE -> {
                manager.callback?.onBotMessage(ChatMessage.BotText(text = "Start a new scan or check your history!"))
                manager.setActions(listOf(
                    ActionButton("new_scan", "📷 New Scan", row = 0),
                    ActionButton("history", "📋 History", row = 0),
                    ActionButton("go_home", "🏠 Home", row = 1)
                ))
            }
            else -> {
                manager.callback?.onNeedLlmParse(textToProcess, manager.currentState)
            }
        }
    }

    private fun handleGlucoseText(text: String) {
        val lower = text.lowercase().trim()
        if (lower == "skip" || lower == "no" || lower == "none") {
            manager.assessmentHandler.onGlucoseProvided(null)
            return
        }
        val number = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toIntOrNull()
        if (number != null) {
            manager.assessmentHandler.onGlucoseProvided(number)
        } else {
            manager.callback?.onBotMessage(ChatMessage.BotText(text = "Please type a number (e.g. 120) or 'skip'."))
        }
    }

    private fun handleActivityText(text: String) {
        val lower = text.lowercase().trim()
        when {
            lower.contains("none") || lower.contains("no") || lower == "skip" -> manager.assessmentHandler.onActivitySelected("normal")
            lower.contains("light") -> manager.assessmentHandler.onActivitySelected("light")
            lower.contains("intense") || lower.contains("heavy") || lower.contains("hard") -> manager.assessmentHandler.onActivitySelected("intense")
            else -> {
                manager.callback?.onBotMessage(ChatMessage.BotText(text = "Choose: None, Light, or Intense."))
            }
        }
    }

    fun handleLlmResponse(resp: ChatParseResponseDto) {
        if (resp.action == "clarify") {
            manager.pendingClarificationContext = manager.lastUserText
            manager.setCurrentState(ChatState.CLARIFYING)
            manager.callback?.onBotMessage(ChatMessage.BotText(text = resp.message ?: "Could you be more specific? (e.g. amount in grams)"))
            manager.notifyStateChanged()
            return
        }

        if (resp.action == "unknown") {
            manager.callback?.onBotMessage(ChatMessage.BotText(text = resp.message ?: "I didn't catch that. Try rephrasing?"))
            restoreCurrentStateActions()
            return
        }

        manager.pendingClarificationContext = null

        when (resp.action) {
            "add_food" -> {
                val items = resp.items?.map {
                    FoodItem(
                        name = it.name,
                        carbsGrams = it.estimatedCarbsGrams,
                        quantity = it.quantity?.toFloat() ?: 1f,
                        quantityUnit = null
                    )
                } ?: emptyList()

                val shouldMerge = (manager.currentState == ChatState.REVIEWING_FOOD ||
                        manager.currentState == ChatState.CLARIFYING) &&
                        MealSessionManager.currentMeal != null

                manager.foodHandler.onLlmFoodItems(items, merge = shouldMerge)
            }
            "set_medical_params" -> {
                manager.medicalHandler.onMedicalParamsUpdated(resp.icr, resp.isf, resp.targetGlucose)
            }
            "set_glucose" -> {
                resp.glucose?.let { manager.assessmentHandler.onGlucoseProvided(it) }
                    ?: manager.callback?.onBotMessage(ChatMessage.BotText(text = resp.message ?: "Couldn't parse glucose."))
            }
            "set_activity" -> {
                resp.activity?.let { manager.assessmentHandler.onActivitySelected(it) }
                    ?: manager.callback?.onBotMessage(ChatMessage.BotText(text = resp.message ?: "Couldn't parse activity."))
            }
            "confirm" -> {
                when (manager.currentState) {
                    ChatState.REVIEWING_FOOD -> manager.foodHandler.onFoodConfirmed()
                    ChatState.REVIEWING_MEDICAL -> manager.medicalHandler.onMedicalConfirmed()
                    else -> restoreCurrentStateActions()
                }
            }
            else -> {
                if (!resp.message.isNullOrBlank()) {
                    manager.callback?.onBotMessage(ChatMessage.BotText(text = resp.message))
                }
                restoreCurrentStateActions()
            }
        }
    }

    fun restoreCurrentStateActions() {
        when (manager.currentState) {
            ChatState.AWAITING_IMAGE -> manager.showAwaitingImageActions()
            ChatState.REVIEWING_FOOD -> manager.foodHandler.showFoodActions()
            ChatState.REVIEWING_MEDICAL -> manager.medicalHandler.showMedicalActions()
            ChatState.EDITING_MEDICAL -> manager.setActions(listOf(ActionButton("cancel_edit_medical", "Cancel", style = ChipStyle.TERTIARY)))
            ChatState.ASKING_GLUCOSE -> manager.setActions(listOf(ActionButton("skip_glucose", "Skip", style = ChipStyle.TERTIARY)))
            ChatState.ASKING_ACTIVITY -> manager.assessmentHandler.showActivityOptions()
            ChatState.SHOWING_RESULT -> manager.resultHandler.showResultActions()
            ChatState.ADJUSTING_ACTIVITY -> manager.resultHandler.onAdjustActivity()
            ChatState.DONE -> manager.setActions(listOf(
                ActionButton("new_scan", "📷 New Scan", style = ChipStyle.PRIMARY),
                ActionButton("history", "📋 History", style = ChipStyle.SECONDARY)
            ))
            else -> manager.clearActions()
        }
    }
}
