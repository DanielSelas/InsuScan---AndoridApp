package com.example.insuscan.chat.viewholders

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.chat.ChatMessage

class BotMedicalCardVH(view: View) : RecyclerView.ViewHolder(view) {
    private val icrValue: TextView = view.findViewById(R.id.tv_icr_value)
    private val isfValue: TextView = view.findViewById(R.id.tv_isf_value)
    private val targetValue: TextView = view.findViewById(R.id.tv_target_value)

    fun bind(msg: ChatMessage.BotMedicalCard) {
        icrValue.text = String.format("1:%.0f g/u", msg.icr)
        isfValue.text = String.format("%.0f", msg.isf)
        val units = msg.glucoseUnits ?: "mg/dL"
        targetValue.text = "${msg.targetGlucose} $units"
    }
}
