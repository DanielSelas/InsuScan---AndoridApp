package com.example.insuscan.chat

import com.example.insuscan.meal.FoodItem
import com.example.insuscan.utils.InsulinCalculatorUtil
import java.util.UUID

// All possible message types in the chat UI
sealed class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis()
) {
    // Bot sends a plain text message (left-aligned bubble)
    data class BotText(
        val text: String,
        val msgId: String = UUID.randomUUID().toString(),
        val ts: Long = System.currentTimeMillis()
    ) : ChatMessage(msgId, ts)

    // User sends a plain text message (right-aligned bubble)
    data class UserText(
        val text: String,
        val msgId: String = UUID.randomUUID().toString(),
        val ts: Long = System.currentTimeMillis()
    ) : ChatMessage(msgId, ts)

    // User sends an image (right-aligned thumbnail)
    data class UserImage(
        val imagePath: String,
        val msgId: String = UUID.randomUUID().toString(),
        val ts: Long = System.currentTimeMillis()
    ) : ChatMessage(msgId, ts)

    // Bot shows a loading/typing indicator
    data class BotLoading(
        val text: String = "Analyzing...",
        val msgId: String = UUID.randomUUID().toString(),
        val ts: Long = System.currentTimeMillis()
    ) : ChatMessage(msgId, ts)

    // Bot shows a food items card with confirm/edit buttons
    data class BotFoodCard(
        val foodItems: List<FoodItem>,
        val totalCarbs: Float,
        val msgId: String = UUID.randomUUID().toString(),
        val ts: Long = System.currentTimeMillis()
    ) : ChatMessage(msgId, ts)

    // Bot shows the medical profile card (ICR, ISF, target)
    data class BotMedicalCard(
        val icr: Float,
        val isf: Float,
        val targetGlucose: Int,
        val glucoseUnits: String? = "mg/dL",
        val msgId: String = UUID.randomUUID().toString(),
        val ts: Long = System.currentTimeMillis()
    ) : ChatMessage(msgId, ts)

    // Bot shows action buttons (activity level, sport, sick, etc.)
    data class BotActionButtons(
        val buttons: List<ActionButton>,
        val msgId: String = UUID.randomUUID().toString(),
        val ts: Long = System.currentTimeMillis()
    ) : ChatMessage(msgId, ts)

    // Bot shows the final dose result card
    data class BotDoseResult(
        val doseResult: InsulinCalculatorUtil.DoseResult,
        val msgId: String = UUID.randomUUID().toString(),
        val ts: Long = System.currentTimeMillis()
    ) : ChatMessage(msgId, ts)

    // Bot shows a detailed summary card before save
    data class BotSummaryCard(
        val foodItems: List<FoodItem>,
        val totalCarbs: Float,
        val glucoseLevel: Int?,
        val activityLevel: String,
        val isSick: Boolean,
        val isStress: Boolean,
        val icr: Float,
        val isf: Float,
        val targetGlucose: Int,
        val glucoseUnits: String,
        val sickPct: Int,
        val stressPct: Int,
        val exercisePct: Int,
        val doseResult: InsulinCalculatorUtil.DoseResult,
        val msgId: String = UUID.randomUUID().toString(),
        val ts: Long = System.currentTimeMillis()
    ) : ChatMessage(msgId, ts)

    // Bot confirms meal was saved
    data class BotSaved(
        val text: String = "Meal saved successfully! ✅",
        val msgId: String = UUID.randomUUID().toString(),
        val ts: Long = System.currentTimeMillis()
    ) : ChatMessage(msgId, ts)
}

// Simple button model for action chips
data class ActionButton(
    val actionId: String, // key used by ConversationManager to handle the tap
    val label: String,
    val row: Int = 0      // visual row grouping (0, 1, 2…)
)
