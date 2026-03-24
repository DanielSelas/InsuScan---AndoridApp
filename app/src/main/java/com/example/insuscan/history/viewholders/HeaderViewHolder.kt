package com.example.insuscan.history.viewholders

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R

class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val dateText: TextView = view.findViewById(R.id.tv_header_date)
    fun bind(date: String) {
        dateText.text = date
    }
}
