package com.example.insuscan.chat.delegates

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.viewModelScope
import com.example.insuscan.auth.AuthManager
import com.example.insuscan.chat.ChatMessage
import com.example.insuscan.chat.ChatViewModel
import com.example.insuscan.mapping.MealDtoMapper
import com.example.insuscan.network.repository.ScanRepositoryImpl
import com.example.insuscan.profile.UserProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatScanDelegate(private val viewModel: ChatViewModel, private val application: Application) {
    private val scanRepository = ScanRepositoryImpl()
    private val pipelineManager = com.example.insuscan.scan.ScanPipelineManager(application)

    fun onImageReceived(imagePath: String, referenceObjectType: String? = null) {
        viewModel.addMessage(ChatMessage.UserImage(imagePath = imagePath))
        val loadingMsg = ChatMessage.BotLoading(text = "Analyzing your meal…")
        viewModel.addMessage(loadingMsg)

        viewModel.conversationManager.beginParallelQuestions()

        viewModel.viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(imagePath) }
                if (bitmap == null) {
                    viewModel.removeMessage(loadingMsg.id)
                    viewModel.conversationManager.onScanError("Could not read image file.")
                    return@launch
                }

                val email = AuthManager.getUserEmail() ?: UserProfileManager.getUserEmail(application) ?: "unknown"

                val result = withContext(Dispatchers.IO) { scanRepository.scanImage(bitmap, email, referenceObjectType = referenceObjectType) }
                viewModel.removeMessage(loadingMsg.id)

                result.onSuccess { mealDto ->
                    val meal = MealDtoMapper.map(mealDto).copy(imagePath = imagePath)
                    viewModel.conversationManager.onScanSuccess(meal)
                }.onFailure { error ->
                    viewModel.conversationManager.onScanError(error.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                viewModel.removeMessage(loadingMsg.id)
                viewModel.conversationManager.onScanError(e.message ?: "Unexpected error")
            }
        }
    }

    fun onScanResultFromScanFragment(meal: com.example.insuscan.meal.Meal, imagePath: String?) {
        if (imagePath != null) {
            viewModel.addMessage(ChatMessage.UserImage(imagePath = imagePath))
        }
        viewModel.conversationManager.beginParallelQuestions()
        viewModel.conversationManager.onScanSuccess(meal)
    }

    fun onImageCapturedFromCamera(data: com.example.insuscan.scan.CapturedScanData) {
        viewModel.addMessage(ChatMessage.UserImage(imagePath = data.imagePath))
        val loadingMsg = ChatMessage.BotLoading(text = "Analyzing your meal…")
        viewModel.addMessage(loadingMsg)

        viewModel.conversationManager.beginParallelQuestions()

        viewModel.viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(data.imagePath) }
                if (bitmap == null) {
                    viewModel.removeMessage(loadingMsg.id)
                    viewModel.conversationManager.onScanError("Could not read image file.")
                    return@launch
                }

                pipelineManager.isRefObjectExpectedInFrame = data.referenceType != null && data.referenceType != "NONE"
                pipelineManager.wasRefFoundInLivePreview = data.wasRefFoundInPreview
                pipelineManager.skipSidePhoto()
                val imageFile = java.io.File(data.imagePath)

                val refCheck = pipelineManager.checkReferenceObject(bitmap, data.referenceType)
                val refTypeToUse = when (refCheck) {
                    is com.example.insuscan.scan.RefCheckResult.Proceed -> refCheck.refType
                    is com.example.insuscan.scan.RefCheckResult.AlternativeFound -> {
                        when (refCheck.detectedMode) {
                            com.example.insuscan.analysis.detection.ReferenceObjectDetector.DetectionMode.STRICT -> "INSULIN_SYRINGE"
                            com.example.insuscan.analysis.detection.ReferenceObjectDetector.DetectionMode.FLEXIBLE -> "SYRINGE_KNIFE"
                            com.example.insuscan.analysis.detection.ReferenceObjectDetector.DetectionMode.CARD -> "CARD"
                        }
                    }
                }

                val result = pipelineManager.runAnalysis(bitmap, imageFile, refTypeToUse, data.imagePath)
                viewModel.removeMessage(loadingMsg.id)

                when (result) {
                    is com.example.insuscan.scan.PipelineResult.Success -> {
                        if (result.warning != null) viewModel.addMessage(ChatMessage.BotText(text = "⚠️ ${result.warning}"))
                        viewModel.conversationManager.onScanSuccess(result.meal)
                    }
                    is com.example.insuscan.scan.PipelineResult.NeedSidePhoto -> {
                        pipelineManager.skipSidePhoto()
                        val fallbackResult = pipelineManager.runAnalysis(bitmap, imageFile, refTypeToUse, data.imagePath)
                        viewModel.removeMessage(loadingMsg.id)
                        handleBackgroundPipelineResult(fallbackResult)
                    }
                    com.example.insuscan.scan.PipelineResult.NoFoodDetected -> {
                        viewModel.conversationManager.onScanError("No food detected in the image. Try again?")
                    }
                    is com.example.insuscan.scan.PipelineResult.Failed -> {
                        viewModel.conversationManager.onScanError(result.error.message ?: "Analysis failed")
                    }
                }
            } catch (e: Exception) {
                viewModel.removeMessage(loadingMsg.id)
                viewModel.conversationManager.onScanError(e.message ?: "Unexpected error")
            }
        }
    }

    private fun handleBackgroundPipelineResult(result: com.example.insuscan.scan.PipelineResult) {
        when (result) {
            is com.example.insuscan.scan.PipelineResult.Success -> {
                if (result.warning != null) viewModel.addMessage(ChatMessage.BotText(text = "⚠️ ${result.warning}"))
                viewModel.conversationManager.onScanSuccess(result.meal)
            }
            com.example.insuscan.scan.PipelineResult.NoFoodDetected -> {
                viewModel.conversationManager.onScanError("No food detected in the image.")
            }
            is com.example.insuscan.scan.PipelineResult.Failed -> {
                viewModel.conversationManager.onScanError(result.error.message ?: "Analysis failed")
            }
            is com.example.insuscan.scan.PipelineResult.NeedSidePhoto -> {
                viewModel.conversationManager.onScanError("Analysis incomplete. Try again?")
            }
        }
    }
}
