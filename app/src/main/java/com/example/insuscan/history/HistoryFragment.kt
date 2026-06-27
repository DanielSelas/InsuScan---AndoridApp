package com.example.insuscan.history

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.appdata.AppDataStore
import com.example.insuscan.appdata.DataState
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
    private lateinit var btnClearFilter: View

    private val viewModel: HistoryViewModel by activityViewModels {
        HistoryViewModelFactory(requireContext().applicationContext)
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
        btnFilterContainer = view.findViewById(R.id.btn_filter_container)
        btnClearFilter = view.findViewById(R.id.btn_clear_filter)

        val tvAvgCarbs = view.findViewById<TextView>(R.id.tv_avg_carbs)
        val tvAvgDose = view.findViewById<TextView>(R.id.tv_avg_dose)
        val tvTotalMeals = view.findViewById<TextView>(R.id.tv_total_meals)
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
                viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    AppDataStore.mealAddedSignal.collect { adapter.refresh() }
                }
            }

            viewLifecycleOwner.lifecycleScope.launch {
                adapter.submitData(PagingData.empty())
            }

            val apiDateString = DateTimeHelper.formatDateForFilter(selection)
            Log.d("HistoryFilter", "Date picker selected: $selection ms -> API date: $apiDateString")

            val uiFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
            val displayDate = uiFormat.format(Date(selection))
            btnClearFilter.isVisible = true

            viewModel.setDateFilter(apiDateString)
        }

        datePicker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun clearFilter() {
        viewLifecycleOwner.lifecycleScope.launch {
            adapter.submitData(PagingData.empty())
        }

        btnClearFilter.isVisible = false

        viewModel.setDateFilter(null)
        Log.d("HistoryFilter", "Filter cleared")
    }

    override fun onResume() {
        super.onResume()
        adapter.refresh()
        viewModel.refreshLatestMeal()
    }

    private fun observeData() {
        val tvAvgCarbs = view?.findViewById<TextView>(R.id.tv_avg_carbs)
        val tvAvgDose = view?.findViewById<TextView>(R.id.tv_avg_dose)
        val tvTotalMeals = view?.findViewById<TextView>(R.id.tv_total_meals)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.historyFlow.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        // Refresh history when a new meal is saved
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDataStore.mealAddedSignal.collect {
                    adapter.refresh()
                    viewModel.refreshLatestMeal()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            adapter.loadStateFlow.collectLatest {
                val mealItems = adapter.snapshot().items
                    .filterIsInstance<com.example.insuscan.history.models.HistoryUiModel.MealItem>()
                if (mealItems.isNotEmpty()) {
                    val avgCarbs = mealItems.mapNotNull { it.meal.carbs }.average()
                    val avgDose = mealItems.mapNotNull { it.meal.insulinDose }.average()
                    if (!avgCarbs.isNaN()) tvAvgCarbs?.text = avgCarbs.toInt().toString()
                    if (!avgDose.isNaN()) tvAvgDose?.text = String.format("%.1f", avgDose)
                    tvTotalMeals?.text = mealItems.size.toString()
                }
            }
        }
    }

}