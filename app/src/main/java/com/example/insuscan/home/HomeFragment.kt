package com.example.insuscan.home

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.insuscan.MainActivity
import com.example.insuscan.R


class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scanButton = view.findViewById<Button>(R.id.btn_start_scan)

        scanButton.setOnClickListener {
            (requireActivity() as MainActivity).selectScanTab()
        }
    }
}