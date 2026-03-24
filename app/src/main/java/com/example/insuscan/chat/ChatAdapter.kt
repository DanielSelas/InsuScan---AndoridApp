package com.example.insuscan.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.chat.viewholders.*

// RecyclerView adapter for chat messages.
// Cards are display-only — all buttons are in the sticky area managed by Fragment.
class ChatAdapter(
    private val onActionButton: ((String) -> Unit)? = null
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatDiffCallback()) {

    companion object {
        private const val TYPE_BOT_TEXT = 0
        private const val TYPE_USER_TEXT = 1
        private const val TYPE_USER_IMAGE = 2
        private const val TYPE_BOT_LOADING = 3
        private const val TYPE_BOT_FOOD_CARD = 4
        private const val TYPE_BOT_MEDICAL_CARD = 5
        private const val TYPE_BOT_DOSE_RESULT = 6
        private const val TYPE_BOT_SAVED = 7
        private const val TYPE_BOT_SUMMARY = 8
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ChatMessage.BotText -> TYPE_BOT_TEXT
            is ChatMessage.UserText -> TYPE_USER_TEXT
            is ChatMessage.UserImage -> TYPE_USER_IMAGE
            is ChatMessage.BotLoading -> TYPE_BOT_LOADING
            is ChatMessage.BotFoodCard -> TYPE_BOT_FOOD_CARD
            is ChatMessage.BotMedicalCard -> TYPE_BOT_MEDICAL_CARD
            is ChatMessage.BotDoseResult -> TYPE_BOT_DOSE_RESULT
            is ChatMessage.BotSaved -> TYPE_BOT_SAVED
            is ChatMessage.BotSummaryCard -> TYPE_BOT_SUMMARY
            // BotActionButtons no longer used as inline cards — handled by sticky area
            is ChatMessage.BotActionButtons -> TYPE_BOT_TEXT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER_TEXT -> UserTextVH(inflater.inflate(R.layout.item_chat_user_text, parent, false))
            TYPE_USER_IMAGE -> UserImageVH(inflater.inflate(R.layout.item_chat_user_image, parent, false))
            TYPE_BOT_LOADING -> BotLoadingVH(inflater.inflate(R.layout.item_chat_loading, parent, false))
            TYPE_BOT_FOOD_CARD -> BotFoodCardVH(inflater.inflate(R.layout.item_chat_food_card, parent, false))
            TYPE_BOT_MEDICAL_CARD -> BotMedicalCardVH(inflater.inflate(R.layout.item_chat_medical_card, parent, false))
            TYPE_BOT_DOSE_RESULT -> BotDoseResultVH(inflater.inflate(R.layout.item_chat_dose_result, parent, false))
            TYPE_BOT_SUMMARY -> BotSummaryCardVH(inflater.inflate(R.layout.item_chat_summary_card, parent, false))
            else -> BotTextVH(inflater.inflate(R.layout.item_chat_bot_text, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val msg = getItem(position)) {
            is ChatMessage.BotText -> (holder as BotTextVH).bind(msg.text)
            is ChatMessage.UserText -> (holder as UserTextVH).bind(msg.text)
            is ChatMessage.UserImage -> (holder as UserImageVH).bind(msg.imagePath)
            is ChatMessage.BotLoading -> (holder as BotLoadingVH).bind(msg.text)
            is ChatMessage.BotFoodCard -> (holder as BotFoodCardVH).bind(msg)
            is ChatMessage.BotMedicalCard -> (holder as BotMedicalCardVH).bind(msg)
            is ChatMessage.BotDoseResult -> (holder as BotDoseResultVH).bind(msg)
            is ChatMessage.BotSummaryCard -> (holder as BotSummaryCardVH).bind(msg)
            is ChatMessage.BotSaved -> (holder as BotTextVH).bind(msg.text)
            is ChatMessage.BotActionButtons -> {
                // Legacy fallback — just show empty text. Buttons are in sticky area now.
                (holder as BotTextVH).bind("")
            }
        }
    }
}

class ChatDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem == newItem
}