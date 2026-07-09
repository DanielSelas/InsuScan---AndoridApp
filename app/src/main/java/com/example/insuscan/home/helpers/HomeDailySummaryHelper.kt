package com.example.insuscan.home.helpers

import android.content.Context
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.insuscan.R
import com.example.insuscan.home.GlucoseGaugeView
import com.example.insuscan.meal.Meal
import com.example.insuscan.utils.GlucoseThresholds

/**
 * Renders the home daily summary card: meal count, total carbs, total insulin,
 * and the latest glucose value with its status color on the gauge.
 */
class HomeDailySummaryHelper(
    private val context: Context,
    private val tvMealsLogged: TextView,
    private val tvTotalCarbs: TextView,
    private val tvTotalInsulin: TextView,
    private val tvGlucoseValue: TextView,
    private val tvGlucoseStatus: TextView,
    private val gaugeGlucose: GlucoseGaugeView
) {

    fun displaySummary(meals: List<Meal>) {
        val count = meals.size
        val totalCarbs = meals.sumOf { it.carbs.toDouble() }.toFloat()
        val totalInsulin = meals.sumOf { (it.insulinDose ?: it.recommendedDose ?: 0f).toDouble() }.toFloat()
        val latestGlucose = meals.maxByOrNull { it.timestamp }?.glucoseLevel

        tvMealsLogged.text = context.resources.getQuantityString(R.plurals.meals_logged, count, count)
        tvTotalCarbs.text = "${totalCarbs.toInt()}"
        tvTotalInsulin.text = String.format("%.1f", totalInsulin)

        if (latestGlucose != null) {
            tvGlucoseValue.text = "$latestGlucose"
            gaugeGlucose.glucoseValue = latestGlucose
            val (statusText, colorRes) = resolveGlucoseStatus(latestGlucose)
            tvGlucoseStatus.text = statusText
            tvGlucoseStatus.setTextColor(ContextCompat.getColor(context, colorRes))
        }
    }

    private fun resolveGlucoseStatus(glucose: Int): Pair<String, Int> {
        return when {
            glucose < GlucoseThresholds.LOW -> Pair(context.getString(R.string.glucose_status_low), R.color.status_critical)
            glucose > GlucoseThresholds.HIGH -> Pair(context.getString(R.string.glucose_status_high), R.color.status_warning)
            else -> Pair(context.getString(R.string.glucose_status_in_range), R.color.secondary)
        }
    }

    fun showLoading() {
        val placeholder = context.getString(R.string.value_placeholder)
        tvMealsLogged.text = context.getString(R.string.home_summary_refreshing)
        tvTotalCarbs.text = placeholder
        tvTotalInsulin.text = placeholder
        tvGlucoseValue.text = placeholder
        tvGlucoseStatus.text = ""
    }

    fun showError(message: String) {
        val placeholder = context.getString(R.string.value_placeholder)
        tvMealsLogged.text = message
        tvTotalCarbs.text = placeholder
        tvTotalInsulin.text = placeholder
        tvGlucoseValue.text = placeholder
        tvGlucoseStatus.text = ""
    }
}