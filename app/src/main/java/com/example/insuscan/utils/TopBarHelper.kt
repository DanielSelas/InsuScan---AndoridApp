package com.example.insuscan.utils

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.example.insuscan.R

/**
 * Configures the shared top-bar layout (title, optional subtitle, optional back button).
 *
 * All screens that embed `layout_top_bar` should set it up through this helper
 * to ensure a consistent appearance and behaviour.
 */
object TopBarHelper {

    /**
     * Populates the top-bar views inside [rootView].
     *
     * @param title     Main title text.
     * @param subtitle  Optional subtitle shown below the title; hidden when null.
     * @param onBack    Click handler for the back button; the button is hidden when null.
     * @param rightView Reserved for an optional right-side action view (currently unused).
     */
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

    /** Convenience variant that navigates back to the scan screen. */
    fun setupTopBarBackToScan(
        rootView: View,
        title: String,
        onBackToScan: () -> Unit
    ) {
        setupTopBar(rootView = rootView, title = title, onBack = onBackToScan)
    }

    /** Convenience variant that navigates back to the summary screen. */
    fun setupTopBarBackToSummary(
        rootView: View,
        title: String,
        onBackToSummary: () -> Unit
    ) {
        setupTopBar(rootView = rootView, title = title, onBack = onBackToSummary)
    }
}