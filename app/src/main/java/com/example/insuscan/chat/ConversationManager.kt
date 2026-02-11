package com.example.insuscan.chat

import android.content.Context
import com.example.insuscan.meal.FoodItem
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.FileLogger

import com.example.insuscan.utils.InsulinCalculatorUtil
import com.example.insuscan.network.dto.ChatParseResponseDto
import com.example.insuscan.network.dto.ChatFoodEntryDto

// State machine for the chat conversation flow.
// Drives state transitions and generates bot responses.
class ConversationManager(private val context: Context) {

    var currentState: ChatState = ChatState.AWAITING_IMAGE
        private set

    // Clarification context
    private var pendingClarificationContext: String? = null
    private var lastUserText: String = ""

    // Collected data during the conversation (exposed for meal building)
    var collectedGlucose: Int? = null
        private set
    var collectedActivityLevel: String = "normal"
        private set
    var lastDoseResult: InsulinCalculatorUtil.DoseResult? = null
        private set

    // Callback interface so the ViewModel can react to events
    interface Callback {
        fun onBotMessage(message: ChatMessage)
        fun onStateChanged(newState: ChatState)
        fun onNeedLlmParse(text: String, state: ChatState) {} 
        fun onStickyAction(actions: List<ActionButton>?) {} // New: update sticky buttons
    }

    private fun setActions(actions: List<ActionButton>) {
        callback?.onStickyAction(actions)
    }

    private fun clearActions() {
        callback?.onStickyAction(emptyList())
    }

    var callback: Callback? = null

    // Start a new conversation
    private var hasStarted = false

    fun startConversation() {
        if (hasStarted || currentState != ChatState.AWAITING_IMAGE) return
        hasStarted = true

        currentState = ChatState.AWAITING_IMAGE
        callback?.onBotMessage(
            ChatMessage.BotText(text = "Hey! 👋 I'm your meal assistant.\nTake a photo of your meal or pick one from gallery to get started.")
        )

        setActions(listOf(
            ActionButton("take_photo", "📷 Take Photo"),
            ActionButton("pick_gallery", "🖼️ Gallery")
        ))

        callback?.onStateChanged(currentState)
    }

    // Called when the scan returns successfully
    fun onScanSuccess(meal: Meal) {
        FileLogger.log("CHAT", "Scan success: ${meal.foodItems?.size} items, ${meal.carbs}g carbs")

        val items = meal.foodItems
        if (items.isNullOrEmpty()) {
            currentState = ChatState.AWAITING_IMAGE
            callback?.onBotMessage(
                ChatMessage.BotText(text = "Hmm, I couldn't detect any food in that image. Try again with a clearer photo?")
            )
            callback?.onStateChanged(currentState)
            return
        }

        MealSessionManager.setCurrentMeal(meal)

        val totalCarbs = items.sumOf { (it.carbsGrams ?: 0f).toDouble() }.toFloat()
        currentState = ChatState.REVIEWING_FOOD
        callback?.onBotMessage(
            ChatMessage.BotText(text = "Here's what I found in your meal:")
        )
        callback?.onBotMessage(
            ChatMessage.BotFoodCard(foodItems = items, totalCarbs = totalCarbs)
        )

        val missingCarbs = items.filter { (it.carbsGrams ?: 0f) == 0f }
        if (missingCarbs.isNotEmpty()) {
            val names = missingCarbs.joinToString(", ") { it.name }
            callback?.onBotMessage(
                ChatMessage.BotText(text = "⚠️ I couldn't find carb data for: $names.\nYou can edit them or continue without.")
            )
        }

        callback?.onBotMessage(
            ChatMessage.BotText(text = "Does this look right? Confirm to continue, or edit the items.")
        )
        setActions(listOf(
            ActionButton("confirm_food", "Confirm"),
            ActionButton("edit_food", "Edit Items")
        ))
        callback?.onStateChanged(currentState)
    }

    // Called when the scan fails
    fun onScanError(errorMessage: String) {
        FileLogger.log("CHAT", "Scan error: $errorMessage")
        currentState = ChatState.AWAITING_IMAGE
        callback?.onBotMessage(
            ChatMessage.BotText(text = "Something went wrong analyzing the image: $errorMessage\nTry again?")
        )
        callback?.onStateChanged(currentState)
    }

