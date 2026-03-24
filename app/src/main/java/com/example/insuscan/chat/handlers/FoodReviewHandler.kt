package com.example.insuscan.chat.handlers

import com.example.insuscan.chat.ActionButton
import com.example.insuscan.chat.ChatMessage
import com.example.insuscan.chat.ChatState
import com.example.insuscan.chat.ChipStyle
import com.example.insuscan.chat.ConversationManager
import com.example.insuscan.meal.FoodItem
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager

class FoodReviewHandler(private val manager: ConversationManager) {

    fun showFoodReview(items: List<FoodItem>) {
        val totalCarbs = items.sumOf { (it.carbsGrams ?: 0f).toDouble() }.toFloat()
        manager.setCurrentState(ChatState.REVIEWING_FOOD)

        manager.callback?.onBotMessage(ChatMessage.BotText(text = "── 🍽️ Meal Review ──"))
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "✅ Meal analysis complete! Here's what I found:"))
        manager.callback?.onBotMessage(ChatMessage.BotFoodCard(foodItems = items, totalCarbs = totalCarbs))

        val missing = items.filter { (it.carbsGrams ?: 0f) == 0f }
        if (missing.isNotEmpty()) {
            val names = missing.joinToString(", ") { it.name }
            manager.callback?.onBotMessage(ChatMessage.BotText(text = "⚠️ No carb data for: $names. You can edit or continue."))
        }

        manager.callback?.onRequestEditMealSheet()
        showFoodActions()
        manager.notifyStateChanged()
    }

    fun showFoodActions() {
        manager.setActions(listOf(
            ActionButton("confirm_food", "✅ Confirm", row = 0, style = ChipStyle.PRIMARY),
            ActionButton("edit_food", "✏️ Edit Items", row = 0, style = ChipStyle.SECONDARY)
        ))
    }

    fun onFoodConfirmed() {
        if (manager.currentState != ChatState.REVIEWING_FOOD) return
        manager.clearActions()
        manager.isEditingStep = false
        manager.resultHandler.performCalculation()
    }

    fun onMealUpdated() {
        val meal = MealSessionManager.currentMeal ?: return
        val items = meal.foodItems ?: emptyList()
        val totalCarbs = meal.carbs

        manager.callback?.onBotMessage(ChatMessage.BotText(text = "Updated meal:"))
        manager.callback?.onBotMessage(ChatMessage.BotFoodCard(foodItems = items, totalCarbs = totalCarbs))
        showFoodActions()
    }

    fun onLlmFoodItems(items: List<FoodItem>, merge: Boolean = false) {
        if (items.isEmpty()) {
            manager.callback?.onBotMessage(ChatMessage.BotText(text = "I couldn't identify any food. Try a photo instead?"))
            manager.llmHandler.restoreCurrentStateActions()
            return
        }

        if (merge && manager.currentState == ChatState.REVIEWING_FOOD && MealSessionManager.currentMeal != null) {
            val current = MealSessionManager.currentMeal!!
            val updated = current.foodItems?.toMutableList() ?: mutableListOf()
            updated.addAll(items)
            val totalCarbs = updated.sumOf { (it.carbsGrams ?: 0f).toDouble() }.toFloat()
            MealSessionManager.setCurrentMeal(current.copy(foodItems = updated, carbs = totalCarbs))

            manager.callback?.onBotMessage(ChatMessage.BotText(text = "Added: ${items.joinToString(", ") { it.name }}"))
            manager.callback?.onFoodItemsAddedToSheet(items)
            onMealUpdated()
            return
        }

        val totalCarbs = items.sumOf { (it.carbsGrams ?: 0f).toDouble() }.toFloat()
        val meal = Meal(title = "Manual Entry", foodItems = items, carbs = totalCarbs)
        MealSessionManager.setCurrentMeal(meal)

        manager.setCurrentState(ChatState.REVIEWING_FOOD)
        manager.callback?.onBotMessage(ChatMessage.BotText(text = "Review and edit your meal items below:"))
        manager.callback?.onRequestEditMealSheet()

        manager.setActions(listOf(
            ActionButton("confirm_food", "✅ Looks Good"),
            ActionButton("edit_food", "✏️ Edit Again")
        ))
        manager.notifyStateChanged()
    }

    fun proceedToFoodReview() {
        if (manager.scanComplete) {
            val meal = manager.pendingScanMeal
            if (meal != null && !meal.foodItems.isNullOrEmpty()) {
                showFoodReview(meal.foodItems!!)
            } else {
                MealSessionManager.setCurrentMeal(Meal(title = "Manual Entry", carbs = 0f, foodItems = emptyList()))
                manager.setCurrentState(ChatState.REVIEWING_FOOD)
                manager.callback?.onBotMessage(ChatMessage.BotText(text = "No food items detected. Add items manually:"))
                manager.callback?.onRequestEditMealSheet()
                showFoodActions()
                manager.notifyStateChanged()
            }
        } else {
            manager.waitingForScan = true
            val msg = if (manager.waitingForScan) "⏳ Still analyzing..." else "⏳ Analyzing your meal… Feel free to review your inputs while we wait."
            manager.callback?.onBotMessage(ChatMessage.BotText(text = msg))
            manager.setActions(listOf(ActionButton("edit_step_wait", "✏️ Review/Edit Steps", row = 0)))
        }
    }
}
