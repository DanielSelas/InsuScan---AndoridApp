package com.example.insuscan.chat.delegates

import com.example.insuscan.chat.ChatMessage
import com.example.insuscan.chat.ChatViewModel

class ChatScanDelegate(private val viewModel: ChatViewModel) {

    fun onScanCompleted(meal: com.example.insuscan.meal.Meal) {
        meal.imagePath?.let { viewModel.addMessage(ChatMessage.UserImage(imagePath = it)) }
        viewModel.conversationManager.onScanSuccess(meal)
    }
}