    // Called when user confirms the food list
    fun onFoodConfirmed() {
        if (currentState != ChatState.REVIEWING_FOOD) return

        val pm = UserProfileManager
        val icr = pm.getGramsPerUnit(context)
        val isf = pm.getCorrectionFactor(context)
        val targetGlucose = pm.getTargetGlucose(context)
        val glucoseUnits = pm.getGlucoseUnits(context)

        if (icr == null || isf == null || targetGlucose == null) {
            callback?.onBotMessage(
                ChatMessage.BotText(text = "⚠️ Your medical profile is incomplete. Please set ICR, ISF, and target glucose in your Profile first.")
            )
            return
        }

        currentState = ChatState.REVIEWING_MEDICAL
        callback?.onBotMessage(
            ChatMessage.BotMedicalCard(
                icr = icr,
                isf = isf,
                targetGlucose = targetGlucose,
                glucoseUnits = glucoseUnits
            )
        )
        callback?.onBotMessage(
            ChatMessage.BotText(text = "These are your current settings.")
        )
        setActions(listOf(
            ActionButton("confirm_medical", "Confirm"),
            ActionButton("edit_medical", "Edit Settings")
        ))
        callback?.onStateChanged(currentState)
    }

    // Called when user confirms medical settings
    fun onMedicalConfirmed() {
        if (currentState != ChatState.REVIEWING_MEDICAL) return

        currentState = ChatState.COLLECTING_EXTRAS
        callback?.onBotMessage(
            ChatMessage.BotText(text = "What's your current glucose level? (Type a number or tap 'Skip')")
        )
        setActions(listOf(
            ActionButton("skip", "Skip")
        ))
        callback?.onStateChanged(currentState)
    }

    // Called when user requests to edit medical settings via sticky button
    fun onRequestMedicalEdit() {
        if (currentState != ChatState.REVIEWING_MEDICAL) return
        
        callback?.onBotMessage(
            ChatMessage.BotText(text = "Sure. Type the new values (e.g. 'ICR 10', 'Target 110').")
        )
    }

    // Called when user provides glucose
    fun onGlucoseProvided(glucose: Int?) {
        clearActions()
        collectedGlucose = glucose

        if (glucose != null) {
            callback?.onBotMessage(
                ChatMessage.BotText(text = "Got it — glucose: $glucose")
            )
        }

        // Ask about activity
        callback?.onBotMessage(
            ChatMessage.BotText(text = "Any activity adjustments?")
        )
        setActions(listOf(
            ActionButton("normal", "None"),
            ActionButton("light", "Light Exercise"),
            ActionButton("intense", "Intense Exercise")
        ))
    }

    // Called when user selects activity level
    fun onActivitySelected(activity: String) {
        clearActions()
        collectedActivityLevel = activity
        performCalculation()
    }

    var collectedSickMode = false
    var collectedStressMode = false

    fun toggleSickMode() {
        collectedSickMode = !collectedSickMode
        performCalculation()
    }

    fun toggleStressMode() {
        collectedStressMode = !collectedStressMode
        performCalculation()
    }

    // Called when user sends free text during COLLECTING_EXTRAS
    fun onCollectingExtrasText(text: String) {
        val lower = text.lowercase().trim()

        // Glucose response
        if (lower == "skip" || lower == "no" || lower == "none") {
            onGlucoseProvided(null)
            return
        }

        val number = lower.toIntOrNull()
        if (number != null) {
            onGlucoseProvided(number)
            return
        }

        callback?.onBotMessage(
            ChatMessage.BotText(text = "Please enter a number for your glucose level, or type 'skip'.")
        )
    }

    // Perform the insulin calculation
    private fun performCalculation() {
        currentState = ChatState.CALCULATING
        callback?.onStateChanged(currentState)

        val meal = MealSessionManager.currentMeal


        val totalCarbs = meal?.foodItems?.sumOf { (it.carbsGrams ?: 0f).toDouble() }?.toFloat() ?: meal?.carbs ?: 0f
        val gramsPerUnit = UserProfileManager.getGramsPerUnit(context) ?: 10f

        val result = InsulinCalculatorUtil.calculate(
            context = context,
            carbs = totalCarbs,
            glucose = collectedGlucose,
            activeInsulin = null,
            activityLevel = collectedActivityLevel,
            gramsPerUnit = gramsPerUnit,
            isSick = collectedSickMode,
            isStress = collectedStressMode
        )

        lastDoseResult = result
        currentState = ChatState.SHOWING_RESULT
        callback?.onBotMessage(
            ChatMessage.BotText(text = "Here's your dose calculation:")
        )
        callback?.onBotMessage(
            ChatMessage.BotDoseResult(doseResult = result)
        )
        
        // Show sticky actions for adjustments + save
        setActions(listOf(
            ActionButton("save_meal", "✅ Save"),
            ActionButton("activity_menu", "🏃 Activity"),
            ActionButton("sick_toggle", if (collectedSickMode) "No Sick" else "🤒 Sick"),
            ActionButton("stress_toggle", if (collectedStressMode) "No Stress" else "😫 Stress")
        ))
        
        callback?.onBotMessage(
            ChatMessage.BotText(text = "Does this look right? You can adjust the settings or tap Save to finish.")
        )
        
        callback?.onStateChanged(currentState)
    }

