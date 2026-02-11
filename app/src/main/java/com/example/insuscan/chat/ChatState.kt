package com.example.insuscan.chat

// All possible states in the chat conversation flow
enum class ChatState {
    IDLE,               // initial state - waiting for user to start
    AWAITING_IMAGE,     // asked user to take/upload a photo
    SCANNING,           // image sent to server, waiting for results
    REVIEWING_FOOD,     // showing food card, waiting for confirm/edit
    CLARIFYING,         // asking about items with 0 carbs
    REVIEWING_MEDICAL,  // showing medical profile, waiting for confirm
    COLLECTING_EXTRAS,  // asking about activity, sick mode, etc.
    CALCULATING,        // running insulin calculation
    SHOWING_RESULT,     // showing dose result card
    SAVING,             // saving meal to server
    DONE                // meal saved, flow complete
}
