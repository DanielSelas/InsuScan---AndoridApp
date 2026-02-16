package com.example.insuscan.chat

// All possible states in the chat conversation flow
enum class ChatState {
    AWAITING_IMAGE,     // waiting for photo/gallery or text food description
    SCANNING,           // image sent to server, analyzing
    REVIEWING_FOOD,     // showing food card, waiting for confirm/edit
    CLARIFYING,         // LLM asked for more info (e.g. amount)
    REVIEWING_MEDICAL,  // showing medical profile card
    EDITING_MEDICAL,    // user is typing new medical values
    ASKING_GLUCOSE,     // asking for current glucose level
    ASKING_ACTIVITY,    // asking about exercise level
    CALCULATING,        // running insulin calc
    SHOWING_RESULT,     // showing dose result + adjustment toggles
    ADJUSTING_ACTIVITY, // sub-menu for picking activity level
    CHOOSING_EDIT_STEP, // user chose to edit a specific step (food/medical/glucose/activity)
    SAVING,             // saving meal to server
    DONE                // flow complete
}
