package com.example.insuscan.home.helpers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.network.dto.InsulinPlanDto

/**
 * RecyclerView adapter for the custom insulin plans on the home screen.
 * Renders each plan's name, a subtitle derived from its name, and its ICR/ISF/TG values.
 */
class InsulinPlanAdapter(
    private val onPlanSelected: (String) -> Unit
) : RecyclerView.Adapter<InsulinPlanAdapter.PlanViewHolder>() {

    private var plans = listOf<InsulinPlanDto>()
    private var selectedId: String? = null

    fun submitList(newPlans: List<InsulinPlanDto>, currentSelectedId: String?) {
        plans = newPlans
        selectedId = currentSelectedId
        notifyDataSetChanged()
    }

    fun setSelectedId(id: String?) {
        selectedId = id
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_plan, parent, false)
        return PlanViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        holder.bind(plans[position])
    }

    override fun getItemCount() = plans.size

    inner class PlanViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val radio: RadioButton = view.findViewById(R.id.rb_plan)
        private val name: TextView = view.findViewById(R.id.tv_plan_name)
        private val subtitle: TextView = view.findViewById(R.id.tv_plan_subtitle)
        private val details: TextView = view.findViewById(R.id.tv_plan_details)

        fun bind(plan: InsulinPlanDto) {
            name.text = plan.name ?: itemView.context.getString(R.string.plan_name_unnamed)
            subtitle.text = formatSubtitle(plan)
            details.text = formatDetails(plan)
            radio.isChecked = selectedId != null && plan.id == selectedId

            itemView.setOnClickListener {
                onPlanSelected(plan.id ?: return@setOnClickListener)
            }
        }

        private fun formatSubtitle(plan: InsulinPlanDto): String {
            val context = itemView.context
            val name = plan.name?.lowercase()
            return when {
                name?.contains(PLAN_NAME_SICK) == true -> context.getString(R.string.plan_subtitle_sick)
                name?.contains(PLAN_NAME_WORKOUT) == true -> context.getString(R.string.plan_subtitle_workout)
                name?.contains(PLAN_NAME_STRESS) == true -> context.getString(R.string.plan_subtitle_stress)
                else -> context.getString(R.string.plan_subtitle_custom)
            }
        }

        private fun formatDetails(plan: InsulinPlanDto): String {
            val context = itemView.context
            val placeholder = context.getString(R.string.value_placeholder)
            val icr = plan.icr?.let { "${it.toInt()}" } ?: placeholder
            val isf = plan.isf?.let { "${it.toInt()}" } ?: placeholder
            val tg = plan.targetGlucose?.toString() ?: placeholder
            return context.getString(R.string.plan_details_format, icr, isf, tg)
        }
    }
    companion object {
        private const val PLAN_NAME_SICK = "sick"
        private const val PLAN_NAME_WORKOUT = "workout"
        private const val PLAN_NAME_STRESS = "stress"
    }
}