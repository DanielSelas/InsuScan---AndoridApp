package com.example.insuscan.chat

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.insuscan.auth.AuthManager
import com.example.insuscan.mapping.MealDtoMapper
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.network.RetrofitClient
import com.example.insuscan.network.dto.ChatParseRequestDto
import com.example.insuscan.network.repository.MealRepositoryImpl
import com.example.insuscan.network.repository.ScanRepositoryImpl
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Holds chat messages and drives the conversation via ConversationManager.
// Handles camera/image results, scan API calls, and all user actions.
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages
    
    private val _stickyActions = MutableLiveData<List<ActionButton>?>(null)
    val stickyActions: LiveData<List<ActionButton>?> = _stickyActions

    private val conversationManager = ConversationManager(application)
    private val scanRepository = ScanRepositoryImpl()
    private val mealRepository = MealRepositoryImpl()

    // Expose current state for UI decisions
    val currentState: ChatState get() = conversationManager.currentState

    init {
        conversationManager.callback = object : ConversationManager.Callback {
            override fun onBotMessage(message: ChatMessage) {
                addMessage(message)
            }

            override fun onStateChanged(newState: ChatState) {
                FileLogger.log("CHAT_VM", "State → $newState")
            }

            override fun onNeedLlmParse(text: String, state: ChatState) {
                parseFreeTextWithLlm(text, state)
            }
            
            override fun onStickyAction(actions: List<ActionButton>?) {
                _stickyActions.postValue(actions)
            }
        }

        conversationManager.startConversation()
    }

    // Called when user taps send
    fun onUserSendText(text: String) {
        if (text.isBlank()) return
        addMessage(ChatMessage.UserText(text = text))

        // Route through ConversationManager
        conversationManager.handleFreeText(text)
    }

    // Called when user picks or captures an image
    fun onImageReceived(imagePath: String) {
        addMessage(ChatMessage.UserImage(imagePath = imagePath))

        val loadingMsg = ChatMessage.BotLoading(text = "Analyzing your meal…")
        addMessage(loadingMsg)

        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(imagePath)
                }

                if (bitmap == null) {
                    removeMessage(loadingMsg.id)
                    conversationManager.onScanError("Could not read the image file.")
                    return@launch
                }

                val email = AuthManager.getUserEmail()
                    ?: UserProfileManager.getUserEmail(getApplication())
                    ?: "unknown"

                val result = withContext(Dispatchers.IO) {
                    scanRepository.scanImage(bitmap, email)
                }

                removeMessage(loadingMsg.id)

                result.onSuccess { mealDto ->
                    val meal = MealDtoMapper.map(mealDto).copy(imagePath = imagePath)
                    conversationManager.onScanSuccess(meal)
                }.onFailure { error ->
                    conversationManager.onScanError(error.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                removeMessage(loadingMsg.id)
                conversationManager.onScanError(e.message ?: "Unexpected error")
            }
        }
    }

    // Called when user taps Confirm on food card
    fun onFoodConfirmed() {
        addMessage(ChatMessage.UserText(text = "✅ Confirmed"))
        conversationManager.onFoodConfirmed()
    }

    // Called when user taps Edit on food card
    fun onFoodEdit() {
        _editFoodEvent.value = true // Trigger dialog in Fragment
        _editFoodEvent.value = false // Reset immediately
    }

    // Called when user updates items via EditMealBottomSheet
    fun updateMealItems(items: List<com.example.insuscan.meal.FoodItem>) {
        val currentMeal = MealSessionManager.currentMeal ?: return
        
        // Sum up carbs
        val totalCarbs = items.sumOf { (it.carbsGrams ?: 0f).toDouble() }.toFloat()
        
        val updatedMeal = currentMeal.copy(
            foodItems = items,
            carbs = totalCarbs
        )
        MealSessionManager.updateCurrentMeal(updatedMeal)
        
        addMessage(ChatMessage.UserText(text = "✏️ Edited meal items"))
        conversationManager.onMealUpdated()
    }

    // Called when user manually updates total carbs from dialog (legacy/fallback)
    fun updateMealCarbs(newTotalCarbs: Float) {
        val currentMeal = MealSessionManager.currentMeal ?: return
        
        val updatedMeal = currentMeal.copy(carbs = newTotalCarbs)
        MealSessionManager.updateCurrentMeal(updatedMeal)
        
        addMessage(ChatMessage.UserText(text = "✏️ Edit: ${newTotalCarbs}g"))
        conversationManager.onMealUpdated()
    }

    // Called when user taps Confirm on medical card
    fun onMedicalConfirmed() {
        addMessage(ChatMessage.UserText(text = "✅ Settings confirmed"))
        conversationManager.onMedicalConfirmed()
    }

    // Event to trigger camera, gallery, or edit dialog
    private val _imagePickEvent = MutableLiveData<String?>()
    val imagePickEvent: LiveData<String?> = _imagePickEvent

    private val _editFoodEvent = MutableLiveData<Boolean>()
    val editFoodEvent: LiveData<Boolean> = _editFoodEvent

    private val _navigationEvent = MutableLiveData<String?>()
    val navigationEvent: LiveData<String?> = _navigationEvent

    fun onMedicalEdit() {
        _navigationEvent.value = "profile"
        _navigationEvent.value = null
    }

    // Called when user taps an action button (activity level, photo, gallery)
    fun onActionButton(actionId: String) {
        when (actionId) {
            "take_photo" -> {
                _imagePickEvent.value = "camera"
                _imagePickEvent.value = null // reset
                return
            }
            "pick_gallery" -> {
                _imagePickEvent.value = "gallery"
                _imagePickEvent.value = null // reset
                return
            }
            "skip" -> {
                addMessage(ChatMessage.UserText(text = "Skip"))
                conversationManager.onGlucoseProvided(null)
                return
            }
            "new_scan" -> {
                _messages.value = emptyList() // Clear chat
                conversationManager.startConversation()
                return
            }
            "history" -> {
                _navigationEvent.value = "history"
                _navigationEvent.value = null
                return
            }
            "confirm_food" -> {
                conversationManager.onFoodConfirmed()
                return
            }
            "edit_food" -> {
                _editFoodEvent.value = true
                _editFoodEvent.value = false // Reset? Or handle in Fragment
                return
            }
            "confirm_medical" -> {
                conversationManager.onMedicalConfirmed()
                return
            }
            "edit_medical" -> {
                conversationManager.onRequestMedicalEdit()
                return
            }
            "save_meal" -> {
                onSaveMeal()
                return
            }
            "sick_toggle" -> {
                conversationManager.toggleSickMode()
                return
            }
            "stress_toggle" -> {
                conversationManager.toggleStressMode()
                return
            }
            "activity_menu" -> {
                conversationManager.onShowActivityMenu()
                return
            }
            "back_to_result" -> {
                conversationManager.onBackToResult()
                return
            }
        }

        val label = when (actionId) {
            "normal" -> "No adjustment"
            "light" -> "Light exercise"
            "intense" -> "Intense exercise"
            else -> actionId
        }
        addMessage(ChatMessage.UserText(text = label))
        conversationManager.onActivitySelected(actionId)
    }

    // Called when user taps Save Meal
    fun onSaveMeal() {
        val meal = MealSessionManager.currentMeal
        if (meal == null) {
            addMessage(ChatMessage.BotText(text = "Error: No meal to save."))
            return
        }

        addMessage(ChatMessage.UserText(text = "💾 Save Meal"))
        addMessage(ChatMessage.BotLoading(text = "Saving your meal…"))

        // Build updated meal with dose result (mirrors SummaryFragment.buildUpdatedMeal)
        val doseResult = conversationManager.lastDoseResult
        val updatedMeal = if (doseResult != null) {
            val exerciseAdjValue = if (doseResult.exerciseAdj > 0) -doseResult.exerciseAdj else 0f
            val pm = UserProfileManager
            val ctx = getApplication<Application>()
            meal.copy(
                insulinDose = doseResult.roundedDose,
                recommendedDose = doseResult.roundedDose,
                glucoseLevel = conversationManager.collectedGlucose,
                glucoseUnits = pm.getGlucoseUnits(ctx),
                activityLevel = conversationManager.collectedActivityLevel,
                carbDose = doseResult.carbDose,
                correctionDose = doseResult.correctionDose,
                exerciseAdjustment = exerciseAdjValue,
                sickAdjustment = doseResult.sickAdj,
                stressAdjustment = doseResult.stressAdj,
                wasSickMode = pm.isSickModeEnabled(ctx),
                wasStressMode = pm.isStressModeEnabled(ctx),
                activeInsulin = 0f
            )
        } else {
            meal
        }

        MealSessionManager.updateCurrentMeal(updatedMeal)

        viewModelScope.launch {
            try {
                val mealDto = MealDtoMapper.mapToDto(updatedMeal)
                val email = AuthManager.getUserEmail() ?: ""

                val result = withContext(Dispatchers.IO) {
                    mealRepository.saveScannedMeal(email, mealDto)
                }

                // Remove loading message
                val msgs = _messages.value.orEmpty().toMutableList()
                msgs.removeAll { it is ChatMessage.BotLoading }
                _messages.value = msgs

                if (result.isSuccess) {
                    conversationManager.onMealSaved()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    addMessage(ChatMessage.BotText(text = "❌ Failed to save: $errorMsg\nTry again with the Save button."))
                }
            } catch (e: Exception) {
                // Remove loading message
                val msgs = _messages.value.orEmpty().toMutableList()
                msgs.removeAll { it is ChatMessage.BotLoading }
                _messages.value = msgs

                addMessage(ChatMessage.BotText(text = "❌ Error: ${e.message}"))
                FileLogger.log("CHAT_VM", "Save error: ${e.message}")
            }
        }
    }

    // --- helpers ---

    private fun addMessage(message: ChatMessage) {
        val current = _messages.value.orEmpty().toMutableList()
        current.add(message)
        _messages.value = current
    }

    private fun removeMessage(messageId: String) {
        val current = _messages.value.orEmpty().toMutableList()
        current.removeAll { it.id == messageId }
        _messages.value = current
    }

    // --- LLM free-text parsing via server ---

    private fun parseFreeTextWithLlm(text: String, state: ChatState) {
        addMessage(ChatMessage.BotLoading(text = "Thinking…"))

        viewModelScope.launch {
            try {
                val request = ChatParseRequestDto(text = text, state = state.name)
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.parseChatText(request)
                }

                // Remove loading
                val msgs = _messages.value.orEmpty().toMutableList()
                msgs.removeAll { it is ChatMessage.BotLoading }
                _messages.value = msgs

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        handleLlmResponse(body)
                    } else {
                        addMessage(ChatMessage.BotText(text = "I couldn't understand that. Try describing your meal or take a photo!"))
                    }
                } else {
                    addMessage(ChatMessage.BotText(text = "I couldn't understand that. Try describing your meal or take a photo!"))
                }
            } catch (e: Exception) {
                // Remove loading
                val msgs = _messages.value.orEmpty().toMutableList()
                msgs.removeAll { it is ChatMessage.BotLoading }
                _messages.value = msgs

                addMessage(ChatMessage.BotText(text = "I couldn't understand that. Try describing your meal or take a photo!"))
                FileLogger.log("CHAT_VM", "LLM parse error: ${e.message}")
            }
        }
    }

    private fun handleLlmResponse(response: com.example.insuscan.network.dto.ChatParseResponseDto) {
        when (response.action) {
            "add_food" -> {
                val items = response.items?.map { entry ->
                    com.example.insuscan.meal.FoodItem(
                        // Append quantity to name if > 1 for clarity, e.g. "Apple (2)"
                        name = if ((entry.quantity ?: 1) > 1) "${entry.name} (${entry.quantity})" else entry.name,
                        carbsGrams = entry.estimatedCarbsGrams,
                        weightGrams = null, // AI provides carbs directly
                        confidence = 0.7f
                    )
                } ?: emptyList()

                if (items.isNotEmpty()) {
                    val state = conversationManager.currentState
                    val shouldMerge = (state == ChatState.REVIEWING_FOOD || state == ChatState.REVIEWING_MEDICAL) &&
                            com.example.insuscan.meal.MealSessionManager.currentMeal != null
                    
                    conversationManager.onLlmFoodItems(items, merge = shouldMerge)
                } else {
                    addMessage(ChatMessage.BotText(text = response.message ?: "No food items found."))
                }
            }
            "set_medical_params" -> {
                conversationManager.onMedicalParamsUpdated(
                    icr = response.icr,
                    isf = response.isf,
                    target = response.targetGlucose
                )
            }
            "clarify" -> {
                addMessage(ChatMessage.BotText(text = response.message ?: "Could you please clarify?"))
            }
            "set_glucose" -> {
                val glucose = response.glucose
                if (glucose != null) {
                    conversationManager.handleFreeText(glucose.toString())
                } else {
                    addMessage(ChatMessage.BotText(text = response.message ?: "Could not determine glucose level."))
                }
            }
            "set_activity" -> {
                val activity = response.activity
                if (activity != null) {
                    conversationManager.handleFreeText(activity)
                } else {
                    addMessage(ChatMessage.BotText(text = response.message ?: "Could not determine activity level."))
                }
            }
            "confirm" -> {
                conversationManager.handleFreeText("confirm")
            }
            else -> {
                addMessage(ChatMessage.BotText(text = response.message ?: "I couldn't understand that. Try describing your meal or take a photo!"))
            }
        }
    }
}
