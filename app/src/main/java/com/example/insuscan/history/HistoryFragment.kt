package com.example.insuscan.history

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.utils.TopBarHelper
import com.google.android.material.bottomnavigation.BottomNavigationView

class HistoryFragment : Fragment(R.layout.fragment_history) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var btnScanNext: Button
    private lateinit var adapter: MealHistoryAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTopBar(view)
        findViews(view)
        setupRecyclerView()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun setupTopBar(view: View) {
        // History doesn't need back button, title is in layout
        view.findViewById<View>(R.id.top_bar)?.visibility = View.GONE
    }

    private fun findViews(view: View) {
        recyclerView = view.findViewById(R.id.rv_meal_history)
        emptyState = view.findViewById(R.id.tv_empty_history)
        btnScanNext = view.findViewById(R.id.btn_scan_next)
    }

    private fun setupRecyclerView() {
        adapter = MealHistoryAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        btnScanNext.setOnClickListener {
            val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
            bottomNav.selectedItemId = R.id.scanFragment
        }
    }

    private fun loadHistory() {
        val history = MealSessionManager.getHistory()

        val emptyLayout = view?.findViewById<View>(R.id.layout_empty)

        if (history.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyLayout?.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyLayout?.visibility = View.GONE
            adapter.submitList(history)
        }
    }
}