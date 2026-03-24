package com.example.insuscan.chat.viewholders

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.chat.ChatMessage

class BotSummaryCardVH(view: View) : RecyclerView.ViewHolder(view) {
    private val foodListContainer: LinearLayout = view.findViewById(R.id.ll_summary_food_list)
    private val totalCarbs: TextView = view.findViewById(R.id.tv_summary_total_carbs)
    private val medicalText: TextView = view.findViewById(R.id.tv_summary_medical)
    private val glucoseText: TextView = view.findViewById(R.id.tv_summary_glucose)
    private val adjustmentsLayout: View = view.findViewById(R.id.ll_summary_adjustments)
    private val adjustmentsText: TextView = view.findViewById(R.id.tv_summary_adjustments)
    private val finalDose: TextView = view.findViewById(R.id.tv_summary_final_dose)

    fun bind(msg: ChatMessage.BotSummaryCard) {
        val ctx = itemView.context

        // Food items
        foodListContainer.removeAllViews()
        msg.foodItems.forEach { item ->
            val tv = TextView(ctx).apply {
                val weight = item.weightGrams?.let { "${it.toInt()}g" } ?: "?"
                val carbs = item.carbsGrams?.let { String.format("%.1fg carbs", it) } ?: "? carbs"
                text = "  • ${item.name}  —  $weight  |  $carbs"
                textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                setPadding(0, 2, 0, 2)
            }
            foodListContainer.addView(tv)
        }
        totalCarbs.text = String.format("Total: %.0fg carbs", msg.totalCarbs)

        // Medical settings
        medicalText.text = "ICR: 1:${msg.icr.toInt()} g/u  •  ISF: ${msg.isf.toInt()}  •  Target: ${msg.targetGlucose} ${msg.glucoseUnits}"

        // Glucose
        glucoseText.text = if (msg.glucoseLevel != null) {
            "📊 Glucose: ${msg.glucoseLevel} ${msg.glucoseUnits}"
        } else {
            "📊 Glucose: Skipped"
        }

        // Adjustments
        val adjustments = mutableListOf<String>()
        val activityLabel = when (msg.activityLevel) {
            "light" -> "🏃 Light exercise (-${msg.exercisePct}%)"
            "intense" -> "🏋️ Intense exercise (-${msg.exercisePct}%)"
            else -> null
        }
        if (activityLabel != null) adjustments.add(activityLabel)
        if (msg.isSick) adjustments.add("🤒 Sick (+${msg.sickPct}%)")
        if (msg.isStress) adjustments.add("😫 Stress (+${msg.stressPct}%)")

        if (adjustments.isNotEmpty()) {
            adjustmentsLayout.visibility = View.VISIBLE
            adjustmentsText.text = adjustments.joinToString("\n")
        } else {
            adjustmentsLayout.visibility = View.GONE
        }

        // Final dose
        finalDose.text = String.format("%.1f u", msg.doseResult.roundedDose)
    }
}
