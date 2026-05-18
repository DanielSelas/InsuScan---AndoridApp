package com.example.insuscan.home.helpers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.network.dto.InsulinPlanDto

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
            name.text = plan.name ?: "Unnamed"
            subtitle.text = formatSubtitle(plan)
            details.text = formatDetails(plan)
            radio.isChecked = selectedId != null && plan.id == selectedId

            itemView.setOnClickListener {
                onPlanSelected(plan.id ?: return@setOnClickListener)
            }
        }

        private fun formatSubtitle(plan: InsulinPlanDto): String {
            return when {
                plan.name?.lowercase()?.contains("sick") == true -> "Sick day values"
                plan.name?.lowercase()?.contains("workout") == true -> "Reduced carb ratio"
                plan.name?.lowercase()?.contains("stress") == true -> "Higher correction"
                else -> "Custom values"
            }
        }

        private fun formatDetails(plan: InsulinPlanDto): String {
            val icr = plan.icr?.let { "${it.toInt()}" } ?: "--"
            val isf = plan.isf?.let { "${it.toInt()}" } ?: "--"
            val tg = plan.targetGlucose?.toString() ?: "--"
            return "ICR $icr · ISF $isf · TG $tg"
        }
    }
}