package com.example.insuscan.chat.delegates

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.example.insuscan.auth.AuthManager
import com.example.insuscan.chat.ChatMessage
import com.example.insuscan.chat.ChatViewModel
import com.example.insuscan.mapping.MealDtoMapper
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.network.repository.MealRepositoryImpl
import com.example.insuscan.network.repository.UserRepositoryImpl
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatSyncDelegate(private val viewModel: ChatViewModel, private val application: Application) {
    private val userRepository = UserRepositoryImpl()
    private val mealRepository = MealRepositoryImpl()

    fun updateMedicalSettings(icr: Double?, isf: Double?, target: Int?) {
        viewModel.conversationManager.onMedicalParamsUpdated(icr, isf, target)

        viewModel.viewModelScope.launch {
            try {
                val email = AuthManager.getUserEmail() ?: UserProfileManager.getUserEmail(application)
                if (email != null) {
                    val icrString = icr?.let { "1:${it.toInt()}" }
                    val dto = com.example.insuscan.network.dto.UserDto(
                        userId = null, username = null, role = null, avatar = null,
                        insulinCarbRatio = icrString, correctionFactor = isf?.toFloat(), targetGlucose = target,
                        syringeType = null, customSyringeLength = null,
                        age = null, gender = null, pregnant = null, dueDate = null,
                        diabetesType = null, insulinType = null, activeInsulinTime = null,
                        doseRounding = null, glucoseUnits = null,
                        sickDayAdjustment = null, stressAdjustment = null,
                        lightExerciseAdjustment = null, intenseExerciseAdjustment = null,
                        createdTimestamp = null, updatedTimestamp = null
                    )
                    userRepository.updateUser(email, dto)
                }
            } catch (e: Exception) {
                FileLogger.log("CHAT_VM", "Medical sync error: ${e.message}")
            }
        }
    }

    fun updateAdjustmentPercentages(sick: Int? = null, stress: Int? = null, light: Int? = null, intense: Int? = null) {
        val pm = UserProfileManager

        sick?.let { pm.saveSickDayAdjustment(application, it) }
        stress?.let { pm.saveStressAdjustment(application, it) }
        light?.let { pm.saveLightExerciseAdjustment(application, it) }
        intense?.let { pm.saveIntenseExerciseAdjustment(application, it) }

        viewModel.conversationManager.onAdjustmentPercentagesUpdated()

        viewModel.viewModelScope.launch {
            try {
                val email = AuthManager.getUserEmail() ?: pm.getUserEmail(application)
                if (email != null) {
                    val dto = com.example.insuscan.network.dto.UserDto(
                        userId = null, username = null, role = null, avatar = null,
                        insulinCarbRatio = null, correctionFactor = null, targetGlucose = null,
                        syringeType = null, customSyringeLength = null,
                        age = null, gender = null, pregnant = null, dueDate = null,
                        diabetesType = null, insulinType = null, activeInsulinTime = null,
                        doseRounding = null, glucoseUnits = null, createdTimestamp = null, updatedTimestamp = null,
                        sickDayAdjustment = sick, stressAdjustment = stress,
                        lightExerciseAdjustment = light, intenseExerciseAdjustment = intense
                    )
                    userRepository.updateUser(email, dto)
                }
            } catch (e: Exception) {
                FileLogger.log("CHAT_VM", "Sync error: ${e.message}")
            }
        }
    }

    fun onSaveMeal() {
        val meal = MealSessionManager.currentMeal ?: run {
            viewModel.addMessage(ChatMessage.BotText(text = "No meal to save."))
            return
        }
        val doseResult = viewModel.conversationManager.lastDoseResult
        val loadingMsg = ChatMessage.BotLoading(text = "Saving…")
        viewModel.addMessage(loadingMsg)

        viewModel.viewModelScope.launch {
            try {
                val email = AuthManager.getUserEmail() ?: UserProfileManager.getUserEmail(application) ?: "unknown"
                val pm = UserProfileManager
                val mealToSave = meal.copy(
                    glucoseLevel = viewModel.conversationManager.collectedGlucose,
                    glucoseUnits = pm.getGlucoseUnits(application),
                    activityLevel = viewModel.conversationManager.collectedActivityLevel,
                    wasSickMode = viewModel.conversationManager.collectedSickMode,
                    wasStressMode = viewModel.conversationManager.collectedStressMode,
                    carbDose = doseResult?.carbDose,
                    correctionDose = doseResult?.correctionDose,
                    recommendedDose = doseResult?.roundedDose,
                    sickAdjustment = doseResult?.sickAdj,
                    stressAdjustment = doseResult?.stressAdj,
                    exerciseAdjustment = doseResult?.exerciseAdj,
                    savedIcr = pm.getGramsPerUnit(application),
                    savedIsf = pm.getCorrectionFactor(application),
                    savedTargetGlucose = pm.getTargetGlucose(application),
                    savedSickPct = if (viewModel.conversationManager.collectedSickMode) pm.getSickDayAdjustment(application) else 0,
                    savedStressPct = if (viewModel.conversationManager.collectedStressMode) pm.getStressAdjustment(application) else 0,
                    savedExercisePct = when (viewModel.conversationManager.collectedActivityLevel) {
                        "light" -> pm.getLightExerciseAdjustment(application)
                        "intense" -> pm.getIntenseExerciseAdjustment(application)
                        else -> 0
                    }
                )
                val dto = MealDtoMapper.mapToDto(mealToSave)
                val result = withContext(Dispatchers.IO) { mealRepository.saveScannedMeal(email, dto) }
                viewModel.removeMessage(loadingMsg.id)

                result.onSuccess {
                    viewModel.conversationManager.onMealSaved()
                }.onFailure { error ->
                    viewModel.addMessage(ChatMessage.BotText(text = "❌ Failed to save: ${error.message ?: "Unknown error"}"))
                }
            } catch (e: Exception) {
                viewModel.removeMessage(loadingMsg.id)
                viewModel.addMessage(ChatMessage.BotText(text = "❌ Error: ${e.message}"))
                FileLogger.log("CHAT_VM", "Save error: ${e.message}")
            }
        }
    }
}
