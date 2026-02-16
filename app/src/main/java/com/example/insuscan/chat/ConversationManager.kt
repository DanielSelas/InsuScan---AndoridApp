package com.example.insuscan.chat

import android.content.Context
import com.example.insuscan.meal.FoodItem
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.FileLogger
import com.example.insuscan.utils.InsulinCalculatorUtil
import com.example.insuscan.network.dto.ChatParseResponseDto

// State machine for the chat conversation flow.
// Drives state transitions and generates bot messages + sticky buttons.
// All interactive buttons go through sticky area — cards are display-only.
class ConversationManager(private val context: Context) {

    var currentState: ChatState = ChatState.AWAITING_IMAGE
        private set

    // Clarification context — saves the original text when LLM asks for more info
    private var pendingClarificationContext: String? = null
    private var lastUserText: String = ""

    // Parallel scan support: scan runs in background while questions are asked
    private var pendingScanMeal: Meal? = null
    private var scanComplete: Boolean = false
    private var waitingForScan: Boolean = false  // true when questions are done but scan isn't

    // Step-edit loop: when editing a specific step from the result screen
    private var isEditingStep: Boolean = false

    // Collected data during conversation
    var collectedGlucose: Int? = null
        private set
    var collectedActivityLevel: String = "normal"
        private set
    var collectedSickMode: Boolean = false
        private set
    var collectedStressMode: Boolean = false
        private set
    var lastDoseResult: InsulinCalculatorUtil.DoseResult? = null
        private set

    // ViewModel listens to these callbacks
    interface Callback {
        fun onBotMessage(message: ChatMessage)
        fun onStateChanged(newState: ChatState)
        fun onNeedLlmParse(text: String, state: ChatState) {}
        fun onStickyAction(actions: List<ActionButton>?) {}
        fun onRequestEditMealSheet() {} // auto-open the edit sheet
        fun onRequestEditMedicalSheet() {} // for point 3
        fun onFoodItemsAddedToSheet(items: List<FoodItem>) {} // inject items into open sheet
        fun onRequestEditAdjustmentsDialog() {} // open adjustment % editor
    }

    var callback: Callback? = null
    private var hasStarted = false

    // -- Sticky button helpers --

    private fun setActions(actions: List<ActionButton>) {
        callback?.onStickyAction(actions)
    }

    private fun clearActions() {
        callback?.onStickyAction(emptyList())
    }

    // -- Start --

    fun startConversation() {
        if (hasStarted) return
        hasStarted = true
        currentState = ChatState.AWAITING_IMAGE

        callback?.onBotMessage(
            ChatMessage.BotText(text = "Hey! 👋 I'm your meal assistant.\nTake a photo or describe your meal to get started.")
        )
        showAwaitingImageActions()
        callback?.onStateChanged(currentState)
    }

    private fun showAwaitingImageActions() {
        setActions(listOf(
            ActionButton("take_photo", "📷 Photo"),
            ActionButton("pick_gallery", "🖼️ Gallery")
        ))
    }

    // -- Parallel flow: start questions immediately --

    fun beginParallelQuestions() {
        scanComplete = false
        pendingScanMeal = null
        waitingForScan = false
        callback?.onBotMessage(
            ChatMessage.BotText(text = "⏳ Analyzing your meal… Let's set up the rest while we wait.")
        )
        showMedicalReview()
    }

    // -- Scan results (may arrive mid-questions or after) --

