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
    private lateinit var adapter: MealHistoryAdapter
    private lateinit var btnFilterContainer: View
    private lateinit var tvFilterStatus: TextView
    private lateinit var btnClearFilter: View


    private val viewModel: HistoryViewModel by viewModels {
        HistoryUiModel.HistoryViewModelFactory(requireContext())
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
        tvFilterStatus = view.findViewById(R.id.tv_filter_status)
        btnClearFilter = view.findViewById(R.id.btn_clear_filter)
    }

    private fun setupRecyclerView() {
        adapter = MealHistoryAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
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

            val apiDateString = DateTimeHelper.formatDateForFilter(selection)
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

    private var isLatestMealExpanded = false
    private var currentLatestMeal: com.example.insuscan.meal.Meal? = null

    private fun updateLatestMealCard(meal: com.example.insuscan.meal.Meal?) {
        val cardLatest = view?.findViewById<androidx.cardview.widget.CardView>(R.id.card_latest_meal)
        val tvPreviousLabel = view?.findViewById<android.widget.TextView>(R.id.tv_previous_label)
        
        currentLatestMeal = meal

        if (meal == null) {
            cardLatest?.visibility = View.GONE
            tvPreviousLabel?.visibility = View.GONE
            return
        }

        cardLatest?.visibility = View.VISIBLE
        tvPreviousLabel?.visibility = View.VISIBLE

        val header = view?.findViewById<View>(R.id.layout_latest_header)
        val expanded = view?.findViewById<View>(R.id.layout_latest_expanded)
        
        val tvTitle = view?.findViewById<TextView>(R.id.tv_latest_title)
        val tvSubtitle = view?.findViewById<TextView>(R.id.tv_latest_subtitle)
        val tvCarbs = view?.findViewById<TextView>(R.id.tv_latest_carbs)
        val tvTime = view?.findViewById<TextView>(R.id.tv_latest_time)
        val tvDoseBadge = view?.findViewById<TextView>(R.id.tv_latest_dose_badge)
        val tvIndicatorSick = view?.findViewById<TextView>(R.id.tv_latest_indicator_sick)
        val tvIndicatorStress = view?.findViewById<TextView>(R.id.tv_latest_indicator_stress)

        val uiModel = HistoryUiModel.MealItem(meal)

        // Header Binding
        tvTitle?.text = uiModel.displayTitle
        tvSubtitle?.text = uiModel.displaySubtitle
        tvCarbs?.text = "${meal.carbs.toInt()}g"
        tvTime?.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(meal.timestamp))
        
        tvIndicatorSick?.isVisible = meal.wasSickMode
        tvIndicatorStress?.isVisible = meal.wasStressMode
        
        tvDoseBadge?.isVisible = meal.insulinDose != null || meal.recommendedDose != null
        tvDoseBadge?.text = uiModel.totalDoseValue

        // Expanded Section Binding
        expanded?.isVisible = isLatestMealExpanded
        if (isLatestMealExpanded) {
            // Context Row
            val layoutGlucose = view?.findViewById<View>(R.id.layout_latest_context_glucose)
            val tvGlucose = view?.findViewById<TextView>(R.id.tv_latest_context_glucose)
            val layoutActivity = view?.findViewById<View>(R.id.layout_latest_context_activity)
            val tvActivity = view?.findViewById<TextView>(R.id.tv_latest_context_activity)
            val layoutModes = view?.findViewById<View>(R.id.layout_latest_context_modes)
            val tvStatusSick = view?.findViewById<TextView>(R.id.tv_latest_status_sick)
            val tvStatusStress = view?.findViewById<TextView>(R.id.tv_latest_status_stress)

            layoutGlucose?.isVisible = uiModel.isGlucoseVisible
            tvGlucose?.text = uiModel.glucoseText
            layoutActivity?.isVisible = uiModel.isActivityVisible
            tvActivity?.text = uiModel.activityText
            
            val hasModes = meal.wasSickMode || meal.wasStressMode
            layoutModes?.isVisible = hasModes
            tvStatusSick?.isVisible = meal.wasSickMode
            tvStatusStress?.isVisible = meal.wasStressMode

            // Food List
            val tvFoodList = view?.findViewById<TextView>(R.id.tv_latest_food_list)
            tvFoodList?.text = uiModel.receiptFoodList

            // Receipt
            val tvReceiptCarbLabel = view?.findViewById<TextView>(R.id.tv_latest_receipt_carb_label)
            val tvReceiptCarbValue = view?.findViewById<TextView>(R.id.tv_latest_receipt_carb_value)
            val rowCorrection = view?.findViewById<View>(R.id.row_latest_receipt_correction)
            val tvCorrectionValue = view?.findViewById<TextView>(R.id.tv_latest_receipt_correction_value)
            val rowExercise = view?.findViewById<View>(R.id.row_latest_receipt_exercise)
            val tvExerciseValue = view?.findViewById<TextView>(R.id.tv_latest_receipt_exercise_value)
            val rowSick = view?.findViewById<View>(R.id.row_latest_receipt_sick)
            val tvSickValue = view?.findViewById<TextView>(R.id.tv_latest_receipt_sick_value)
            val rowStress = view?.findViewById<View>(R.id.row_latest_receipt_stress)
            val tvStressValue = view?.findViewById<TextView>(R.id.tv_latest_receipt_stress_value)
            val tvReceiptTotal = view?.findViewById<TextView>(R.id.tv_latest_receipt_total)

            tvReceiptCarbLabel?.text = uiModel.carbDoseLabel
            tvReceiptCarbValue?.text = uiModel.carbDoseValue
            
            rowCorrection?.isVisible = uiModel.isCorrectionVisible
            tvCorrectionValue?.text = uiModel.correctionDoseValue
            
            rowExercise?.isVisible = uiModel.isExerciseVisible
            tvExerciseValue?.text = uiModel.exerciseDoseValue
            
            rowSick?.isVisible = uiModel.isSickVisible
            tvSickValue?.text = uiModel.sickDoseValue
            
            rowStress?.isVisible = uiModel.isStressVisible
            tvStressValue?.text = uiModel.stressDoseValue
            
            tvReceiptTotal?.text = uiModel.totalDoseValue
        }

        header?.setOnClickListener {
            isLatestMealExpanded = !isLatestMealExpanded
            updateLatestMealCard(currentLatestMeal)
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.refresh()
        viewModel.refreshLatestMeal()
    }
}