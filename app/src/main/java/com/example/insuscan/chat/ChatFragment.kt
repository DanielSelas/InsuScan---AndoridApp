package com.example.insuscan.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.insuscan.R

/**
 * Static placeholder screen for the upcoming AI chat assistant.
 * Renders the "coming soon" layout only and holds no chat logic yet.
 */
class ChatFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }
}