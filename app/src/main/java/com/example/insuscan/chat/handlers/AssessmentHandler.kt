package com.example.insuscan.chat.handlers

import com.example.insuscan.chat.ActionButton
import com.example.insuscan.chat.ChatMessage
import com.example.insuscan.chat.ChatState
import com.example.insuscan.chat.ChipStyle
import com.example.insuscan.chat.ConversationManager
import com.example.insuscan.profile.UserProfileManager

class AssessmentHandler(private val manager: ConversationManager) {

    fun askGlucose() {
        manager.setCurrentState(ChatState.ASKING_GLUCOSE)
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "📋 Step 2/4 — Glucose Level"))
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "🩸 What's your current blood glucose level?\nType a number or tap Skip if you haven't measured."))
        manager.setActions(listOf(ActionButton("skip_glucose", "Skip", style = ChipStyle.TERTIARY)))
        manager.notifyStateChanged()
    }

    fun onGlucoseProvided(glucose: Int?) {
        manager.clearActions()
        manager.collectedGlucose = glucose
        if (glucose != null) {
            manager.callback?.onBotMessage(ChatMessage.BotText(text = "Got it — glucose: $glucose"))
        }
        if (manager.isEditingStep) {
            manager.isEditingStep = false
            manager.resultHandler.performCalculation()
        } else {
            askActivity()
        }
    }

    fun askActivity() {
        manager.setCurrentState(ChatState.ASKING_ACTIVITY)
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "📋 Step 3/4 — Activity Level"))
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "🏃 Have you done any exercise recently?\nThis helps adjust your insulin dose."))
        showActivityOptions()
        manager.notifyStateChanged()
    }

    fun showActivityOptions() {
        val pm = UserProfileManager
        val lightPct = pm.getLightExerciseAdjustment(manager.context)
        val intensePct = pm.getIntenseExerciseAdjustment(manager.context)

        manager.setActions(listOf(
            ActionButton("activity_none", "None", row = 0, style = ChipStyle.SECONDARY),
            ActionButton("activity_light", "🏃 Light (-${lightPct}%)", row = 0, style = ChipStyle.SECONDARY),
            ActionButton("activity_intense", "🏋️ Intense (-${intensePct}%)", row = 0, style = ChipStyle.SECONDARY),
            ActionButton("edit_activity_pct", "✏️ Edit %", row = 1, style = ChipStyle.TERTIARY)
        ))
    }

    fun onActivitySelected(activity: String) {
        manager.clearActions()
        manager.collectedActivityLevel = activity
        val pm = UserProfileManager
        val label = when (activity) {
            "light" -> "Light exercise (-${pm.getLightExerciseAdjustment(manager.context)}%)"
            "intense" -> "Intense exercise (-${pm.getIntenseExerciseAdjustment(manager.context)}%)"
            else -> "No exercise"
        }
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "Activity: $label"))

        if (manager.isEditingStep) {
            manager.isEditingStep = false
            manager.resultHandler.performCalculation()
        } else {
            askAdjustments()
        }
    }

    fun askAdjustments() {
        manager.setCurrentState(ChatState.ASKING_ADJUSTMENTS)
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "📋 Step 4/4 — Health Adjustments"))
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "🤒 Are you feeling sick or stressed today?\nThese factors can affect your insulin needs."))
        showAdjustmentOptions()
        manager.notifyStateChanged()
    }

    fun showAdjustmentOptions() {
        val pm = UserProfileManager
        val sickPct = pm.getSickDayAdjustment(manager.context)
        val stressPct = pm.getStressAdjustment(manager.context)

        manager.setActions(listOf(
            ActionButton("adj_none", "No", row = 0, style = ChipStyle.SECONDARY),
            ActionButton("adj_sick", "🤒 Sick (+${sickPct}%)", row = 0, style = ChipStyle.SECONDARY),
            ActionButton("adj_stress", "😫 Stress (+${stressPct}%)", row = 0, style = ChipStyle.SECONDARY),
            ActionButton("adj_both", "Both (+${sickPct + stressPct}%)", row = 1, style = ChipStyle.SECONDARY),
            ActionButton("edit_sick_stress_pct", "✏️ Edit %", row = 1, style = ChipStyle.TERTIARY)
        ))
    }

    fun onAdjustmentSelected(sick: Boolean, stress: Boolean) {
        manager.clearActions()
        manager.collectedSickMode = sick
        manager.collectedStressMode = stress

        val status = when {
            sick && stress -> "Sick & Stress"
            sick -> "Sick"
            stress -> "Stress"
            else -> "Neither"
        }
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "Adjustments: $status"))

        if (manager.isEditingStep) {
            manager.isEditingStep = false
            manager.resultHandler.performCalculation()
        } else {
            manager.foodHandler.proceedToFoodReview()
        }
    }

    fun onAdjustmentPercentagesUpdated() {
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "✅ Adjustment percentages updated."))
        
        if (manager.currentState == ChatState.ASKING_ADJUSTMENTS) {
            showAdjustmentOptions()
        } else if (manager.currentState == ChatState.ASKING_ACTIVITY) {
            showActivityOptions()
        } else if (manager.currentState == ChatState.SHOWING_RESULT) {
            manager.resultHandler.performCalculation()
        }
    }

    fun toggleSickMode() {
        manager.collectedSickMode = !manager.collectedSickMode
        val pm = UserProfileManager
        val pct = pm.getSickDayAdjustment(manager.context)
        val status = if (manager.collectedSickMode) "Sick mode ON (+${pct}%)" else "Sick mode OFF"
        manager.callback?.onBotMessage(ChatMessage.BotText(text = status))
        manager.resultHandler.performCalculation()
    }

    fun toggleStressMode() {
        manager.collectedStressMode = !manager.collectedStressMode
        val pm = UserProfileManager
        val pct = pm.getStressAdjustment(manager.context)
        val status = if (manager.collectedStressMode) "Stress mode ON (+${pct}%)" else "Stress mode OFF"
        manager.callback?.onBotMessage(ChatMessage.BotText(text = status))
        manager.resultHandler.performCalculation()
    }

    fun onEditAdjustments() {
        manager.callback?.onRequestEditAdjustmentsDialog()
    }
}
