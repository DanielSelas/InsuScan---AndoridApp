package com.example.insuscan.chat.viewholders

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R

class BotTextVH(view: View) : RecyclerView.ViewHolder(view) {
    private val tv: TextView = view.findViewById(R.id.tv_bot_message)
    fun bind(text: String) { tv.text = text }
}
