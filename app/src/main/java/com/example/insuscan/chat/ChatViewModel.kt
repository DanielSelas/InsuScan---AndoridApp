package com.example.insuscan.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.insuscan.chat.delegates.ChatActionDelegate
import com.example.insuscan.chat.delegates.ChatScanDelegate
import com.example.insuscan.chat.delegates.ChatSyncDelegate
import com.example.insuscan.meal.FoodItem
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.network.RetrofitClient
import com.example.insuscan.network.dto.ChatParseRequestDto
import com.example.insuscan.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _stickyActions = MutableLiveData<List<ActionButton>?>(null)
    val stickyActions: LiveData<List<ActionButton>?> = _stickyActions

    internal val conversationManager = ConversationManager(application)
    val currentState: ChatState get() = conversationManager.currentState

    private val _imagePickEvent = MutableLiveData<String?>()
    val imagePickEvent: LiveData<String?> = _imagePickEvent

    private val _editFoodEvent = MutableLiveData<Boolean>()
    val editFoodEvent: LiveData<Boolean> = _editFoodEvent

    private val _editMedicalEvent = MutableLiveData<Boolean>()
    val editMedicalEvent: LiveData<Boolean> get() = _editMedicalEvent

    private val _navigationEvent = MutableLiveData<String?>()
    val navigationEvent: LiveData<String?> = _navigationEvent

    private val _addFoodItemsEvent = MutableLiveData<List<FoodItem>?>()
    val addFoodItemsEvent: LiveData<List<FoodItem>?> = _addFoodItemsEvent

    private val _editActivityEvent = MutableLiveData<Boolean>()
    val editActivityEvent: LiveData<Boolean> = _editActivityEvent

    private val _editSickStressEvent = MutableLiveData<Boolean>()
    val editSickStressEvent: LiveData<Boolean> = _editSickStressEvent

    private val botMessageQueue = ConcurrentLinkedQueue<ChatMessage>()
    private var isProcessingQueue = false
    private val TYPING_DELAY_MS = 400L

    internal val scanDelegate = ChatScanDelegate(this, application)
    internal val syncDelegate = ChatSyncDelegate(this, application)
    internal val actionDelegate = ChatActionDelegate(this)

    init {
        conversationManager.callback = object : ConversationManager.Callback {
            override fun onBotMessage(message: ChatMessage) = enqueueBotMessage(message)
            override fun onStateChanged(newState: ChatState) { FileLogger.log("CHAT_VM", "State → $newState") }
            override fun onNeedLlmParse(text: String, state: ChatState) = parseFreeTextWithLlm(text, state)
            override fun onStickyAction(actions: List<ActionButton>?) { _stickyActions.postValue(actions) }
            override fun onRequestEditMealSheet() { _editFoodEvent.postValue(true) }
            override fun onRequestEditMedicalSheet() { _editMedicalEvent.postValue(true) }
            override fun onFoodItemsAddedToSheet(items: List<FoodItem>) { _addFoodItemsEvent.postValue(items) }
        }
        conversationManager.startConversation()
    }

    fun onUserSendText(text: String) {
        if (text.isBlank()) return
        addMessage(ChatMessage.UserText(text = text))
        conversationManager.handleFreeText(text)
    }

    fun updateMedicalSettings(icr: Double?, isf: Double?, target: Int?) = syncDelegate.updateMedicalSettings(icr, isf, target)
    fun onImageReceived(imagePath: String, referenceObjectType: String? = null) = scanDelegate.onImageReceived(imagePath, referenceObjectType)
    fun onScanResultFromScanFragment(meal: com.example.insuscan.meal.Meal, imagePath: String?) = scanDelegate.onScanResultFromScanFragment(meal, imagePath)
    fun onImageCapturedFromCamera(data: com.example.insuscan.scan.CapturedScanData) = scanDelegate.onImageCapturedFromCamera(data)
    fun onActionButton(actionId: String) = actionDelegate.onActionButton(actionId)

    fun onFoodConfirmed() { onActionButton("confirm_food") }
    fun onMedicalConfirmed() { onActionButton("confirm_medical") }
    fun onMedicalEdit() { onActionButton("edit_medical") }
    fun onFoodEdit() { onActionButton("edit_food") }

    fun updateAdjustmentPercentages(sick: Int? = null, stress: Int? = null, light: Int? = null, intense: Int? = null) = syncDelegate.updateAdjustmentPercentages(sick, stress, light, intense)
    fun onSaveMeal() = syncDelegate.onSaveMeal()

    fun updateMealItems(items: List<FoodItem>) {
        val currentMeal = MealSessionManager.currentMeal ?: return
        val totalCarbs = items.sumOf { (it.carbsGrams ?: 0f).toDouble() }.toFloat()
        MealSessionManager.setCurrentMeal(currentMeal.copy(foodItems = items, carbs = totalCarbs))
        addMessage(ChatMessage.UserText(text = "✏️ Edited meal items"))
        conversationManager.onMealUpdated()
    }

    fun updateMealCarbs(newTotalCarbs: Float) {
        val currentMeal = MealSessionManager.currentMeal ?: return
        MealSessionManager.setCurrentMeal(currentMeal.copy(carbs = newTotalCarbs))
        addMessage(ChatMessage.UserText(text = "✏️ Carbs: ${newTotalCarbs}g"))
        conversationManager.onMealUpdated()
    }

    private fun parseFreeTextWithLlm(text: String, state: ChatState) {
        addMessage(ChatMessage.BotLoading(text = "Thinking…"))
        viewModelScope.launch {
            try {
                val request = ChatParseRequestDto(text = text, state = state.name)
                val response = withContext(Dispatchers.IO) { RetrofitClient.api.parseChatText(request) }
                removeAllLoading()
                if (response.isSuccessful && response.body() != null) {
                    conversationManager.handleLlmResponse(response.body()!!)
                } else {
                    addMessage(ChatMessage.BotText(text = "I couldn't understand that. Try the buttons or rephrase."))
                    conversationManager.handleLlmResponse(com.example.insuscan.network.dto.ChatParseResponseDto(action = "unknown", items = null, glucose = null, activity = null, icr = null, isf = null, targetGlucose = null, message = null))
                }
            } catch (e: Exception) {
                removeAllLoading()
                addMessage(ChatMessage.BotText(text = "I couldn't understand that. Try the buttons or rephrase."))
                FileLogger.log("CHAT_VM", "LLM error: ${e.message}")
                conversationManager.handleLlmResponse(com.example.insuscan.network.dto.ChatParseResponseDto(action = "unknown", items = null, glucose = null, activity = null, icr = null, isf = null, targetGlucose = null, message = null))
            }
        }
    }

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
                if (message is ChatMessage.BotText) {
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

    internal fun addMessage(message: ChatMessage) {
        val current = _messages.value.orEmpty().toMutableList()
        current.add(message)
        _messages.value = current
    }

    internal fun removeMessage(messageId: String) {
        val current = _messages.value.orEmpty().toMutableList()
        current.removeAll { it.id == messageId }
        _messages.value = current
    }

    private fun removeAllLoading() {
        val current = _messages.value.orEmpty().toMutableList()
        current.removeAll { it is ChatMessage.BotLoading }
        _messages.value = current
    }

    internal fun clearMessages() { _messages.value = emptyList() }
    
    internal fun fireImagePickEvent(value: String) { fireEvent(_imagePickEvent, value) }
    internal fun fireEditFoodEvent() { _editFoodEvent.value = true; _editFoodEvent.value = false }
    internal fun fireNavigationEvent(value: String) { fireEvent(_navigationEvent, value) }
    internal fun fireEditActivityEvent() { _editActivityEvent.value = true; _editActivityEvent.value = false }
    internal fun fireEditSickStressEvent() { _editSickStressEvent.value = true; _editSickStressEvent.value = false }

    private fun fireEvent(liveData: MutableLiveData<String?>, value: String) {
        liveData.value = value
        liveData.value = null
    }
}