    fun onShowActivityMenu() {
        setActions(listOf(
            ActionButton("light", "Light Ex."),
            ActionButton("intense", "Intense Ex."),
            ActionButton("normal", "No Ex. (Reset)"),
            ActionButton("back_to_result", "Back")
        ))
    }

    fun onBackToResult() {
        performCalculation()
    }

    // Called when user saves the meal
    fun onMealSaved() {
        currentState = ChatState.DONE
        callback?.onBotMessage(
            ChatMessage.BotSaved(text = "Meal saved successfully! ✅")
        )
        setActions(listOf(
            ActionButton("new_scan", "New Scan"),
            ActionButton("history", "View History")
        ))
        callback?.onStateChanged(currentState)

        // Reset for next conversation
        collectedGlucose = null
        collectedActivityLevel = "normal"
        currentState = ChatState.AWAITING_IMAGE
    }

    // Handle free text based on current state
    fun handleFreeText(text: String) {
        clearActions()
        
        // Context handling for clarification
        val textToProcess = if (pendingClarificationContext != null) {
             val combined = "$pendingClarificationContext. $text"
             pendingClarificationContext = null
             combined
        } else {
             lastUserText = text
             text
        }

        val parsed = FreeTextParser.parse(textToProcess, currentState)

        when (currentState) {
            ChatState.REVIEWING_FOOD -> {
                when (parsed) {
                    is FreeTextParser.ParseResult.Confirm -> onFoodConfirmed()
                    is FreeTextParser.ParseResult.EditMode -> callback?.onNeedLlmParse(textToProcess, currentState)
                    is FreeTextParser.ParseResult.Unknown -> callback?.onNeedLlmParse(textToProcess, currentState)
                    else -> callback?.onNeedLlmParse(textToProcess, currentState)
                }
            }
            ChatState.REVIEWING_MEDICAL -> {
                when (parsed) {
                    is FreeTextParser.ParseResult.Confirm -> onMedicalConfirmed()
                    is FreeTextParser.ParseResult.Unknown -> callback?.onNeedLlmParse(text, currentState)
                    else -> callback?.onBotMessage(
                        ChatMessage.BotText(text = "Confirm your medical settings to continue, or go to Profile to update them.")
                    )
                }
            }
            ChatState.COLLECTING_EXTRAS -> {
                onCollectingExtrasText(text)
            }
            ChatState.SHOWING_RESULT -> {
                callback?.onBotMessage(
                    ChatMessage.BotText(text = "Use the 'Save Meal' button to save, or take another photo to start over.")
                )
            }
            ChatState.AWAITING_IMAGE, ChatState.IDLE -> {
                // Try LLM parse for food descriptions even in AWAITING_IMAGE
                callback?.onNeedLlmParse(text, currentState)
            }
            else -> {
                callback?.onNeedLlmParse(text, currentState)
            }
        }
    }

