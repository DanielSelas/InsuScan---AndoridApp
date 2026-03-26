package com.example.insuscan.home.helpers

import android.widget.LinearLayout
import android.widget.RadioButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.network.dto.InsulinPlanDto

class InsulinPlanSelector(
    private val defaultRow: LinearLayout,
    private val defaultRadio: RadioButton,
    private val recyclerView: RecyclerView
) {

    private var plans = listOf<InsulinPlanDto>()
    private var selectedPlanId: String? = "default"

    private val adapter = InsulinPlanAdapter { planId ->
        selectPlan(planId)
    }

    init {
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
        recyclerView.adapter = adapter

        defaultRow.setOnClickListener { selectPlan("default") }
    }

    fun loadPlans(serverPlans: List<InsulinPlanDto>?) {
        selectedPlanId = "default"
        defaultRadio.isChecked = true

        if (!serverPlans.isNullOrEmpty()) {
            plans = serverPlans.filter { !it.isDefault }
            adapter.submitList(plans, null)
        } else {
            plans = emptyList()
            adapter.submitList(emptyList(), null)
        }
    }

    fun getSelectedPlan(): InsulinPlanDto? {
        if (selectedPlanId == null || selectedPlanId == "default") return null
        return plans.find { it.id == selectedPlanId }
    }

    fun resetToDefault() {
        selectPlan("default")
    }

    private fun selectPlan(planId: String) {
        selectedPlanId = planId
        defaultRadio.isChecked = (planId == "default")
        adapter.setSelectedId(if (planId == "default") null else planId)
    }
}