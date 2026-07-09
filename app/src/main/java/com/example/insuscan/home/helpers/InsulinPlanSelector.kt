package com.example.insuscan.home.helpers

import android.widget.LinearLayout
import android.widget.RadioButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.network.dto.InsulinPlanDto

/**
 * Manages insulin plan selection on the home screen: the default row plus the
 * list of custom plans. Exposes the currently selected plan, or null for default.
 */
class InsulinPlanSelector(
    private val defaultRow: LinearLayout,
    private val defaultRadio: RadioButton,
    private val recyclerView: RecyclerView
) {

    private var plans = listOf<InsulinPlanDto>()
    private var selectedPlanId: String? = DEFAULT_PLAN_ID

    private val adapter = InsulinPlanAdapter { planId ->
        selectPlan(planId)
    }

    init {
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
        recyclerView.adapter = adapter

        defaultRow.setOnClickListener { selectPlan(DEFAULT_PLAN_ID) }
    }

    fun loadPlans(serverPlans: List<InsulinPlanDto>?) {
        selectedPlanId = DEFAULT_PLAN_ID
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
        if (selectedPlanId == null || selectedPlanId == DEFAULT_PLAN_ID) return null
        return plans.find { it.id == selectedPlanId }
    }

    private fun selectPlan(planId: String) {
        selectedPlanId = planId
        defaultRadio.isChecked = (planId == DEFAULT_PLAN_ID)
        adapter.setSelectedId(if (planId == DEFAULT_PLAN_ID) null else planId)
    }

    companion object {
        private const val DEFAULT_PLAN_ID = "default"
    }
}