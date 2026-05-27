package com.example.insuscan.home.helpers

import android.content.Context
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.insuscan.R
import com.example.insuscan.home.GlucoseGaugeView
import com.example.insuscan.meal.Meal
import com.example.insuscan.profile.UserProfileManager

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

        tvMealsLogged.text = "$count meal${if (count == 1) "" else "s"} logged"
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

    fun showLoading() {
        tvMealsLogged.text = "Refreshing..."
        tvTotalCarbs.text = "--"
        tvTotalInsulin.text = "--"
        tvGlucoseValue.text = "--"
        tvGlucoseStatus.text = ""
    }

    fun showError(message: String) {
        tvMealsLogged.text = "Could not refresh"
        tvTotalCarbs.text = "--"
        tvTotalInsulin.text = "--"
        tvGlucoseValue.text = "--"
        tvGlucoseStatus.text = ""
    }

    private fun resolveGlucoseStatus(glucose: Int): Pair<String, Int> {
        val units = UserProfileManager.getGlucoseUnits(context)
        val isMMol = units.contains("mmol", ignoreCase = true)
        val valueInMgDl = if (isMMol) (glucose * 18) else glucose
        return when {
            valueInMgDl < 70 -> Pair("Low", R.color.status_critical)
            valueInMgDl > 180 -> Pair("High", R.color.status_warning)
            else -> Pair("In range", R.color.secondary)
        }
    }
}