    fun onScanSuccess(meal: Meal) {
        val items = meal.foodItems

        if (items.isNullOrEmpty()) {
            scanComplete = true
            pendingScanMeal = null
            // If questions are done and we're waiting, this is an error
            if (waitingForScan) {
                waitingForScan = false
                currentState = ChatState.AWAITING_IMAGE
                callback?.onBotMessage(ChatMessage.BotText(text = "I couldn't identify any food. Try again?"))
                showAwaitingImageActions()
                callback?.onStateChanged(currentState)
            } else {
                // Show inline notification; user will see it when questions finish
                callback?.onBotMessage(ChatMessage.BotText(text = "⚠️ I couldn't identify food from the image. You can add items manually."))
            }
            return
        }

        scanComplete = true
        pendingScanMeal = meal
        MealSessionManager.setCurrentMeal(meal)

        // If we were waiting for scan to finish (questions done), show food review now
        if (waitingForScan) {
            waitingForScan = false
            showFoodReview(meal.foodItems!!)
        }
        // Otherwise, scan arrived while questions are still being answered — just store it
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
            callback?.onStateChanged(currentState)
        } else {
            callback?.onBotMessage(
                ChatMessage.BotText(text = "⚠️ Analysis failed: $errorMessage. You can add items manually after the questions.")
            )
        }
    }

    // -- Food review --

    private fun showFoodReview(items: List<FoodItem>) {
        val totalCarbs = items.sumOf { (it.carbsGrams ?: 0f).toDouble() }.toFloat()
        currentState = ChatState.REVIEWING_FOOD

        callback?.onBotMessage(
            ChatMessage.BotText(text = "Here's what I found:")
        )
        callback?.onBotMessage(
            ChatMessage.BotFoodCard(foodItems = items, totalCarbs = totalCarbs)
        )

        // Warn about missing carb data
        val missing = items.filter { (it.carbsGrams ?: 0f) == 0f }
        if (missing.isNotEmpty()) {
            val names = missing.joinToString(", ") { it.name }
            callback?.onBotMessage(
                ChatMessage.BotText(text = "⚠️ No carb data for: $names. You can edit or continue.")
            )
        }

        // Auto-open the edit bottom sheet so user can edit immediately
        callback?.onRequestEditMealSheet()

        showFoodActions()
        callback?.onStateChanged(currentState)
    }

    private fun showFoodActions() {
        setActions(listOf(
            ActionButton("confirm_food", "✅ Confirm"),
            ActionButton("edit_food", "✏️ Edit Items")
        ))
    }

    fun onFoodConfirmed() {
        if (currentState != ChatState.REVIEWING_FOOD) return
        clearActions()
        // Whether from normal flow or step-edit, confirming food → calculate
        isEditingStep = false
        performCalculation()
    }

    // Called after user edits items in the bottom sheet
    fun onMealUpdated() {
        val meal = MealSessionManager.currentMeal ?: return
        val items = meal.foodItems ?: emptyList()
        val totalCarbs = meal.carbs

        callback?.onBotMessage(ChatMessage.BotText(text = "Updated meal:"))
        callback?.onBotMessage(
            ChatMessage.BotFoodCard(foodItems = items, totalCarbs = totalCarbs)
        )
        showFoodActions()
    }

    // -- LLM food items (from text input) --

    fun onLlmFoodItems(items: List<FoodItem>, merge: Boolean = false) {
        if (items.isEmpty()) {
            callback?.onBotMessage(ChatMessage.BotText(text = "I couldn't identify any food. Try a photo instead?"))
            restoreCurrentStateActions()
            return
        }

        if (merge && currentState == ChatState.REVIEWING_FOOD && MealSessionManager.currentMeal != null) {
            // Add to existing meal
            val current = MealSessionManager.currentMeal!!
            val updated = current.foodItems?.toMutableList() ?: mutableListOf()
            updated.addAll(items)
            val totalCarbs = updated.sumOf { (it.carbsGrams ?: 0f).toDouble() }.toFloat()
            MealSessionManager.setCurrentMeal(current.copy(foodItems = updated, carbs = totalCarbs))

            callback?.onBotMessage(
                ChatMessage.BotText(text = "Added: ${items.joinToString(", ") { it.name }}")
            )
            // Inject into the open edit sheet
            callback?.onFoodItemsAddedToSheet(items)
            onMealUpdated()
            return
        }

        val totalCarbs = items.sumOf { (it.carbsGrams ?: 0f).toDouble() }.toFloat()
        val meal = Meal(title = "Manual Entry", foodItems = items, carbs = totalCarbs)
        MealSessionManager.setCurrentMeal(meal)

        currentState = ChatState.REVIEWING_FOOD
        callback?.onBotMessage(ChatMessage.BotText(text = "Review and edit your meal items below:"))

        callback?.onRequestEditMealSheet()

        setActions(listOf(
            ActionButton("confirm_food", "✅ Looks Good"),
            ActionButton("edit_food", "✏️ Edit Again")
        ))
        callback?.onStateChanged(currentState)
    }

    // -- Medical review --

    private fun showMedicalReview() {
        val pm = UserProfileManager
        val icr = pm.getGramsPerUnit(context)
        val isf = pm.getCorrectionFactor(context)
        val targetGlucose = pm.getTargetGlucose(context)
        val glucoseUnits = pm.getGlucoseUnits(context)

        if (icr == null || isf == null || targetGlucose == null) {
            callback?.onBotMessage(
                ChatMessage.BotText(text = "⚠️ Medical profile incomplete. Set ICR, ISF, and target in your Profile first.")
            )
            setActions(listOf(
                ActionButton("open_profile", "Open Profile")
            ))
            return
        }

        currentState = ChatState.REVIEWING_MEDICAL
        callback?.onBotMessage(
            ChatMessage.BotMedicalCard(icr = icr, isf = isf, targetGlucose = targetGlucose, glucoseUnits = glucoseUnits)
        )
        showMedicalActions()
        callback?.onStateChanged(currentState)
    }

    private fun showMedicalActions() {
        setActions(listOf(
            ActionButton("confirm_medical", "✅ Confirm"),
            ActionButton("edit_medical", "✏️ Edit")
        ))
    }

    fun onMedicalConfirmed() {
        if (currentState != ChatState.REVIEWING_MEDICAL) return
        clearActions()
        if (isEditingStep) {
            isEditingStep = false
            performCalculation()
        } else {
            askGlucose()
        }
    }

    fun onRequestMedicalEdit() {
        if (currentState != ChatState.REVIEWING_MEDICAL) return
        currentState = ChatState.EDITING_MEDICAL
        clearActions()
        // open the medical edit bottom sheet
        callback?.onRequestEditMedicalSheet()

        setActions(listOf(
            ActionButton("cancel_edit_medical", "Cancel")
        ))
        callback?.onStateChanged(currentState)
    }


    // Called when LLM or local parser updates medical params
    fun onMedicalParamsUpdated(icr: Double?, isf: Double?, target: Int?) {
        val pm = UserProfileManager
        if (icr != null) pm.saveInsulinCarbRatio(context, icr.toString())
        if (isf != null) pm.saveCorrectionFactor(context, isf.toFloat())
        if (target != null) pm.saveTargetGlucose(context, target)

        callback?.onBotMessage(ChatMessage.BotText(text = "✅ Profile updated."))

        // Show the refreshed card and stay in REVIEWING_MEDICAL
        currentState = ChatState.REVIEWING_MEDICAL
        val newIcr = pm.getGramsPerUnit(context)
        val newIsf = pm.getCorrectionFactor(context)
        val newTarget = pm.getTargetGlucose(context)
        val units = pm.getGlucoseUnits(context)

        if (newIcr != null && newIsf != null && newTarget != null) {
            callback?.onBotMessage(
                ChatMessage.BotMedicalCard(icr = newIcr, isf = newIsf, targetGlucose = newTarget, glucoseUnits = units)
            )
        }
        showMedicalActions()
        callback?.onStateChanged(currentState)
    }

    fun onCancelMedicalEdit() {
        currentState = ChatState.REVIEWING_MEDICAL
        showMedicalActions()
        callback?.onStateChanged(currentState)
    }

    // -- Glucose --

    private fun askGlucose() {
        currentState = ChatState.ASKING_GLUCOSE
        callback?.onBotMessage(
            ChatMessage.BotText(text = "What's your current glucose level? (type a number, or skip)")
        )
        setActions(listOf(
            ActionButton("skip_glucose", "Skip")
        ))
        callback?.onStateChanged(currentState)
    }

    fun onGlucoseProvided(glucose: Int?) {
        clearActions()
        collectedGlucose = glucose
        if (glucose != null) {
            callback?.onBotMessage(ChatMessage.BotText(text = "Got it — glucose: $glucose"))
        }
        if (isEditingStep) {
            isEditingStep = false
            performCalculation()
        } else {
            askActivity()
        }
    }

    // -- Activity (with percentage labels) --

    private fun askActivity() {
        currentState = ChatState.ASKING_ACTIVITY
        callback?.onBotMessage(ChatMessage.BotText(text = "Any exercise to account for?"))
        showActivityOptions()
        callback?.onStateChanged(currentState)
    }

    private fun showActivityOptions() {
        val pm = UserProfileManager
        val lightPct = pm.getLightExerciseAdjustment(context)
        val intensePct = pm.getIntenseExerciseAdjustment(context)

        setActions(listOf(
            ActionButton("activity_none", "None"),
            ActionButton("activity_light", "🏃 Light (-${lightPct}%)"),
            ActionButton("activity_intense", "🏋️ Intense (-${intensePct}%)")
        ))
    }

    fun onActivitySelected(activity: String) {
        clearActions()
        collectedActivityLevel = activity
        val pm = UserProfileManager
        val label = when (activity) {
            "light" -> "Light exercise (-${pm.getLightExerciseAdjustment(context)}%)"
            "intense" -> "Intense exercise (-${pm.getIntenseExerciseAdjustment(context)}%)"
            else -> "No exercise"
        }
        callback?.onBotMessage(ChatMessage.BotText(text = "Activity: $label"))

        if (isEditingStep) {
            // Editing activity from step-edit loop → recalculate
            isEditingStep = false
            performCalculation()
        } else {
            // Normal flow → proceed to food review
            proceedToFoodReview()
        }
    }

    /** After all questions answered, show food review (or wait for scan) */
    private fun proceedToFoodReview() {
        if (scanComplete) {
            val meal = pendingScanMeal
            if (meal != null && !meal.foodItems.isNullOrEmpty()) {
                showFoodReview(meal.foodItems!!)
            } else {
                // Scan succeeded but no items, or scan errored — let user add manually
                MealSessionManager.setCurrentMeal(
                    Meal(title = "Manual Entry", carbs = 0f, foodItems = emptyList())
                )
                currentState = ChatState.REVIEWING_FOOD
                callback?.onBotMessage(ChatMessage.BotText(text = "No food items detected. Add items manually:"))
                callback?.onRequestEditMealSheet()
                showFoodActions()
                callback?.onStateChanged(currentState)
            }
        } else {
            // Still waiting for scan — show loading
            waitingForScan = true
            callback?.onBotMessage(
                ChatMessage.BotText(text = "⏳ Still analyzing your meal image… Just a moment.")
            )
        }
    }

    // -- Calculation + Result --

    private fun performCalculation() {
        currentState = ChatState.CALCULATING
        callback?.onStateChanged(currentState)

        val meal = MealSessionManager.currentMeal
        val totalCarbs = meal?.foodItems?.sumOf { (it.carbsGrams ?: 0f).toDouble() }?.toFloat() ?: meal?.carbs ?: 0f
        val gramsPerUnit = UserProfileManager.getGramsPerUnit(context) ?: 10f

        val result = InsulinCalculatorUtil.calculate(
            context = context,
            carbs = totalCarbs,
            glucose = collectedGlucose,
            activeInsulin = null,
            activityLevel = collectedActivityLevel,
            gramsPerUnit = gramsPerUnit,
            isSick = collectedSickMode,
            isStress = collectedStressMode
        )

        lastDoseResult = result
        currentState = ChatState.SHOWING_RESULT

        // Show the dose breakdown card
        callback?.onBotMessage(ChatMessage.BotText(text = "Here's your dose:"))
        callback?.onBotMessage(ChatMessage.BotDoseResult(doseResult = result))

        // Show detailed summary card
        showSummaryCard(result, totalCarbs)

        // Show multi-select hint below the summary
        callback?.onBotMessage(
            ChatMessage.BotText(text = "💡 You can toggle multiple adjustments together — they stack.")
        )

        showResultActions()
        callback?.onStateChanged(currentState)
    }

    private fun showSummaryCard(result: InsulinCalculatorUtil.DoseResult, totalCarbs: Float) {
        val pm = UserProfileManager
        val meal = MealSessionManager.currentMeal
        val items = meal?.foodItems ?: emptyList()

        callback?.onBotMessage(
            ChatMessage.BotSummaryCard(
                foodItems = items,
                totalCarbs = totalCarbs,
                glucoseLevel = collectedGlucose,
                activityLevel = collectedActivityLevel,
                isSick = collectedSickMode,
                isStress = collectedStressMode,
                icr = pm.getGramsPerUnit(context) ?: 0f,
                isf = pm.getCorrectionFactor(context) ?: 0f,
                targetGlucose = pm.getTargetGlucose(context) ?: 0,
                glucoseUnits = pm.getGlucoseUnits(context),
                sickPct = if (collectedSickMode) pm.getSickDayAdjustment(context) else 0,
                stressPct = if (collectedStressMode) pm.getStressAdjustment(context) else 0,
                exercisePct = when (collectedActivityLevel) {
                    "light" -> pm.getLightExerciseAdjustment(context)
                    "intense" -> pm.getIntenseExerciseAdjustment(context)
                    else -> 0
                },
                doseResult = result
            )
        )
    }

    private fun showResultActions() {
        setActions(listOf(
            ActionButton("save_meal", "💾 Save Meal", row = 0),
            ActionButton("edit_step", "✏️ Edit a Step", row = 0)
        ))
    }

    // -- Step-edit loop --

    fun onChooseEditStep() {
        currentState = ChatState.CHOOSING_EDIT_STEP
        callback?.onBotMessage(
            ChatMessage.BotText(text = "Which step would you like to edit?")
        )
        setActions(listOf(
            ActionButton("edit_step_food", "🍽️ Food Items"),
            ActionButton("edit_step_medical", "⚕️ Medical"),
            ActionButton("edit_step_glucose", "🩸 Glucose"),
            ActionButton("edit_step_activity", "🏃 Activity"),
            ActionButton("back_to_result", "← Back")
        ))
        callback?.onStateChanged(currentState)
    }

    fun onEditStepFood() {
        isEditingStep = true
        clearActions()
        val meal = MealSessionManager.currentMeal
        val items = meal?.foodItems ?: emptyList()
        showFoodReview(items)
    }

    fun onEditStepMedical() {
        isEditingStep = true
        clearActions()
        showMedicalReview()
    }

    fun onEditStepGlucose() {
        isEditingStep = true
        clearActions()
        askGlucose()
    }

    fun onEditStepActivity() {
        isEditingStep = true
        clearActions()
        askActivity()
    }

    // Activity adjustment sub-menu from result screen
    fun onAdjustActivity() {
        currentState = ChatState.ADJUSTING_ACTIVITY
        val pm = UserProfileManager
        val lightPct = pm.getLightExerciseAdjustment(context)
        val intensePct = pm.getIntenseExerciseAdjustment(context)

        setActions(listOf(
            ActionButton("activity_none", "No Exercise"),
            ActionButton("activity_light", "🏃 Light (-${lightPct}%)"),
            ActionButton("activity_intense", "🏋️ Intense (-${intensePct}%)"),
            ActionButton("back_to_result", "← Back")
        ))
        callback?.onStateChanged(currentState)
    }

    fun onActivityAdjusted(activity: String) {
        collectedActivityLevel = activity
        val pm = UserProfileManager
        val label = when (activity) {
            "light" -> "Light exercise (-${pm.getLightExerciseAdjustment(context)}%)"
            "intense" -> "Intense exercise (-${pm.getIntenseExerciseAdjustment(context)}%)"
            else -> "No exercise"
        }
        callback?.onBotMessage(ChatMessage.BotText(text = "Updated: $label"))
        performCalculation() // recalc and return to SHOWING_RESULT
    }

    fun onBackToResult() {
        currentState = ChatState.SHOWING_RESULT
        showResultActions()
        callback?.onStateChanged(currentState)
    }

    fun toggleSickMode() {
        collectedSickMode = !collectedSickMode
        val pm = UserProfileManager
        val pct = pm.getSickDayAdjustment(context)
        val status = if (collectedSickMode) "Sick mode ON (+${pct}%)" else "Sick mode OFF"
        callback?.onBotMessage(ChatMessage.BotText(text = status))
        performCalculation()
    }

    fun toggleStressMode() {
        collectedStressMode = !collectedStressMode
        val pm = UserProfileManager
        val pct = pm.getStressAdjustment(context)
        val status = if (collectedStressMode) "Stress mode ON (+${pct}%)" else "Stress mode OFF"
        callback?.onBotMessage(ChatMessage.BotText(text = status))
        performCalculation()
    }

    // Open the adjustment % editor dialog
    fun onEditAdjustments() {
        callback?.onRequestEditAdjustmentsDialog()
    }

    // Called after user edits adjustment percentages in the dialog
    fun onAdjustmentPercentagesUpdated() {
        callback?.onBotMessage(ChatMessage.BotText(text = "✅ Adjustment percentages updated."))
        performCalculation()
    }

    // -- Save --

    fun onMealSaved() {
        currentState = ChatState.DONE
        callback?.onBotMessage(ChatMessage.BotSaved(text = "Meal saved! ✅"))
        setActions(listOf(
            ActionButton("new_scan", "📷 New Scan"),
            ActionButton("history", "📋 History")
        ))
        callback?.onStateChanged(currentState)

        // Reset collected data for next round
        collectedGlucose = null
        collectedActivityLevel = "normal"
        collectedSickMode = false
        collectedStressMode = false
    }

    // Reset for new scan after DONE
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
        startConversation()
    }

    // -- Free text handling --

    fun handleFreeText(text: String) {
        // If we're in clarification mode, combine context with new input
        val textToProcess = if (pendingClarificationContext != null) {
            val combined = "${pendingClarificationContext}, $text"
            pendingClarificationContext = null
            combined
        } else {
            text
        }
        lastUserText = text

        val parsed = FreeTextParser.parse(textToProcess, currentState)

        when (currentState) {
            ChatState.REVIEWING_FOOD -> {
                when (parsed) {
                    is FreeTextParser.ParseResult.Confirm -> onFoodConfirmed()
                    else -> {
                        // Open the edit sheet and send to LLM in parallel
                        callback?.onRequestEditMealSheet()
                        clearActions()
                        callback?.onNeedLlmParse(textToProcess, currentState)
                    }
                }
            }
            ChatState.REVIEWING_MEDICAL -> {
                when (parsed) {
                    is FreeTextParser.ParseResult.Confirm -> onMedicalConfirmed()
                    else -> {
                        // Open the medical edit sheet and send to LLM in parallel
                        callback?.onRequestEditMedicalSheet()
                        clearActions()
                        callback?.onNeedLlmParse(textToProcess, currentState)
                    }
                }
            }
            ChatState.EDITING_MEDICAL -> {
                // Forward to LLM to parse medical values like "ICR 12" or "target 110"
                clearActions()
                callback?.onNeedLlmParse(textToProcess, currentState)
            }
            ChatState.ASKING_GLUCOSE -> {
                handleGlucoseText(text)
            }
            ChatState.ASKING_ACTIVITY -> {
                handleActivityText(text)
            }
            ChatState.SHOWING_RESULT -> {
                // In result screen, only buttons matter — but handle confirm/save text too
                val lower = text.lowercase().trim()
                if (lower == "save" || lower == "yes" || lower == "confirm") {
                    // Trigger save — ViewModel handles actual saving
                    callback?.onBotMessage(ChatMessage.BotText(text = "Use the 💾 Save Meal button to save."))
                    showResultActions()
                } else {
                    callback?.onBotMessage(
                        ChatMessage.BotText(text = "Adjust settings with the buttons below, or tap 💾 Save Meal.")
                    )
                    showResultActions()
                }
            }
            ChatState.CLARIFYING -> {
                // User is responding to a clarification question — send combined to LLM
                clearActions()
                callback?.onNeedLlmParse(textToProcess, currentState)
            }
            ChatState.AWAITING_IMAGE -> {
                // Try parsing as food description via LLM
                clearActions()
                callback?.onNeedLlmParse(textToProcess, currentState)
            }
            ChatState.DONE -> {
                callback?.onBotMessage(
                    ChatMessage.BotText(text = "Start a new scan or check your history!")
                )
                setActions(listOf(
                    ActionButton("new_scan", "📷 New Scan"),
                    ActionButton("history", "📋 History")
                ))
            }
            else -> {
                callback?.onNeedLlmParse(textToProcess, currentState)
            }
        }
    }

    private fun handleGlucoseText(text: String) {
        val lower = text.lowercase().trim()
        if (lower == "skip" || lower == "no" || lower == "none") {
            onGlucoseProvided(null)
            return
        }
        // Try to extract number — support "120", "120 mg/dl", etc.
        val number = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toIntOrNull()
        if (number != null) {
            onGlucoseProvided(number)
        } else {
            callback?.onBotMessage(
                ChatMessage.BotText(text = "Please type a number (e.g. 120) or 'skip'.")
            )
        }
    }

    private fun handleActivityText(text: String) {
        val lower = text.lowercase().trim()
        when {
            lower.contains("none") || lower.contains("no") || lower == "skip" -> onActivitySelected("normal")
            lower.contains("light") -> onActivitySelected("light")
            lower.contains("intense") || lower.contains("heavy") || lower.contains("hard") -> onActivitySelected("intense")
            else -> {
                callback?.onBotMessage(
                    ChatMessage.BotText(text = "Choose: None, Light, or Intense.")
                )
            }
        }
    }

    // -- LLM response handling --

    fun handleLlmResponse(resp: ChatParseResponseDto) {
        // Clarify — LLM needs more info from user
        if (resp.action == "clarify") {
            pendingClarificationContext = lastUserText
            currentState = ChatState.CLARIFYING
            callback?.onBotMessage(
                ChatMessage.BotText(text = resp.message ?: "Could you be more specific? (e.g. amount in grams)")
            )
            callback?.onStateChanged(currentState)
            return
        }

        // Unknown — couldn't parse at all
        if (resp.action == "unknown") {
            callback?.onBotMessage(
                ChatMessage.BotText(text = resp.message ?: "I didn't catch that. Try rephrasing?")
            )
            restoreCurrentStateActions()
            return
        }

        // Successful parse — clear clarification context
        pendingClarificationContext = null

        when (resp.action) {
            "add_food" -> {
                val items = resp.items?.map {
                    FoodItem(
                        name = it.name,
                        carbsGrams = it.estimatedCarbsGrams,
                        quantity = it.quantity?.toFloat() ?: 1f,
                        quantityUnit = null
                    )
                } ?: emptyList()

                val shouldMerge = (currentState == ChatState.REVIEWING_FOOD ||
                        currentState == ChatState.CLARIFYING) &&
                        MealSessionManager.currentMeal != null

                onLlmFoodItems(items, merge = shouldMerge)
            }
            "set_medical_params" -> {
                onMedicalParamsUpdated(resp.icr, resp.isf, resp.targetGlucose)
            }
            "set_glucose" -> {
                resp.glucose?.let { onGlucoseProvided(it) }
                    ?: callback?.onBotMessage(ChatMessage.BotText(text = resp.message ?: "Couldn't parse glucose."))
            }
            "set_activity" -> {
                resp.activity?.let { onActivitySelected(it) }
                    ?: callback?.onBotMessage(ChatMessage.BotText(text = resp.message ?: "Couldn't parse activity."))
            }
            "confirm" -> {
                when (currentState) {
                    ChatState.REVIEWING_FOOD -> onFoodConfirmed()
                    ChatState.REVIEWING_MEDICAL -> onMedicalConfirmed()
                    else -> restoreCurrentStateActions()
                }
            }
            else -> {
                if (!resp.message.isNullOrBlank()) {
                    callback?.onBotMessage(ChatMessage.BotText(text = resp.message))
                }
                restoreCurrentStateActions()
            }
        }
    }

    // Restore buttons for whatever state we're in now
    private fun restoreCurrentStateActions() {
        when (currentState) {
            ChatState.AWAITING_IMAGE -> showAwaitingImageActions()
            ChatState.REVIEWING_FOOD -> showFoodActions()
            ChatState.REVIEWING_MEDICAL -> showMedicalActions()
            ChatState.EDITING_MEDICAL -> setActions(listOf(ActionButton("cancel_edit_medical", "Cancel")))
            ChatState.ASKING_GLUCOSE -> setActions(listOf(ActionButton("skip_glucose", "Skip")))
            ChatState.ASKING_ACTIVITY -> showActivityOptions()
            ChatState.SHOWING_RESULT -> showResultActions()
            ChatState.ADJUSTING_ACTIVITY -> onAdjustActivity()
            ChatState.DONE -> setActions(listOf(
                ActionButton("new_scan", "📷 New Scan"),
                ActionButton("history", "📋 History")
            ))
            else -> clearActions()
        }
    }
}