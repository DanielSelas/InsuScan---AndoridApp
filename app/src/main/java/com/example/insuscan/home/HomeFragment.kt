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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val startScanButton = view.findViewById<Button>(R.id.btn_start_scan)
        val greetingText = view.findViewById<TextView>(R.id.tv_home_greeting)

        // Try to use stored name, fallback to "Daniel" if nothing is saved yet
        val storedName = UserProfileManager.getUserName(requireContext())
        val displayName = if (!storedName.isNullOrBlank()) {
            storedName
        } else {
            "Daniel"
        }

        greetingText.text = "Hello, $displayName"

        // Main entry point to start a new scan
        startScanButton.setOnClickListener {
            // TODO: Consider clearing current meal before start if flow requires it
            (activity as? MainActivity)?.selectScanTab()
        }
    }
}