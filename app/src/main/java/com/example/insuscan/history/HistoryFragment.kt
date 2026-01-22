package com.example.insuscan.history

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryFragment : Fragment(R.layout.fragment_history) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var btnScanNext: Button
    private lateinit var adapter: MealHistoryAdapter

    private val viewModel: HistoryViewModel by viewModels {
        HistoryViewModelFactory(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide standard top bar if present
        view.findViewById<View>(R.id.top_bar)?.visibility = View.GONE

        findViews(view)
        setupRecyclerView()
        setupListeners()
        observeData()
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
            requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
                .selectedItemId = R.id.scanFragment
        }

        // Handle empty states based on loading state
        adapter.addLoadStateListener { loadState ->
            val isListEmpty = loadState.refresh is LoadState.NotLoading && adapter.itemCount == 0
            emptyState.isVisible = isListEmpty
            recyclerView.isVisible = !isListEmpty

            // Optional: Show progress bar during initial load
            // progressBar.isVisible = loadState.source.refresh is LoadState.Loading
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.historyFlow.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }
    }
}