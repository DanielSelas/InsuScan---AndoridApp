package com.example.insuscan.profile

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.insuscan.R
import java.util.UUID

class InsulinPlanViewManager(
    private val context: Context,
    private val container: LinearLayout
) {

    private val plans = mutableListOf<InsulinPlan>()
    private val viewMap = mutableMapOf<String, View>()

    fun loadPlans(serverPlans: List<InsulinPlan>?) {
        plans.clear()
        container.removeAllViews()
        viewMap.clear()

        if (serverPlans.isNullOrEmpty()) {
            plans.addAll(createDefaultPlans())
        } else {
            plans.addAll(serverPlans)
        }

        plans.forEach { addPlanCard(it) }
    }

    private fun createDefaultPlans(): List<InsulinPlan> {
        return listOf(
            InsulinPlan(
                id = UUID.randomUUID().toString(),
                name = "Sick Day",
                isDefault = false
            ),
            InsulinPlan(
                id = UUID.randomUUID().toString(),
                name = "Workout",
                isDefault = false
            ),
            InsulinPlan(
                id = UUID.randomUUID().toString(),
                name = "Stress",
                isDefault = false
            )
        )
    }

    fun getPlans(): List<InsulinPlan> {
        return plans.map { plan ->
            val view = viewMap[plan.id] ?: return@map plan
            val icr = view.findViewById<EditText>(R.id.et_plan_icr).text.toString().toFloatOrNull()
            val isf = view.findViewById<EditText>(R.id.et_plan_isf).text.toString().toFloatOrNull()
            val target = view.findViewById<EditText>(R.id.et_plan_target).text.toString().toIntOrNull()
            plan.copy(icr = icr, isf = isf, targetGlucose = target)
        }
    }

    fun addNewPlan(name: String) {
        val plan = InsulinPlan(
            id = UUID.randomUUID().toString(),
            name = name,
            isDefault = false
        )
        plans.add(plan)
        addPlanCard(plan)
    }

    private fun addPlanCard(plan: InsulinPlan) {
        val view = LayoutInflater.from(context).inflate(R.layout.item_insulin_plan, container, false)

        val nameText = view.findViewById<TextView>(R.id.tv_plan_name)
        val expandArrow = view.findViewById<ImageView>(R.id.iv_expand_arrow)
        val deleteButton = view.findViewById<ImageView>(R.id.iv_delete_plan)
        val details = view.findViewById<LinearLayout>(R.id.plan_details)
        val header = view.findViewById<LinearLayout>(R.id.plan_header)
        val icrField = view.findViewById<EditText>(R.id.et_plan_icr)
        val isfField = view.findViewById<EditText>(R.id.et_plan_isf)
        val targetField = view.findViewById<EditText>(R.id.et_plan_target)

        nameText.text = plan.name

        if (!plan.isDefault) {
            deleteButton.visibility = View.VISIBLE
            deleteButton.setOnClickListener {
                plans.removeAll { it.id == plan.id }
                viewMap.remove(plan.id)
                container.removeView(view)
            }
        }

        plan.icr?.let { icrField.setText(it.toInt().toString()) }
        plan.isf?.let { isfField.setText(it.toInt().toString()) }
        plan.targetGlucose?.let { targetField.setText(it.toString()) }

        header.setOnClickListener {
            if (details.visibility == View.GONE) {
                details.visibility = View.VISIBLE
                expandArrow.setImageResource(android.R.drawable.arrow_up_float)
            } else {
                details.visibility = View.GONE
                expandArrow.setImageResource(android.R.drawable.arrow_down_float)
            }
        }

        viewMap[plan.id] = view
        container.addView(view)
    }
}