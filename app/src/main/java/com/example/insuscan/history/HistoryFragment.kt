package com.example.insuscan.history

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.utils.DateTimeHelper
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment(R.layout.fragment_history) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyStateText: TextView
    private lateinit var adapter: MealHistoryAdapter
    private lateinit var btnFilterContainer: View
    private lateinit var tvFilterStatus: TextView
    private lateinit var btnClearFilter: View

    private val viewModel: HistoryViewModel by viewModels {
        HistoryViewModelFactory(requireContext())
    }

    private lateinit var latestMealCardHelper: com.example.insuscan.history.helpers.LatestMealCardHelper

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.top_bar)?.visibility = View.GONE

        latestMealCardHelper = com.example.insuscan.history.helpers.LatestMealCardHelper(view)

        findViews(view)
        setupRecyclerView()
        setupListeners()
        observeData()
    }

    private fun findViews(view: View) {
        recyclerView = view.findViewById(R.id.rv_meal_history)
        emptyState = view.findViewById(R.id.layout_empty)
        emptyStateText = view.findViewById(R.id.tv_empty_history)
        btnFilterContainer = view.findViewById(R.id.btn_filter_container)
        tvFilterStatus = view.findViewById(R.id.tv_filter_status)
        btnClearFilter = view.findViewById(R.id.btn_clear_filter)
    }

    private fun setupRecyclerView() {
        adapter = MealHistoryAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        adapter.addLoadStateListener { loadState ->
            val isListEmpty = loadState.refresh is LoadState.NotLoading && adapter.itemCount == 0
            val isLoading = loadState.refresh is LoadState.Loading

            emptyState.isVisible = isListEmpty && !isLoading
            recyclerView.isVisible = !isListEmpty || isLoading

            if (isListEmpty) {
                if (btnClearFilter.isVisible) {
                    emptyStateText.text = "No meals found for this date."
                } else {
                    emptyStateText.text = "No meals yet.\nScan your first meal to get started!"
                }
            }
        }

        btnFilterContainer.setOnClickListener {
            showDatePicker()
        }

        btnClearFilter.setOnClickListener {
            clearFilter()
        }
    }

    private fun showDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            viewLifecycleOwner.lifecycleScope.launch {
                adapter.submitData(PagingData.empty())
            }

            val apiDateString = DateTimeHelper.formatDateForFilter(selection)
            Log.d("HistoryFilter", "Date picker selected: $selection ms -> API date: $apiDateString")

            val uiFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
            val displayDate = uiFormat.format(Date(selection))

            tvFilterStatus.text = "Date: $displayDate"
            btnClearFilter.isVisible = true

            viewModel.setDateFilter(apiDateString)
        }

        datePicker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun clearFilter() {
        viewLifecycleOwner.lifecycleScope.launch {
            adapter.submitData(PagingData.empty())
        }

        tvFilterStatus.text = "Filter by Date"
        btnClearFilter.isVisible = false

        viewModel.setDateFilter(null)
        Log.d("HistoryFilter", "Filter cleared")
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.historyFlow.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.latestMeal.collectLatest { meal ->
                updateLatestMealCard(meal)
            }
        }
    }

    private fun updateLatestMealCard(meal: com.example.insuscan.meal.Meal?) {
        latestMealCardHelper.updateCard(meal)
    }

    override fun onResume() {
        super.onResume()
        adapter.refresh()
        viewModel.refreshLatestMeal()
    }
}