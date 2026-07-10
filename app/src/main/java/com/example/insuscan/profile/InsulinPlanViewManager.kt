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
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher

/**
 * Builds and manages the editable insulin-plan cards in the profile screen:
 * loads plans (or defaults), tracks live edits, and reports changes via onPlansEdited.
 */
class InsulinPlanViewManager(
    private val context: Context,
    private val container: LinearLayout
) {

    var onPlansEdited: (() -> Unit)? = null

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
                name = context.getString(R.string.plan_name_sick_day),
                isDefault = false
            ),
            InsulinPlan(
                id = UUID.randomUUID().toString(),
                name = context.getString(R.string.plan_name_workout),
                isDefault = false
            ),
            InsulinPlan(
                id = UUID.randomUUID().toString(),
                name = context.getString(R.string.plan_name_stress),
                isDefault = false
            )
        )
    }

    /**
     * Returns the plans with ICR/ISF/target read from their current field values.
     */
    fun getPlans(): List<InsulinPlan> {
        return plans.map { plan ->
            val view = viewMap[plan.id] ?: return@map plan
            val icr = view.findViewById<EditText>(R.id.et_plan_icr).text.toString().toFloatOrNull()
            val isf = view.findViewById<EditText>(R.id.et_plan_isf).text.toString().toFloatOrNull()
            val target = view.findViewById<EditText>(R.id.et_plan_target).text.toString().toIntOrNull()
            plan.copy(icr = icr, isf = isf, targetGlucose = target)
        }
    }

    fun addNewPlan(name: String, icr: Float?, isf: Float?, target: Int?) {
        val plan = InsulinPlan(
            id = UUID.randomUUID().toString(),
            name = name,
            isDefault = false,
            icr = icr,
            isf = isf,
            targetGlucose = target
        )
        plans.add(plan)
        addPlanCard(plan)
        onPlansEdited?.invoke()
    }

    private fun addPlanCard(plan: InsulinPlan) {
        val view = LayoutInflater.from(context).inflate(R.layout.item_insulin_plan, container, false)

        val nameText = view.findViewById<TextView>(R.id.tv_plan_name)
        val summaryText = view.findViewById<TextView>(R.id.tv_plan_summary)
        val expandArrow = view.findViewById<TextView>(R.id.iv_expand_arrow)
        val deleteButton = view.findViewById<ImageView>(R.id.iv_delete_plan)
        val details = view.findViewById<LinearLayout>(R.id.plan_details)
        val header = view.findViewById<LinearLayout>(R.id.plan_header)
        val icrField = view.findViewById<EditText>(R.id.et_plan_icr)
        val isfField = view.findViewById<EditText>(R.id.et_plan_isf)
        val targetField = view.findViewById<EditText>(R.id.et_plan_target)

        nameText.text = plan.name

        fun updateSummary() {
            val icr = icrField.text.toString().toIntOrNull()
            val isf = isfField.text.toString().toIntOrNull()
            val tg = targetField.text.toString().toIntOrNull()
            if (icr != null || isf != null || tg != null) {
                summaryText.text = buildString {
                    if (icr != null) append(context.getString(R.string.plan_summary_icr, icr))
                    if (isf != null) append(context.getString(R.string.plan_summary_isf, isf))
                    if (tg != null) append(context.getString(R.string.plan_summary_tg, tg))
                }
                summaryText.visibility = View.VISIBLE
            }
        }

        plan.icr?.let { icrField.setText(it.toInt().toString()) }
        plan.isf?.let { isfField.setText(it.toInt().toString()) }
        plan.targetGlucose?.let { targetField.setText(it.toString()) }
        updateSummary()

        val planFieldsWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSummary()
                onPlansEdited?.invoke()
            }
        }
        icrField.addTextChangedListener(planFieldsWatcher)
        isfField.addTextChangedListener(planFieldsWatcher)
        targetField.addTextChangedListener(planFieldsWatcher)

        if (!plan.isDefault) {
            deleteButton.visibility = View.VISIBLE
            deleteButton.setOnClickListener {
                plans.removeAll { it.id == plan.id }
                viewMap.remove(plan.id)
                container.removeView(view)
                onPlansEdited?.invoke()
            }
        }

        header.setOnClickListener {
            if (details.visibility == View.GONE) {
                details.visibility = View.VISIBLE
                expandArrow.rotation = 90f
            } else {
                details.visibility = View.GONE
                expandArrow.rotation = 0f
                updateSummary()
            }
        }

        viewMap[plan.id] = view
        container.addView(view)

        if (plans.indexOf(plan) < plans.size - 1) {
            val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                    marginStart = 16
                }
                setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.divider))
            }
            container.addView(divider)
        }
    }

}