    // Handle LLM parse result
    fun handleLlmResponse(resp: ChatParseResponseDto) {
        // Clarify logic
        if (resp.action == "clarify") {
            pendingClarificationContext = lastUserText
            callback?.onBotMessage(
                ChatMessage.BotText(text = resp.message ?: "Could you specify the amount?")
            )
            return
        }

        // Unknown logic
        if (resp.action == "unknown") {
            callback?.onBotMessage(
                ChatMessage.BotText(text = resp.message ?: "I didn't catch that. Could you rephrase?")
            )
            restoreActions()
            return
        }

        // Clear context if successful
        if (pendingClarificationContext != null) pendingClarificationContext = null

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
                onLlmFoodItems(items, merge = true)
            }
            "set_medical_params" -> {
                onMedicalParamsUpdated(resp.icr, resp.isf, resp.targetGlucose)
            }
            "set_glucose" -> {
                onGlucoseProvided(resp.glucose)
            }
            "set_activity" -> {
                if (resp.activity != null) onActivitySelected(resp.activity)
            }
            "confirm" -> {
                if (currentState == ChatState.REVIEWING_FOOD) onFoodConfirmed()
                else if (currentState == ChatState.REVIEWING_MEDICAL) onMedicalConfirmed()
            }
            else -> {
                if (!resp.message.isNullOrBlank()) {
                    callback?.onBotMessage(ChatMessage.BotText(text = resp.message))
                } else if (resp.action == "unknown") {
                    callback?.onBotMessage(ChatMessage.BotText(text = "I didn't understand. Could you rephrase?"))
                }
                restoreActions()
            }
        }
    }

    private fun restoreActions() {
        val actions = when (currentState) {
            ChatState.REVIEWING_FOOD -> listOf(
                ActionButton("confirm_food", "Confirm"),
                ActionButton("edit_food", "Edit Items")
            )
            ChatState.REVIEWING_MEDICAL -> listOf(
                ActionButton("confirm_medical", "Confirm"),
                ActionButton("edit_medical", "Edit Settings")
            )
            ChatState.SHOWING_RESULT -> listOf(
                ActionButton("save_meal", "✅ Save"),
                ActionButton("activity_menu", "🏃 Activity"),
                ActionButton("sick_toggle", if (collectedSickMode) "No Sick" else "🤒 Sick"),
                ActionButton("stress_toggle", if (collectedStressMode) "No Stress" else "😫 Stress")
            )
            else -> emptyList()
        }
        if (actions.isNotEmpty()) {
            setActions(actions)
            callback?.onStateChanged(currentState) // Refresh UI
        }
    }

    // Handle LLM parse result for add_food action
    fun onLlmFoodItems(items: List<FoodItem>, merge: Boolean = false) {
        if (items.isEmpty()) {
            callback?.onBotMessage(
                ChatMessage.BotText(text = "I couldn't identify any food items. Try taking a photo instead!")
            )
            return
        }

        if (merge && currentState == ChatState.REVIEWING_FOOD && MealSessionManager.currentMeal != null) {
            val currentMeal = MealSessionManager.currentMeal!!
            val currentItems = currentMeal.foodItems?.toMutableList() ?: mutableListOf()
            currentItems.addAll(items)

            val totalCarbs = currentItems.sumOf { (it.carbsGrams ?: 0f).toDouble() }.toFloat()
            val updatedMeal = currentMeal.copy(foodItems = currentItems, carbs = totalCarbs)
            MealSessionManager.setCurrentMeal(updatedMeal)

            callback?.onBotMessage(
                ChatMessage.BotText(text = "Added: ${items.joinToString(", ") { it.name }}")
            )
            onMealUpdated()
            return
        }

        // Build a pseudo-meal from text-described food items
        val totalCarbs = items.sumOf { (it.carbsGrams ?: 0f).toDouble() }.toFloat()
        val meal = Meal(
            title = "Manual Entry",
            foodItems = items,
            carbs = totalCarbs
        )
        MealSessionManager.setCurrentMeal(meal)

        currentState = ChatState.REVIEWING_FOOD
        callback?.onBotMessage(
            ChatMessage.BotText(text = "Here's what I understood from your description:")
        )
        callback?.onBotMessage(
            ChatMessage.BotFoodCard(foodItems = items, totalCarbs = totalCarbs)
        )
        callback?.onBotMessage(
            ChatMessage.BotText(text = "Does this look right? Confirm to continue, or edit the items.")
        )
        setActions(listOf(
            ActionButton("confirm_food", "Confirm"),
            ActionButton("edit_food", "Edit Items")
        ))
        callback?.onStateChanged(currentState)
    }

    // Called after manual edit
    fun onMealUpdated() {
        val meal = MealSessionManager.currentMeal ?: return
        val items = meal.foodItems ?: emptyList()
        val totalCarbs = meal.carbs

        callback?.onBotMessage(
            ChatMessage.BotText(text = "Updated meal:")
        )
        callback?.onBotMessage(
            ChatMessage.BotFoodCard(foodItems = items, totalCarbs = totalCarbs)
        )
        callback?.onBotMessage(
            ChatMessage.BotText(text = "Does this look right? Confirm to continue.")
        )
        setActions(listOf(
            ActionButton("confirm_food", "Confirm"),
            ActionButton("edit_food", "Edit Items")
        ))
    }

    // Called when LLM updates medical params
    fun onMedicalParamsUpdated(icr: Double?, isf: Double?, target: Int?) {
        val pm = UserProfileManager
        val ctx = context

        if (icr != null) pm.saveInsulinCarbRatio(ctx, icr.toString())
        if (isf != null) pm.saveCorrectionFactor(ctx, isf.toFloat())
        if (target != null) pm.saveTargetGlucose(ctx, target)

        callback?.onBotMessage(ChatMessage.BotText(text = "✅ Updated your medical profile."))

        // If currently reviewing medical, refresh the card
        if (currentState == ChatState.REVIEWING_MEDICAL) {
            onFoodConfirmed() // Re-triggers medical review/display
        } else if (currentState == ChatState.SHOWING_RESULT) {
            performCalculation() // Re-calculate with new profile
        }
    }
}
