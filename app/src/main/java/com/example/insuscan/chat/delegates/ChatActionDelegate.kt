package com.example.insuscan.chat.delegates

import com.example.insuscan.chat.ChatMessage
import com.example.insuscan.chat.ChatState
import com.example.insuscan.chat.ChatViewModel

class ChatActionDelegate(private val viewModel: ChatViewModel) {

    fun onActionButton(actionId: String) {
        val manager = viewModel.conversationManager
        when (actionId) {
            "take_photo" -> { viewModel.fireImagePickEvent("camera"); return }
            "pick_gallery" -> { viewModel.fireImagePickEvent("gallery"); return }

            "confirm_food" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "✅ Confirmed"))
                manager.onFoodConfirmed()
            }
            "edit_food" -> viewModel.fireEditFoodEvent()

            "confirm_medical" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "✅ Settings confirmed"))
                manager.onMedicalConfirmed()
            }
            "edit_medical" -> manager.onRequestMedicalEdit()
            "cancel_edit_medical" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "Cancel"))
                manager.onCancelMedicalEdit()
            }
            "open_profile" -> viewModel.fireNavigationEvent("profile")

            "skip_glucose" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "Skip"))
                manager.onGlucoseProvided(null)
            }

            "activity_none" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "No exercise"))
                if (manager.currentState == ChatState.ADJUSTING_ACTIVITY) manager.onActivityAdjusted("normal")
                else manager.onActivitySelected("normal")
            }
            "activity_light" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "Light exercise"))
                if (manager.currentState == ChatState.ADJUSTING_ACTIVITY) manager.onActivityAdjusted("light")
                else manager.onActivitySelected("light")
            }
            "activity_intense" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "Intense exercise"))
                if (manager.currentState == ChatState.ADJUSTING_ACTIVITY) manager.onActivityAdjusted("intense")
                else manager.onActivitySelected("intense")
            }
            "edit_activity_pct" -> viewModel.fireEditActivityEvent()

            "edit_step" -> manager.onChooseEditStep()
            "edit_step_wait" -> manager.onChooseEditStep()
            "edit_step_food" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "Edit food items"))
                manager.onEditStepFood()
            }
            "edit_step_medical" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "Edit medical settings"))
                manager.onEditStepMedical()
            }
            "edit_step_glucose" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "Edit glucose"))
                manager.onEditStepGlucose()
            }
            "edit_step_activity" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "Edit activity"))
                manager.onEditStepActivity()
            }
            "edit_step_adjustments" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "Edit adjustments"))
                manager.onEditStepAdjustments()
            }

            "adj_none" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "Neither"))
                manager.onAdjustmentSelected(sick = false, stress = false)
            }
            "adj_sick" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "Sick"))
                manager.onAdjustmentSelected(sick = true, stress = false)
            }
            "adj_stress" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "Stress"))
                manager.onAdjustmentSelected(sick = false, stress = true)
            }
            "adj_both" -> {
                viewModel.addMessage(ChatMessage.UserText(text = "Both"))
                manager.onAdjustmentSelected(sick = true, stress = true)
            }
            "edit_sick_stress_pct" -> viewModel.fireEditSickStressEvent()

            "activity_menu" -> manager.onAdjustActivity()
            "sick_toggle" -> manager.toggleSickMode()
            "stress_toggle" -> manager.toggleStressMode()
            "edit_adjustments" -> manager.onEditAdjustments()
            "back_to_result" -> manager.onBackToResult()

            "save_meal" -> viewModel.syncDelegate.onSaveMeal()

            "new_scan" -> {
                viewModel.clearMessages()
                manager.resetForNewScan()
            }
            "go_home" -> viewModel.fireNavigationEvent("home")
            "history" -> viewModel.fireNavigationEvent("history")
        }
    }
}
