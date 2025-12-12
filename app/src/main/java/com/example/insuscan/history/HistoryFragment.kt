package com.example.insuscan.history

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment(R.layout.fragment_history) {
    private lateinit var lastTitle: TextView
    private lateinit var lastDetails: TextView
    private lateinit var lastTime: TextView
    private lateinit var previousHeader: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var scanNextButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)

        val allMeals = MealSessionManager.getHistory()
        val dateFormat = createDateFormat()

        setupRecyclerView()

        if (allMeals.isEmpty()) {
            renderEmptyState()
            // TODO: Persist empty/non-empty state using local storage (Room/SharedPreferences) if history should survive app restarts
            return
        }

        renderLastMeal(allMeals.first(), dateFormat)

        val itemsForAdapter = buildPreviousMealItems(allMeals, dateFormat)
        recyclerView.adapter = MealHistoryAdapter(itemsForAdapter)

        // TODO: Consider grouping meals by day or adding filters (e.g. by time of day or carbs range)

        initializeListeners()
    }

    private fun findViews(view: View) {
        lastTitle = view.findViewById(R.id.tv_last_meal_title)
        lastDetails = view.findViewById(R.id.tv_last_meal_details)
        lastTime = view.findViewById(R.id.tv_last_meal_time)
        previousHeader = view.findViewById(R.id.tv_previous_meals_header)
        recyclerView = view.findViewById(R.id.rv_meal_history)
        scanNextButton = view.findViewById(R.id.btn_scan_next_meal)
    }

    private fun createDateFormat(): SimpleDateFormat {
        return SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)
    }

    private fun renderEmptyState() {
        lastTitle.text = "No meals yet"
        lastDetails.text = "Your meals and insulin doses will appear here."
        lastTime.text = ""

        previousHeader.text = "Previous meals"
        recyclerView.adapter = MealHistoryAdapter(emptyList())
    }

    private fun renderLastMeal(lastMeal: Meal, dateFormat: SimpleDateFormat) {
        val lastCarbsText = buildCarbsText(lastMeal.carbs)
        val lastInsulinText = buildInsulinText(lastMeal.insulinDose)

        lastTitle.text = lastMeal.title
        lastDetails.text = "$lastCarbsText   |   $lastInsulinText"
        lastTime.text = "Time: ${dateFormat.format(Date(lastMeal.timestamp))}"
    }

    private fun buildPreviousMealItems(
        allMeals: List<Meal>,
        dateFormat: SimpleDateFormat
    ): List<MealHistoryItem> {
        val previousMeals = if (allMeals.size > 1) allMeals.drop(1) else emptyList()

        return previousMeals.map { meal ->
            val carbsText = buildCarbsText(meal.carbs)
            val insulinText = buildInsulinText(meal.insulinDose)
            val timeText = dateFormat.format(Date(meal.timestamp))

            MealHistoryItem(
                title = meal.title,
                carbsText = carbsText,
                insulinText = insulinText,
                timeText = timeText
            )
        }
    }

    private fun buildCarbsText(carbs: Float): String {
        return "Carbs: ${carbs.toInt()} g"
    }

    private fun buildInsulinText(insulinDose: Float?): String {
        return if (insulinDose != null) {
            "Insulin: ${String.format("%.1f", insulinDose)} units"
        } else {
            "Insulin: -"
        }
    }

    private fun initializeListeners() {
        scanNextButton.setOnClickListener {
            val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
            bottomNav.selectedItemId = R.id.scanFragment
        }
    }
}