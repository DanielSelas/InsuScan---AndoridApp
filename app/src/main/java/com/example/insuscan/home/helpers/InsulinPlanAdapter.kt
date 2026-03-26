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
        private val details: TextView = view.findViewById(R.id.tv_plan_details)

        fun bind(plan: InsulinPlanDto) {
            name.text = plan.name ?: "Unnamed"
            details.text = formatDetails(plan)
            radio.isChecked = plan.id == selectedId

            itemView.setOnClickListener {
                onPlanSelected(plan.id ?: return@setOnClickListener)
            }
        }

        private fun formatDetails(plan: InsulinPlanDto): String {
            val parts = mutableListOf<String>()
            plan.icr?.let { parts.add("ICR 1:${it.toInt()}") }
            plan.isf?.let { parts.add("ISF ${it.toInt()}") }
            plan.targetGlucose?.let { parts.add("TG $it") }
            return if (parts.isEmpty()) "not configured" else parts.joinToString(" · ")
        }
    }
}