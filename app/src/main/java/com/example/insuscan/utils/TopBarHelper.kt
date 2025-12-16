package com.example.insuscan.utils

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.example.insuscan.R

object TopBarHelper {

    fun setupTopBar(
        rootView: View,
        title: String,
        onBack: () -> Unit
    ) {
        val backButton = rootView.findViewById<ImageButton>(R.id.btn_back_home)
        val topTitle = rootView.findViewById<TextView>(R.id.tv_top_title)

        topTitle.text = title
        backButton.setOnClickListener { onBack() }
    }

    fun setupTopBarBackToScan(
        rootView: View,
        title: String,
        onBackToScan: () -> Unit
    ) {
        setupTopBar(
            rootView = rootView,
            title = title,
            onBack = onBackToScan
        )
    }

    fun setupTopBarBackToSummary(
        rootView: View,
        title: String,
        onBackToSummary: () -> Unit
    ) {
        setupTopBar(
            rootView = rootView,
            title = title,
            onBack = onBackToSummary
        )
    }
}