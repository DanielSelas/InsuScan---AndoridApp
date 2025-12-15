package com.example.insuscan.home

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.insuscan.MainActivity
import com.example.insuscan.R
import com.example.insuscan.profile.UserProfileManager

class HomeFragment : Fragment(R.layout.fragment_home) {
    private lateinit var startScanButton: Button
    private lateinit var greetingText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)
        renderGreeting()
        initializeListeners()
    }

    private fun findViews(view: View) {
        startScanButton = view.findViewById(R.id.btn_start_scan)
        greetingText = view.findViewById(R.id.tv_home_greeting)
    }

    private fun renderGreeting() {
        val displayName = getDisplayName()
        greetingText.text = "Hello, $displayName"
    }

    private fun getDisplayName(): String {
        val ctx = requireContext()

        // Try to use stored name, fallback to "Daniel" if nothing is saved yet
        val storedName = UserProfileManager.getUserName(ctx)
        return if (!storedName.isNullOrBlank()) storedName else DEFAULT_NAME
    }

    private fun initializeListeners() {
        // Main entry point to start a new scan
        startScanButton.setOnClickListener {
            // TODO: Consider clearing current meal before start if flow requires it
            (activity as? MainActivity)?.selectScanTab()
        }
    }

    companion object {
        private const val DEFAULT_NAME = "Daniel"
    }
}