package com.example.insuscan.history

import android.os.Bundle
import android.util.Log
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
import com.example.insuscan.utils.TopBarHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.lifecycle.lifecycleScope
import com.example.insuscan.network.dto.MealDto
import com.example.insuscan.network.repository.MealRepository
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.ToastHelper
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat as JavaSimpleDateFormat

class HistoryFragment : Fragment(R.layout.fragment_history) {
    companion object {
        private const val TAG = "HistoryFragment"
    }

    private lateinit var lastTitle: TextView
    private lateinit var lastDetails: TextView
    private lateinit var lastTime: TextView
    private lateinit var previousHeader: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var scanNextButton: Button

    private lateinit var lastSickInd: TextView
    private lateinit var lastStressInd: TextView
    private lateinit var lastGlucoseInd: TextView

    private val mealRepository = MealRepository()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Top bar (shared component)
        TopBarHelper.setupTopBar(
            rootView = view,
            title = "Meal history",
            onBack = {
                val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
                bottomNav.selectedItemId = R.id.homeFragment
            }
        )

        findViews(view)
        setupRecyclerView()
        initializeListeners()

        // Render local meals (if any) immediately, but always try to refresh from server.
        val localMeals = MealSessionManager.getHistory()
        val dateFormat = createDateFormat()

        if (localMeals.isEmpty()) {
            renderEmptyState()
            // TODO: Persist empty/non-empty state using local storage (Room/SharedPreferences) if history should survive app restarts
        } else {
            renderLastMeal(localMeals.first(), dateFormat)
            recyclerView.adapter = MealHistoryAdapter(buildPreviousMealItems(localMeals, dateFormat))
        }

        // Always load from server (this is the source of truth)
        loadMealsFromServer()
    }

    private fun findViews(view: View) {
        // Header & Navigation
        previousHeader = view.findViewById(R.id.tv_previous_meals_header)
        recyclerView = view.findViewById(R.id.rv_meal_history)
        scanNextButton = view.findViewById(R.id.btn_scan_next_meal)

        // Last Meal Card Views
        lastTitle = view.findViewById(R.id.tv_last_meal_title)
        lastDetails = view.findViewById(R.id.tv_last_meal_details)
        lastTime = view.findViewById(R.id.tv_last_meal_time)

        // Accuracy Indicators
        lastSickInd = view.findViewById(R.id.tv_last_sick_ind)
        lastStressInd = view.findViewById(R.id.tv_last_stress_ind)
        lastGlucoseInd = view.findViewById(R.id.tv_last_glucose_ind)
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

        // Professional status indicators for accuracy
        lastSickInd.visibility = if (lastMeal.wasSickMode) View.VISIBLE else View.GONE
        lastStressInd.visibility = if (lastMeal.wasStressMode) View.VISIBLE else View.GONE

        if (lastMeal.glucoseLevel != null) {
            lastGlucoseInd.visibility = View.VISIBLE
            lastGlucoseInd.text = "🩸 Glucose: ${lastMeal.glucoseLevel} mg/dL"
        } else {
            lastGlucoseInd.visibility = View.GONE
        }
    }

    private fun buildPreviousMealItems(
        allMeals: List<Meal>,
        dateFormat: SimpleDateFormat
    ): List<MealHistoryItem> {
        val previousMeals = if (allMeals.size > 1) allMeals.drop(1) else emptyList()

        return previousMeals.map { meal ->
            MealHistoryItem(
                title = meal.title,
                carbsText = buildCarbsText(meal.carbs),
                insulinText = buildInsulinText(meal.insulinDose),
                timeText = dateFormat.format(Date(meal.timestamp)),
                wasSickMode = meal.wasSickMode,
                wasStressMode = meal.wasStressMode,
                glucoseLevel = meal.glucoseLevel?.toString()
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

    private fun loadMealsFromServer() {
        val email = UserProfileManager.getUserEmail(requireContext())
        if (email.isNullOrBlank()) {
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val result = loadAllMealsForUser(email)
            result.onSuccess { meals ->
                if (meals.isNotEmpty()) {
                    renderMealsFromServer(meals)
                } else {
                    renderEmptyState()
                }
            }.onFailure { e ->
                Log.e(TAG, "Failed to load meals from server for email=$email", e)
                // Fallback to local data or show error
                ToastHelper.showShort(requireContext(), "Could not load from server: ${e.message}")
                val localMeals = MealSessionManager.getHistory()
                // ... render local meals ...
            }
        }
    }

    private suspend fun loadAllMealsForUser(email: String): Result<List<MealDto>> {
        val pageSize = 25
        val maxPages = 20 // safety cap (max 500 meals)

        val all = mutableListOf<MealDto>()
        var page = 0

        while (page < maxPages) {
            val result = mealRepository.getUserMeals(email = email, page = page, size = pageSize)
            if (result.isFailure) return result

            val items = result.getOrNull().orEmpty()
            if (items.isEmpty()) break

            all.addAll(items)

            // If server returned less than a full page, we're done.
            if (items.size < pageSize) break

            page++
        }

        return Result.success(all)
    }

    private fun renderMealsFromServer(meals: List<MealDto>) {
        val dateFormat = createDateFormat()

        val serverMealsAsLocal = meals.map { dto ->
            Meal(
                title = dto.foodItems?.firstOrNull()?.name ?: "Meal",
                carbs = dto.totalCarbs ?: 0f,
                insulinDose = dto.actualDose ?: dto.recommendedDose ?: dto.insulinCalculation?.recommendedDose,
                timestamp = parseServerTimestampToMillis(dto.scannedTimestamp) ?: System.currentTimeMillis(),
                serverId = dto.mealId?.id,

                // Accurate mapping from the updated DTO fields
                wasSickMode = dto.wasSickMode ?: false,
                wasStressMode = dto.wasStressMode ?: false,

                // Extracting glucose from the nested insulinCalculation object
                glucoseLevel = dto.insulinCalculation?.currentGlucose
            )
        }

        val last = serverMealsAsLocal.firstOrNull()
        if (last == null) {
            renderEmptyState()
            return
        }

        renderLastMeal(last, dateFormat)
        recyclerView.adapter = MealHistoryAdapter(buildPreviousMealItems(serverMealsAsLocal, dateFormat))
    }

    private fun parseServerTimestampToMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null

        // Spring Boot Date serialization is commonly ISO-8601 (e.g. 2026-01-18T12:34:56.789+00:00)
        // Try a small set of formats; if all fail, return null.
        val candidates = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )

        for (pattern in candidates) {
            try {
                val df = JavaSimpleDateFormat(pattern, Locale.US)
                df.isLenient = true
                val parsed = df.parse(value)
                if (parsed != null) return parsed.time
            } catch (_: ParseException) {
                // try next
            } catch (_: IllegalArgumentException) {
                // try next
            }
        }
        return null
    }
}