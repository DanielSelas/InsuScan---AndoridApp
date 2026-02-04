package com.example.insuscan.history

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log

class HistoryFragment : Fragment(R.layout.fragment_history) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyStateText: TextView
    private lateinit var btnScanNext: Button
    private lateinit var adapter: MealHistoryAdapter
    private lateinit var btnFilterContainer: View
    private lateinit var tvFilterStatus: TextView
    private lateinit var btnClearFilter: View


    private val viewModel: HistoryViewModel by viewModels {
        HistoryViewModelFactory(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.top_bar)?.visibility = View.GONE

        findViews(view)
        setupRecyclerView()
        setupListeners()
        observeData()
    }

    private fun findViews(view: View) {
        recyclerView = view.findViewById(R.id.rv_meal_history)
        emptyState = view.findViewById(R.id.layout_empty)
        emptyStateText = view.findViewById(R.id.tv_empty_history)
        btnScanNext = view.findViewById(R.id.btn_scan_next)
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
        btnScanNext.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
                .selectedItemId = R.id.scanFragment
        }

        // Handle empty states based on loading state
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

            val apiDateString = DateTimeHelper.formatForApi(selection)
            Log.d("HistoryFilter", "Date picker selected: $selection ms -> API date: $apiDateString")

            val uiFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
            val displayDate = uiFormat.format(Date(selection))

            tvFilterStatus.text = "Date: $displayDate"
            btnClearFilter.isVisible = true

            viewModel.setDateFilter(apiDateString)
//            adapter.refresh()

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
        // Collect History List (Paging)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.historyFlow.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }
        
        // Collect Latest Meal (Top Card)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.latestMeal.collectLatest { meal ->
                updateLatestMealCard(meal)
            }
        }
    }

    private fun updateLatestMealCard(meal: com.example.insuscan.meal.Meal?) {
        val cardLatest = view?.findViewById<androidx.cardview.widget.CardView>(R.id.card_latest_meal)
        val tvPreviousLabel = view?.findViewById<android.widget.TextView>(R.id.tv_previous_label)
        
        if (meal == null) {
            cardLatest?.visibility = View.GONE
            tvPreviousLabel?.visibility = View.GONE
            return
        }

        cardLatest?.visibility = View.VISIBLE
        tvPreviousLabel?.visibility = View.VISIBLE

        val tvTitle = view?.findViewById<android.widget.TextView>(R.id.tv_latest_title)
        val tvTime = view?.findViewById<android.widget.TextView>(R.id.tv_latest_time)
        val tvDetails = view?.findViewById<android.widget.TextView>(R.id.tv_latest_details)

        tvTitle?.text = meal.title
        
        // Format Time HH:mm
        tvTime?.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(meal.timestamp))
        
        // Format Details
        val dose = if (meal.insulinDose != null) "${com.example.insuscan.utils.DoseFormatter.formatDose(meal.insulinDose)} units" else "No Dose"
        tvDetails?.text = "${meal.carbs.toInt()}g carbs | $dose"
        
        // Refresh paging list to ensure consistency? 
        // adapter.refresh() // Might cause loop if not careful, skip for now
    }

    override fun onResume() {
        super.onResume()
        adapter.refresh()
        viewModel.refreshLatestMeal()
    }
}