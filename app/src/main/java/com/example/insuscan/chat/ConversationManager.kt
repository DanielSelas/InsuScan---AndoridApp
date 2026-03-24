package com.example.insuscan.chat

import android.content.Context
import com.example.insuscan.chat.handlers.*
import com.example.insuscan.meal.FoodItem
import com.example.insuscan.meal.Meal
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.FileLogger
import com.example.insuscan.utils.InsulinCalculatorUtil
import com.example.insuscan.network.dto.ChatParseResponseDto

class ConversationManager(val context: Context) {

    var currentState: ChatState = ChatState.AWAITING_IMAGE
        private set

    internal var isReturningUser: Boolean = false
    internal var pendingClarificationContext: String? = null
    internal var lastUserText: String = ""

    internal var pendingScanMeal: Meal? = null
    internal var scanComplete: Boolean = false
    internal var waitingForScan: Boolean = false
    internal var isEditingStep: Boolean = false

    var collectedGlucose: Int? = null
        internal set
    var collectedActivityLevel: String = "normal"
        internal set
    var collectedSickMode: Boolean = false
        internal set
    var collectedStressMode: Boolean = false
        internal set
    var lastDoseResult: InsulinCalculatorUtil.DoseResult? = null
        internal set

    interface Callback {
        fun onBotMessage(message: ChatMessage)
        fun onStateChanged(newState: ChatState)
        fun onNeedLlmParse(text: String, state: ChatState) {}
        fun onStickyAction(actions: List<ActionButton>?) {}
        fun onRequestEditMealSheet() {}
        fun onRequestEditMedicalSheet() {}
        fun onFoodItemsAddedToSheet(items: List<FoodItem>) {}
        fun onRequestEditAdjustmentsDialog() {}
    }

    var callback: Callback? = null
    private var hasStarted = false

    internal val foodHandler = FoodReviewHandler(this)
    internal val medicalHandler = MedicalReviewHandler(this)
    internal val assessmentHandler = AssessmentHandler(this)
    internal val resultHandler = ChatResultHandler(this)
    internal val llmHandler = ChatLlmHandler(this)

    fun setCurrentState(state: ChatState) {
        currentState = state
    }

    fun notifyStateChanged() {
        callback?.onStateChanged(currentState)
    }

    fun setActions(actions: List<ActionButton>) {
        callback?.onStickyAction(actions)
    }

    fun clearActions() {
        callback?.onStickyAction(emptyList())
    }

    fun startConversation() {
        if (hasStarted) return
        hasStarted = true
        currentState = ChatState.AWAITING_IMAGE

        val userName = UserProfileManager.getUserName(context)
        val greeting = if (isReturningUser && userName != null) {
            "Welcome back, $userName! 👋 Ready to log another meal?"
        } else if (userName != null) {
            "Hey, $userName! 👋 I'm your meal assistant.\nTake a photo or describe your meal to get started."
        } else {
            "Hey! 👋 I'm your meal assistant.\nTake a photo or describe your meal to get started."
        }

        callback?.onBotMessage(ChatMessage.BotText(text = greeting))
        showAwaitingImageActions()
        notifyStateChanged()
    }

    internal fun showAwaitingImageActions() {
        setActions(listOf(
            ActionButton("take_photo", "📷 Photo", row = 0, style = ChipStyle.PRIMARY),
            ActionButton("pick_gallery", "🖼️ Gallery", row = 0, style = ChipStyle.SECONDARY)
        ))
    }

    fun beginParallelQuestions() {
        scanComplete = false
        pendingScanMeal = null
        waitingForScan = false
        callback?.onBotMessage(
            ChatMessage.BotText(text = "⏳ Analyzing your meal…\nIn the meantime, I'll ask a few quick questions to calculate your dose accurately.")
        )
        medicalHandler.showMedicalReview()
    }

