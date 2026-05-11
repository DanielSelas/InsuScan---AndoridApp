package com.example.insuscan.utils

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.example.insuscan.R

object TopBarHelper {

    fun setupTopBar(
        rootView: View,
        title: String,
        subtitle: String? = null,
        onBack: (() -> Unit)? = null,
        rightView: View? = null
    ) {
        val backButton = rootView.findViewById<ImageButton>(R.id.btn_back_home)
        val titleView = rootView.findViewById<TextView>(R.id.tv_top_title)
        val subtitleView = rootView.findViewById<TextView>(R.id.tv_top_subtitle)

        titleView.text = title

        if (subtitle != null) {
            subtitleView.text = subtitle
            subtitleView.visibility = View.VISIBLE
        } else {
            subtitleView.visibility = View.GONE
        }

        if (onBack != null) {
            backButton.visibility = View.VISIBLE
            backButton.setOnClickListener { onBack() }
        } else {
            backButton.visibility = View.GONE
        }
    }

    fun setupTopBarBackToScan(
        rootView: View,
        title: String,
        onBackToScan: () -> Unit
    ) {
        setupTopBar(rootView = rootView, title = title, onBack = onBackToScan)
    }

    fun setupTopBarBackToSummary(
        rootView: View,
        title: String,
        onBackToSummary: () -> Unit
    ) {
        setupTopBar(rootView = rootView, title = title, onBack = onBackToSummary)
    }
}