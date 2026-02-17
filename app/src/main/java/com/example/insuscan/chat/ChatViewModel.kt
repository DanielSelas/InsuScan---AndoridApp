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
import com.example.insuscan.meal.FoodItem
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.insuscan.network.dto.ChatParseResponseDto
import java.util.concurrent.ConcurrentLinkedQueue

// Holds chat messages and drives the conversation via ConversationManager.
// Single source of truth for the message list + sticky buttons.
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _stickyActions = MutableLiveData<List<ActionButton>?>(null)
    val stickyActions: LiveData<List<ActionButton>?> = _stickyActions

    private val conversationManager = ConversationManager(application)
    private val scanRepository = ScanRepositoryImpl()
    private val mealRepository = MealRepositoryImpl()

    val currentState: ChatState get() = conversationManager.currentState

    // Events for the Fragment to observe (camera, navigation, edit dialog)
    private val _imagePickEvent = MutableLiveData<String?>()
    val imagePickEvent: LiveData<String?> = _imagePickEvent

    private val _editFoodEvent = MutableLiveData<Boolean>()
    val editFoodEvent: LiveData<Boolean> = _editFoodEvent
    // triggers the medical edit bottom sheet
    private val _editMedicalEvent = MutableLiveData<Boolean>()
    val editMedicalEvent: LiveData<Boolean> get() = _editMedicalEvent

    private val _navigationEvent = MutableLiveData<String?>()
    val navigationEvent: LiveData<String?> = _navigationEvent

    // New events for edit sheets and adjustment dialog
    private val _addFoodItemsEvent = MutableLiveData<List<FoodItem>?>()
    val addFoodItemsEvent: LiveData<List<FoodItem>?> = _addFoodItemsEvent

    // Split edit events
    private val _editActivityEvent = MutableLiveData<Boolean>()
    val editActivityEvent: LiveData<Boolean> = _editActivityEvent

    private val _editSickStressEvent = MutableLiveData<Boolean>()
    val editSickStressEvent: LiveData<Boolean> = _editSickStressEvent

    private val userRepository = com.example.insuscan.network.repository.UserRepositoryImpl()

    // D: Typing indicator queue — bot messages are enqueued and shown with a brief delay
    private val botMessageQueue = ConcurrentLinkedQueue<ChatMessage>()
    private var isProcessingQueue = false
    private val TYPING_DELAY_MS = 400L

    init {
        conversationManager.callback = object : ConversationManager.Callback {
            override fun onBotMessage(message: ChatMessage) {
                enqueueBotMessage(message)
            }
// ... (rest of init callback remains similar, but removing old edit event)
            override fun onStateChanged(newState: ChatState) {
                FileLogger.log("CHAT_VM", "State → $newState")
            }

            override fun onNeedLlmParse(text: String, state: ChatState) {
                parseFreeTextWithLlm(text, state)
            }

            override fun onStickyAction(actions: List<ActionButton>?) {
                _stickyActions.postValue(actions)
            }

            override fun onRequestEditMealSheet() {
                // trigger the edit sheet to open automatically
                _editFoodEvent.postValue(true)
            }

            override fun onRequestEditMedicalSheet() {
                _editMedicalEvent.postValue(true)
            }

            override fun onFoodItemsAddedToSheet(items: List<FoodItem>) {
                _addFoodItemsEvent.postValue(items)
            }

            // We no longer need onRequestEditAdjustmentsDialog as it's split
        }
        conversationManager.startConversation()
    }

    // -- User input --

    fun onUserSendText(text: String) {
        if (text.isBlank()) return
        addMessage(ChatMessage.UserText(text = text))
        conversationManager.handleFreeText(text)
    }

    fun updateMedicalSettings(icr: Double?, isf: Double?, target: Int?) {
        conversationManager.onMedicalParamsUpdated(icr, isf, target)

        // Sync updated medical params to server DB
        viewModelScope.launch {
            try {
                val email = AuthManager.getUserEmail()
                    ?: UserProfileManager.getUserEmail(getApplication())
                if (email != null) {
                    val icrString = icr?.let { "1:${it.toInt()}" }
                    val dto = com.example.insuscan.network.dto.UserDto(
                        userId = null, username = null, role = null, avatar = null,
                        insulinCarbRatio = icrString,
                        correctionFactor = isf?.toFloat(),
                        targetGlucose = target,
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

    // -- Image handling --

    fun onImageReceived(imagePath: String) {
        addMessage(ChatMessage.UserImage(imagePath = imagePath))
        val loadingMsg = ChatMessage.BotLoading(text = "Analyzing your meal…")
        addMessage(loadingMsg)

        // Immediately start medical/glucose/activity questions while scan runs
        conversationManager.beginParallelQuestions()

        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(imagePath) }
                if (bitmap == null) {
                    removeMessage(loadingMsg.id)
                    conversationManager.onScanError("Could not read image file.")
                    return@launch
                }

                val email = AuthManager.getUserEmail()
                    ?: UserProfileManager.getUserEmail(getApplication())
                    ?: "unknown"

                val result = withContext(Dispatchers.IO) { scanRepository.scanImage(bitmap, email) }
                removeMessage(loadingMsg.id)

                result.onSuccess { mealDto ->
                    val meal = com.example.insuscan.mapping.MealDtoMapper.map(mealDto).copy(imagePath = imagePath)
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

    // -- Button actions (all routed from ChatFragment) --

    fun onActionButton(actionId: String) {
        when (actionId) {
            // ... (Photo/Gallery/Food/Medical confirmed/edit - no changes)
            "take_photo" -> { fireEvent(_imagePickEvent, "camera"); return }
            "pick_gallery" -> { fireEvent(_imagePickEvent, "gallery"); return }

            // Food review
            "confirm_food" -> {
                addMessage(ChatMessage.UserText(text = "✅ Confirmed"))
                conversationManager.onFoodConfirmed()
            }
            "edit_food" -> {
                _editFoodEvent.value = true
                _editFoodEvent.value = false
            }

            // Medical review
            "confirm_medical" -> {
                addMessage(ChatMessage.UserText(text = "✅ Settings confirmed"))
                conversationManager.onMedicalConfirmed()
            }
            "edit_medical" -> {
                conversationManager.onRequestMedicalEdit()
            }
            "cancel_edit_medical" -> {
                addMessage(ChatMessage.UserText(text = "Cancel"))
                conversationManager.onCancelMedicalEdit()
            }
            "open_profile" -> {
                fireEvent(_navigationEvent, "profile")
            }

            // Glucose
            "skip_glucose" -> {
                addMessage(ChatMessage.UserText(text = "Skip"))
                conversationManager.onGlucoseProvided(null)
            }

            // Activity (during flow or step-edit)
            "activity_none" -> {
                addMessage(ChatMessage.UserText(text = "No exercise"))
                if (currentState == ChatState.ADJUSTING_ACTIVITY) {
                    conversationManager.onActivityAdjusted("normal")
                } else {
                    conversationManager.onActivitySelected("normal")
                }
            }
            "activity_light" -> {
                addMessage(ChatMessage.UserText(text = "Light exercise"))
                if (currentState == ChatState.ADJUSTING_ACTIVITY) {
                    conversationManager.onActivityAdjusted("light")
                } else {
                    conversationManager.onActivitySelected("light")
                }
            }
            "activity_intense" -> {
                addMessage(ChatMessage.UserText(text = "Intense exercise"))
                if (currentState == ChatState.ADJUSTING_ACTIVITY) {
                    conversationManager.onActivityAdjusted("intense")
                } else {
                    conversationManager.onActivitySelected("intense")
                }
            }
            "edit_activity_pct" -> {
                _editActivityEvent.value = true
                _editActivityEvent.value = false
            }

            // Result screen — step-edit loop
            "edit_step" -> conversationManager.onChooseEditStep()
            "edit_step_wait" -> conversationManager.onChooseEditStep() // allow editing while waiting
            "edit_step_food" -> {
                addMessage(ChatMessage.UserText(text = "Edit food items"))
                conversationManager.onEditStepFood()
            }
            "edit_step_medical" -> {
                addMessage(ChatMessage.UserText(text = "Edit medical settings"))
                conversationManager.onEditStepMedical()
            }
            "edit_step_glucose" -> {
                addMessage(ChatMessage.UserText(text = "Edit glucose"))
                conversationManager.onEditStepGlucose()
            }
            "edit_step_activity" -> {
                addMessage(ChatMessage.UserText(text = "Edit activity"))
                conversationManager.onEditStepActivity()
            }
            "edit_step_adjustments" -> {
                addMessage(ChatMessage.UserText(text = "Edit adjustments"))
                conversationManager.onEditStepAdjustments()
            }

            // Adjustments (Sick/Stress)
            "adj_none" -> {
                addMessage(ChatMessage.UserText(text = "Neither"))
                conversationManager.onAdjustmentSelected(sick = false, stress = false)
            }
            "adj_sick" -> {
                addMessage(ChatMessage.UserText(text = "Sick"))
                conversationManager.onAdjustmentSelected(sick = true, stress = false)
            }
            "adj_stress" -> {
                addMessage(ChatMessage.UserText(text = "Stress"))
                conversationManager.onAdjustmentSelected(sick = false, stress = true)
            }
            "adj_both" -> {
                addMessage(ChatMessage.UserText(text = "Both"))
                conversationManager.onAdjustmentSelected(sick = true, stress = true)
            }
            "edit_sick_stress_pct" -> {
                _editSickStressEvent.value = true
                _editSickStressEvent.value = false
            }

            "activity_menu" -> conversationManager.onAdjustActivity()
            "sick_toggle" -> conversationManager.toggleSickMode()
            "stress_toggle" -> conversationManager.toggleStressMode()
            "edit_adjustments" -> conversationManager.onEditAdjustments()
            "back_to_result" -> conversationManager.onBackToResult()

            // Save
            "save_meal" -> onSaveMeal()

            // Done screen
            "new_scan" -> {
                _messages.value = emptyList()
                conversationManager.resetForNewScan()
            }
            "history" -> fireEvent(_navigationEvent, "history")
        }
    }

    // Convenience wrappers for Fragment callbacks
    fun onFoodConfirmed() { onActionButton("confirm_food") }
    fun onMedicalConfirmed() { onActionButton("confirm_medical") }
    fun onMedicalEdit() { onActionButton("edit_medical") }
    fun onFoodEdit() { onActionButton("edit_food") }

    // -- Update & Sync Adjustments --

    fun updateAdjustmentPercentages(
        sick: Int? = null,
        stress: Int? = null,
        light: Int? = null,
        intense: Int? = null
    ) {
        val pm = UserProfileManager
        val ctx = getApplication<Application>()

        // 1. Save locally
        sick?.let { pm.saveSickDayAdjustment(ctx, it) }
        stress?.let { pm.saveStressAdjustment(ctx, it) }
        light?.let { pm.saveLightExerciseAdjustment(ctx, it) }
        intense?.let { pm.saveIntenseExerciseAdjustment(ctx, it) }

        // 2. Notify conversation manager immediately so UI updates
        conversationManager.onAdjustmentPercentagesUpdated()

        // 3. Sync to Server
        viewModelScope.launch {
            try {
                val email = AuthManager.getUserEmail() ?: pm.getUserEmail(ctx)
                if (email != null) {
                    val dto = com.example.insuscan.network.dto.UserDto(
                        userId = null, username = null, role = null, avatar = null,
                        insulinCarbRatio = null, correctionFactor = null, targetGlucose = null,
                        syringeType = null, customSyringeLength = null,
                        age = null, gender = null, pregnant = null, dueDate = null,
                        diabetesType = null, insulinType = null, activeInsulinTime = null,
                        doseRounding = null, glucoseUnits = null, createdTimestamp = null, updatedTimestamp = null,
                        
                        // Only these fields are updated
                        sickDayAdjustment = sick,
                        stressAdjustment = stress,
                        lightExerciseAdjustment = light,
                        intenseExerciseAdjustment = intense
                    )
                    userRepository.updateUser(email, dto)
                }
            } catch (e: Exception) {
                FileLogger.log("CHAT_VM", "Sync error: ${e.message}")
            }
        }
    }

    // -- Save meal --

    fun onSaveMeal() {
        val meal = MealSessionManager.currentMeal ?: run {
            addMessage(ChatMessage.BotText(text = "No meal to save."))
            return
        }
        val doseResult = conversationManager.lastDoseResult
        val loadingMsg = ChatMessage.BotLoading(text = "Saving…")
        addMessage(loadingMsg)

        viewModelScope.launch {
            try {
                val email = AuthManager.getUserEmail()
                    ?: UserProfileManager.getUserEmail(getApplication())
                    ?: "unknown"

// Set collected data on the meal before converting to DTO
                val pm = UserProfileManager
                val mealToSave = meal.copy(
                    glucoseLevel = conversationManager.collectedGlucose,
                    glucoseUnits = pm.getGlucoseUnits(getApplication()),
                    activityLevel = conversationManager.collectedActivityLevel,
                    wasSickMode = conversationManager.collectedSickMode,
                    wasStressMode = conversationManager.collectedStressMode,
                    carbDose = doseResult?.carbDose,
                    correctionDose = doseResult?.correctionDose,
                    recommendedDose = doseResult?.roundedDose,
                    sickAdjustment = doseResult?.sickAdj,
                    stressAdjustment = doseResult?.stressAdj,
                    exerciseAdjustment = doseResult?.exerciseAdj,
                    savedIcr = pm.getGramsPerUnit(getApplication()),
                    savedIsf = pm.getCorrectionFactor(getApplication()),
                    savedTargetGlucose = pm.getTargetGlucose(getApplication()),
                    savedSickPct = if (conversationManager.collectedSickMode) pm.getSickDayAdjustment(getApplication()) else 0,
                    savedStressPct = if (conversationManager.collectedStressMode) pm.getStressAdjustment(getApplication()) else 0,
                    savedExercisePct = when (conversationManager.collectedActivityLevel) {
                        "light" -> pm.getLightExerciseAdjustment(getApplication())
                        "intense" -> pm.getIntenseExerciseAdjustment(getApplication())
                        else -> 0
                    }
                )
                val dto = MealDtoMapper.mapToDto(mealToSave)

                val result = withContext(Dispatchers.IO) { mealRepository.saveScannedMeal(email, dto) }
                removeMessage(loadingMsg.id)

                result.onSuccess {
                    conversationManager.onMealSaved()
                }.onFailure { error ->
                    val msg = error.message ?: "Unknown error"
                    addMessage(ChatMessage.BotText(text = "❌ Failed to save: $msg"))
                }
            } catch (e: Exception) {
                removeMessage(loadingMsg.id)
                addMessage(ChatMessage.BotText(text = "❌ Error: ${e.message}"))
                FileLogger.log("CHAT_VM", "Save error: ${e.message}")
            }
        }
    }

    // -- Edit meal items (from bottom sheet) --

    fun updateMealItems(items: List<com.example.insuscan.meal.FoodItem>) {
        val currentMeal = MealSessionManager.currentMeal ?: return
        val totalCarbs = items.sumOf { (it.carbsGrams ?: 0f).toDouble() }.toFloat()
        MealSessionManager.updateCurrentMeal(currentMeal.copy(foodItems = items, carbs = totalCarbs))
        addMessage(ChatMessage.UserText(text = "✏️ Edited meal items"))
        conversationManager.onMealUpdated()
    }

    fun updateMealCarbs(newTotalCarbs: Float) {
        val currentMeal = MealSessionManager.currentMeal ?: return
        MealSessionManager.updateCurrentMeal(currentMeal.copy(carbs = newTotalCarbs))
        addMessage(ChatMessage.UserText(text = "✏️ Carbs: ${newTotalCarbs}g"))
        conversationManager.onMealUpdated()
    }

    // -- LLM free-text parsing --

    private fun parseFreeTextWithLlm(text: String, state: ChatState) {
        addMessage(ChatMessage.BotLoading(text = "Thinking…"))

        viewModelScope.launch {
            try {
                val request = ChatParseRequestDto(text = text, state = state.name)
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.parseChatText(request)
                }
                removeAllLoading()

                if (response.isSuccessful && response.body() != null) {
                    conversationManager.handleLlmResponse(response.body()!!)
                } else {
                    addMessage(ChatMessage.BotText(text = "I couldn't understand that. Try the buttons or rephrase."))
                    // Make sure we restore buttons so the user isn't stuck
                    conversationManager.handleLlmResponse(
                        com.example.insuscan.network.dto.ChatParseResponseDto(
                            action = "unknown", items = null, glucose = null,
                            activity = null, icr = null, isf = null,
                            targetGlucose = null, message = null
                        )
                    )
                }
            } catch (e: Exception) {
                removeAllLoading()
                addMessage(ChatMessage.BotText(text = "I couldn't understand that. Try the buttons or rephrase."))
                FileLogger.log("CHAT_VM", "LLM error: ${e.message}")
                // Restore buttons on error too
                conversationManager.handleLlmResponse(
                    ChatParseResponseDto(
                        action = "unknown",
                        items = null,
                        glucose = null,
                        activity = null,
                        icr = null,
                        isf = null,
                        targetGlucose = null,
                        message = null
                    )
                )
            }
        }
    }

    // -- Helpers --

    // D: Enqueue bot messages with typing indicator
    private fun enqueueBotMessage(message: ChatMessage) {
        botMessageQueue.add(message)
        if (!isProcessingQueue) {
            isProcessingQueue = true
            processBotMessageQueue()
        }
    }

    private fun processBotMessageQueue() {
        viewModelScope.launch {
            while (botMessageQueue.isNotEmpty()) {
                val message = botMessageQueue.poll() ?: break

                // Only show typing indicator for text messages, not cards/loading
                val shouldShowTyping = message is ChatMessage.BotText

                if (shouldShowTyping) {
                    val typingMsg = ChatMessage.BotLoading(text = "⋯")
                    addMessage(typingMsg)
                    delay(TYPING_DELAY_MS)
                    removeMessage(typingMsg.id)
                }

                addMessage(message)
            }
            isProcessingQueue = false
        }
    }

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

    private fun removeAllLoading() {
        val current = _messages.value.orEmpty().toMutableList()
        current.removeAll { it is ChatMessage.BotLoading }
        _messages.value = current
    }

    // Fire a one-shot event (set value, then null)
    private fun fireEvent(liveData: MutableLiveData<String?>, value: String) {
        liveData.value = value
        liveData.value = null
    }
}