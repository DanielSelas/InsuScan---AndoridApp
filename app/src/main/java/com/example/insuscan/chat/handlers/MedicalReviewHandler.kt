package com.example.insuscan.chat.handlers

import com.example.insuscan.chat.ActionButton
import com.example.insuscan.chat.ChatMessage
import com.example.insuscan.chat.ChatState
import com.example.insuscan.chat.ChipStyle
import com.example.insuscan.chat.ConversationManager
import com.example.insuscan.profile.UserProfileManager

class MedicalReviewHandler(private val manager: ConversationManager) {

    fun showMedicalReview() {
        val pm = UserProfileManager
        val icr = pm.getGramsPerUnit(manager.context)
        val isf = pm.getCorrectionFactor(manager.context)
        val targetGlucose = pm.getTargetGlucose(manager.context)
        val glucoseUnits = pm.getGlucoseUnits(manager.context)

        if (icr == null || isf == null || targetGlucose == null) {
            manager.callback?.onBotMessage(ChatMessage.BotText(text = "⚠️ Medical profile incomplete. Set ICR, ISF, and target in your Profile first."))
            manager.setActions(listOf(ActionButton("open_profile", "Open Profile", style = ChipStyle.PRIMARY)))
            return
        }

        manager.setCurrentState(ChatState.REVIEWING_MEDICAL)
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "📋 Step 1/4 — Medical Settings"))
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "Let's verify your medical settings are up to date."))
        manager.callback?.onBotMessage(ChatMessage.BotMedicalCard(icr = icr, isf = isf, targetGlucose = targetGlucose, glucoseUnits = glucoseUnits))
        showMedicalActions()
        manager.notifyStateChanged()
    }

    fun showMedicalActions() {
        manager.setActions(listOf(
            ActionButton("confirm_medical", "✅ Confirm", row = 0, style = ChipStyle.PRIMARY),
            ActionButton("edit_medical", "✏️ Edit", row = 0, style = ChipStyle.SECONDARY)
        ))
    }

    fun onMedicalConfirmed() {
        if (manager.currentState != ChatState.REVIEWING_MEDICAL) return
        manager.clearActions()
        if (manager.isEditingStep) {
            manager.isEditingStep = false
            manager.resultHandler.performCalculation()
        } else {
            manager.assessmentHandler.askGlucose()
        }
    }

    fun onRequestMedicalEdit() {
        if (manager.currentState != ChatState.REVIEWING_MEDICAL) return
        manager.setCurrentState(ChatState.EDITING_MEDICAL)
        manager.clearActions()
        manager.callback?.onRequestEditMedicalSheet()

        manager.setActions(listOf(
            ActionButton("cancel_edit_medical", "Cancel", style = ChipStyle.TERTIARY)
        ))
        manager.notifyStateChanged()
    }

    fun onMedicalParamsUpdated(icr: Double?, isf: Double?, target: Int?) {
        val pm = UserProfileManager
        if (icr != null) pm.saveInsulinCarbRatio(manager.context, icr.toString())
        if (isf != null) pm.saveCorrectionFactor(manager.context, isf.toFloat())
        if (target != null) pm.saveTargetGlucose(manager.context, target)

        manager.callback?.onBotMessage(ChatMessage.BotText(text = "✅ Profile updated."))

        manager.setCurrentState(ChatState.REVIEWING_MEDICAL)
        val newIcr = pm.getGramsPerUnit(manager.context)
        val newIsf = pm.getCorrectionFactor(manager.context)
        val newTarget = pm.getTargetGlucose(manager.context)
        val units = pm.getGlucoseUnits(manager.context)

        if (newIcr != null && newIsf != null && newTarget != null) {
            manager.callback?.onBotMessage(ChatMessage.BotMedicalCard(icr = newIcr, isf = newIsf, targetGlucose = newTarget, glucoseUnits = units))
        }
        showMedicalActions()
        manager.notifyStateChanged()
    }

    fun onCancelMedicalEdit() {
        manager.setCurrentState(ChatState.REVIEWING_MEDICAL)
        showMedicalActions()
        manager.notifyStateChanged()
    }
}