    fun onScanSuccess(meal: Meal) {
        val items = meal.foodItems

        if (items.isNullOrEmpty()) {
            scanComplete = true
            pendingScanMeal = null
            if (waitingForScan) {
                waitingForScan = false
                currentState = ChatState.AWAITING_IMAGE
                callback?.onBotMessage(ChatMessage.BotText(text = "I couldn't identify any food. Try again?"))
                showAwaitingImageActions()
                notifyStateChanged()
            }
            return
        }

        scanComplete = true
        pendingScanMeal = meal
        com.example.insuscan.meal.MealSessionManager.setCurrentMeal(meal)

        if (waitingForScan) {
            waitingForScan = false
            foodHandler.showFoodReview(meal.foodItems!!)
        }
    }

    fun onScanError(errorMessage: String) {
        FileLogger.log("CHAT", "Scan error: $errorMessage")
        scanComplete = true
        pendingScanMeal = null

        if (waitingForScan) {
            waitingForScan = false
            currentState = ChatState.AWAITING_IMAGE
            callback?.onBotMessage(
                ChatMessage.BotText(text = "Something went wrong: $errorMessage\nTry again?")
            )
            showAwaitingImageActions()
            notifyStateChanged()
        }
    }

    // Delegated calls
    fun onFoodConfirmed() = foodHandler.onFoodConfirmed()
    fun onMealUpdated() = foodHandler.onMealUpdated()
    fun onLlmFoodItems(items: List<FoodItem>, merge: Boolean = false) = foodHandler.onLlmFoodItems(items, merge)

    fun onMedicalConfirmed() = medicalHandler.onMedicalConfirmed()
    fun onRequestMedicalEdit() = medicalHandler.onRequestMedicalEdit()
    fun onMedicalParamsUpdated(icr: Double?, isf: Double?, target: Int?) = medicalHandler.onMedicalParamsUpdated(icr, isf, target)
    fun onCancelMedicalEdit() = medicalHandler.onCancelMedicalEdit()

    fun onGlucoseProvided(glucose: Int?) = assessmentHandler.onGlucoseProvided(glucose)
    fun onActivitySelected(activity: String) = assessmentHandler.onActivitySelected(activity)
    fun onAdjustmentSelected(sick: Boolean, stress: Boolean) = assessmentHandler.onAdjustmentSelected(sick, stress)
    fun onAdjustmentPercentagesUpdated() = assessmentHandler.onAdjustmentPercentagesUpdated()
    fun toggleSickMode() = assessmentHandler.toggleSickMode()
    fun toggleStressMode() = assessmentHandler.toggleStressMode()
    fun onEditAdjustments() = assessmentHandler.onEditAdjustments()

    fun onChooseEditStep() = resultHandler.onChooseEditStep()
    fun onEditStepFood() = resultHandler.onEditStepFood()
    fun onEditStepMedical() = resultHandler.onEditStepMedical()
    fun onEditStepGlucose() = resultHandler.onEditStepGlucose()
    fun onEditStepActivity() = resultHandler.onEditStepActivity()
    fun onEditStepAdjustments() = resultHandler.onEditStepAdjustments()
    fun onAdjustActivity() = resultHandler.onAdjustActivity()
    fun onActivityAdjusted(activity: String) = resultHandler.onActivityAdjusted(activity)
    fun onBackToResult() = resultHandler.onBackToResult()
    fun onMealSaved() = resultHandler.onMealSaved()

    fun handleFreeText(text: String) = llmHandler.handleFreeText(text)
    fun handleLlmResponse(resp: ChatParseResponseDto) = llmHandler.handleLlmResponse(resp)

    fun resetForNewScan() {
        hasStarted = false
        currentState = ChatState.AWAITING_IMAGE
        pendingClarificationContext = null
        lastUserText = ""
        collectedGlucose = null
        collectedActivityLevel = "normal"
        collectedSickMode = false
        collectedStressMode = false
        lastDoseResult = null
        isReturningUser = true
        startConversation()
    }
}