package com.example.insuscan.chat.handlers

import com.example.insuscan.chat.ActionButton
import com.example.insuscan.chat.ChatMessage
import com.example.insuscan.chat.ChatState
import com.example.insuscan.chat.ChipStyle
import com.example.insuscan.chat.ConversationManager
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.InsulinCalculatorUtil

class ChatResultHandler(private val manager: ConversationManager) {

    fun performCalculation() {
        manager.setCurrentState(ChatState.CALCULATING)
        manager.notifyStateChanged()

        manager.callback?.onBotMessage(ChatMessage.BotText(text = "📊 All set! Calculating your dose based on your inputs…"))

        val meal = MealSessionManager.currentMeal
        val totalCarbs = meal?.foodItems?.sumOf { (it.carbsGrams ?: 0f).toDouble() }?.toFloat() ?: meal?.carbs ?: 0f
        val gramsPerUnit = UserProfileManager.getGramsPerUnit(manager.context) ?: 10f

        val result = InsulinCalculatorUtil.calculate(
            context = manager.context,
            carbs = totalCarbs,
            glucose = manager.collectedGlucose,
            activeInsulin = null,
            activityLevel = manager.collectedActivityLevel,
            gramsPerUnit = gramsPerUnit,
            isSick = manager.collectedSickMode,
            isStress = manager.collectedStressMode
        )

        manager.lastDoseResult = result
        manager.setCurrentState(ChatState.SHOWING_RESULT)

        manager.callback?.onBotMessage(ChatMessage.BotText(text = "── 📊 Results ──"))
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "Here's your recommended dose:"))
        manager.callback?.onBotMessage(ChatMessage.BotDoseResult(doseResult = result))

        showSummaryCard(result, totalCarbs)

        showResultActions()
        manager.notifyStateChanged()
    }

    private fun showSummaryCard(result: InsulinCalculatorUtil.DoseResult, totalCarbs: Float) {
        val pm = UserProfileManager
        val meal = MealSessionManager.currentMeal
        val items = meal?.foodItems ?: emptyList()

        manager.callback?.onBotMessage(
            ChatMessage.BotSummaryCard(
                foodItems = items,
                totalCarbs = totalCarbs,
                glucoseLevel = manager.collectedGlucose,
                activityLevel = manager.collectedActivityLevel,
                isSick = manager.collectedSickMode,
                isStress = manager.collectedStressMode,
                icr = pm.getGramsPerUnit(manager.context) ?: 0f,
                isf = pm.getCorrectionFactor(manager.context) ?: 0f,
                targetGlucose = pm.getTargetGlucose(manager.context) ?: 0,
                glucoseUnits = pm.getGlucoseUnits(manager.context),
                sickPct = if (manager.collectedSickMode) pm.getSickDayAdjustment(manager.context) else 0,
                stressPct = if (manager.collectedStressMode) pm.getStressAdjustment(manager.context) else 0,
                exercisePct = when (manager.collectedActivityLevel) {
                    "light" -> pm.getLightExerciseAdjustment(manager.context)
                    "intense" -> pm.getIntenseExerciseAdjustment(manager.context)
                    else -> 0
                },
                doseResult = result
            )
        )
    }

    fun showResultActions() {
        manager.setActions(listOf(
            ActionButton("save_meal", "💾 Save Meal", row = 0, style = ChipStyle.PRIMARY),
            ActionButton("edit_step", "✏️ Edit a Step", row = 1, style = ChipStyle.TERTIARY)
        ))
    }

    fun onChooseEditStep() {
        manager.setCurrentState(ChatState.CHOOSING_EDIT_STEP)
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "Which step would you like to edit?"))
        manager.setActions(listOf(
            ActionButton("edit_step_food", "🍽️ Food Items", row = 0, style = ChipStyle.SECONDARY),
            ActionButton("edit_step_medical", "⚕️ Medical", row = 0, style = ChipStyle.SECONDARY),
            ActionButton("edit_step_glucose", "🩸 Glucose", row = 0, style = ChipStyle.SECONDARY),
            ActionButton("edit_step_activity", "🏃 Activity", row = 1, style = ChipStyle.SECONDARY),
            ActionButton("edit_step_adjustments", "🔧 Adjustments", row = 1, style = ChipStyle.SECONDARY),
            ActionButton("back_to_result", "← Back", row = 2, style = ChipStyle.TERTIARY)
        ))
        manager.notifyStateChanged()
    }

    fun onEditStepFood() {
        manager.isEditingStep = true
        manager.clearActions()
        val meal = MealSessionManager.currentMeal
        val items = meal?.foodItems ?: emptyList()
        manager.foodHandler.showFoodReview(items)
    }

    fun onEditStepMedical() {
        manager.isEditingStep = true
        manager.clearActions()
        manager.medicalHandler.showMedicalReview()
    }

    fun onEditStepGlucose() {
        manager.isEditingStep = true
        manager.clearActions()
        manager.assessmentHandler.askGlucose()
    }

    fun onEditStepActivity() {
        manager.isEditingStep = true
        manager.clearActions()
        manager.assessmentHandler.askActivity()
    }

    fun onEditStepAdjustments() {
        manager.isEditingStep = true
        manager.clearActions()
        manager.assessmentHandler.askAdjustments()
    }

    fun onAdjustActivity() {
        manager.setCurrentState(ChatState.ADJUSTING_ACTIVITY)
        val pm = UserProfileManager
        val lightPct = pm.getLightExerciseAdjustment(manager.context)
        val intensePct = pm.getIntenseExerciseAdjustment(manager.context)

        manager.setActions(listOf(
            ActionButton("activity_none", "No Exercise", row = 0, style = ChipStyle.SECONDARY),
            ActionButton("activity_light", "🏃 Light (-${lightPct}%)", row = 0, style = ChipStyle.SECONDARY),
            ActionButton("activity_intense", "🏋️ Intense (-${intensePct}%)", row = 0, style = ChipStyle.SECONDARY),
            ActionButton("back_to_result", "← Back", row = 1, style = ChipStyle.TERTIARY)
        ))
        manager.notifyStateChanged()
    }

    fun onActivityAdjusted(activity: String) {
        manager.collectedActivityLevel = activity
        val pm = UserProfileManager
        val label = when (activity) {
            "light" -> "Light exercise (-${pm.getLightExerciseAdjustment(manager.context)}%)"
            "intense" -> "Intense exercise (-${pm.getIntenseExerciseAdjustment(manager.context)}%)"
            else -> "No exercise"
        }
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "Updated: $label"))
        performCalculation()
    }

    fun onBackToResult() {
        manager.setCurrentState(ChatState.SHOWING_RESULT)
        showResultActions()
        manager.notifyStateChanged()
    }

    fun onMealSaved() {
        manager.setCurrentState(ChatState.DONE)
        manager.callback?.onBotMessage(ChatMessage.BotSaved(text = "Meal saved! ✅"))
        manager.setActions(listOf(
            ActionButton("new_scan", "📷 New Scan", row = 0, style = ChipStyle.PRIMARY),
            ActionButton("history", "📋 History", row = 0, style = ChipStyle.SECONDARY),
            ActionButton("go_home", "🏠 Home", row = 1, style = ChipStyle.TERTIARY)
        ))
        manager.notifyStateChanged()

        manager.collectedGlucose = null
        manager.collectedActivityLevel = "normal"
        manager.collectedSickMode = false
        manager.collectedStressMode = false
    }
}
