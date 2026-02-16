package com.example.insuscan.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.insuscan.R
import com.example.insuscan.meal.FoodItem
import com.example.insuscan.utils.InsulinCalculatorUtil
import java.io.File

// RecyclerView adapter for chat messages.
// Cards are display-only — all buttons are in the sticky area managed by Fragment.
class ChatAdapter(
    private val onActionButton: ((String) -> Unit)? = null
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatDiffCallback()) {

    companion object {
        private const val TYPE_BOT_TEXT = 0
        private const val TYPE_USER_TEXT = 1
        private const val TYPE_USER_IMAGE = 2
        private const val TYPE_BOT_LOADING = 3
        private const val TYPE_BOT_FOOD_CARD = 4
        private const val TYPE_BOT_MEDICAL_CARD = 5
        private const val TYPE_BOT_DOSE_RESULT = 6
        private const val TYPE_BOT_SAVED = 7
        private const val TYPE_BOT_SUMMARY = 8
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ChatMessage.BotText -> TYPE_BOT_TEXT
            is ChatMessage.UserText -> TYPE_USER_TEXT
            is ChatMessage.UserImage -> TYPE_USER_IMAGE
            is ChatMessage.BotLoading -> TYPE_BOT_LOADING
            is ChatMessage.BotFoodCard -> TYPE_BOT_FOOD_CARD
            is ChatMessage.BotMedicalCard -> TYPE_BOT_MEDICAL_CARD
            is ChatMessage.BotDoseResult -> TYPE_BOT_DOSE_RESULT
            is ChatMessage.BotSaved -> TYPE_BOT_SAVED
            is ChatMessage.BotSummaryCard -> TYPE_BOT_SUMMARY
            // BotActionButtons no longer used as inline cards — handled by sticky area
            is ChatMessage.BotActionButtons -> TYPE_BOT_TEXT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER_TEXT -> UserTextVH(inflater.inflate(R.layout.item_chat_user_text, parent, false))
            TYPE_USER_IMAGE -> UserImageVH(inflater.inflate(R.layout.item_chat_user_image, parent, false))
            TYPE_BOT_LOADING -> BotLoadingVH(inflater.inflate(R.layout.item_chat_loading, parent, false))
            TYPE_BOT_FOOD_CARD -> BotFoodCardVH(inflater.inflate(R.layout.item_chat_food_card, parent, false))
            TYPE_BOT_MEDICAL_CARD -> BotMedicalCardVH(inflater.inflate(R.layout.item_chat_medical_card, parent, false))
            TYPE_BOT_DOSE_RESULT -> BotDoseResultVH(inflater.inflate(R.layout.item_chat_dose_result, parent, false))
            TYPE_BOT_SUMMARY -> BotSummaryCardVH(inflater.inflate(R.layout.item_chat_summary_card, parent, false))
            else -> BotTextVH(inflater.inflate(R.layout.item_chat_bot_text, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val msg = getItem(position)) {
            is ChatMessage.BotText -> (holder as BotTextVH).bind(msg.text)
            is ChatMessage.UserText -> (holder as UserTextVH).bind(msg.text)
            is ChatMessage.UserImage -> (holder as UserImageVH).bind(msg.imagePath)
            is ChatMessage.BotLoading -> (holder as BotLoadingVH).bind(msg.text)
            is ChatMessage.BotFoodCard -> (holder as BotFoodCardVH).bind(msg)
            is ChatMessage.BotMedicalCard -> (holder as BotMedicalCardVH).bind(msg)
            is ChatMessage.BotDoseResult -> (holder as BotDoseResultVH).bind(msg)
            is ChatMessage.BotSummaryCard -> (holder as BotSummaryCardVH).bind(msg)
            is ChatMessage.BotSaved -> (holder as BotTextVH).bind(msg.text)
            is ChatMessage.BotActionButtons -> {
                // Legacy fallback — just show empty text. Buttons are in sticky area now.
                (holder as BotTextVH).bind("")
            }
        }
    }

    // --- ViewHolders ---

    inner class BotTextVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tv_bot_message)
        fun bind(text: String) { tv.text = text }
    }

    inner class UserTextVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tv_user_message)
        fun bind(text: String) { tv.text = text }
    }

    inner class UserImageVH(view: View) : RecyclerView.ViewHolder(view) {
        private val iv: ImageView = view.findViewById(R.id.iv_user_image)
        fun bind(imagePath: String) {
            Glide.with(iv.context).load(File(imagePath)).centerCrop().into(iv)
        }
    }

    inner class BotLoadingVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tv_loading_text)
        fun bind(text: String) { tv.text = text }
    }

    inner class BotFoodCardVH(view: View) : RecyclerView.ViewHolder(view) {
        private val container: LinearLayout = view.findViewById(R.id.layout_food_items)
        private val totalText: TextView = view.findViewById(R.id.tv_total_carbs)
        // No inline buttons — actions are in the sticky area

        fun bind(msg: ChatMessage.BotFoodCard) {
            // Cards are display-only, buttons are in sticky area

            container.removeAllViews()
            val ctx = itemView.context

            msg.foodItems.forEachIndexed { index, item ->
                val row = buildFoodRow(ctx, item, index == msg.foodItems.lastIndex)
                container.addView(row)
            }
            totalText.text = String.format("Total: %.0fg carbs", msg.totalCarbs)
        }

        private fun buildFoodRow(ctx: android.content.Context, item: FoodItem, isLast: Boolean): View {
            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val nameView = TextView(ctx).apply {
                text = item.name
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            container.addView(nameView)

            val carbs = item.carbsGrams ?: 0f
            val hasMissing = carbs == 0f
            val carbsView = TextView(ctx).apply {
                text = if (hasMissing) "? carbs" else String.format("%.1fg", carbs)
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, if (hasMissing) R.color.error else R.color.primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            container.addView(carbsView)

            val wrapper = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(container)
                if (!isLast) {
                    addView(View(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                        setBackgroundColor(ContextCompat.getColor(ctx, R.color.divider_light))
                    })
                }
            }
            return wrapper
        }
    }

    inner class BotMedicalCardVH(view: View) : RecyclerView.ViewHolder(view) {
        private val icrValue: TextView = view.findViewById(R.id.tv_icr_value)
        private val isfValue: TextView = view.findViewById(R.id.tv_isf_value)
        private val targetValue: TextView = view.findViewById(R.id.tv_target_value)
        // No inline buttons — actions are in the sticky area


        fun bind(msg: ChatMessage.BotMedicalCard) {

            icrValue.text = String.format("1:%.0f g/u", msg.icr)
            isfValue.text = String.format("%.0f", msg.isf)
            val units = msg.glucoseUnits ?: "mg/dL"
            targetValue.text = "${msg.targetGlucose} $units"
        }
    }

    inner class BotDoseResultVH(view: View) : RecyclerView.ViewHolder(view) {
        private val carbDose: TextView = view.findViewById(R.id.tv_dose_carb)
        private val correctionLayout: View = view.findViewById(R.id.layout_dose_correction)
        private val correctionDose: TextView = view.findViewById(R.id.tv_dose_correction)
        private val sickLayout: View = view.findViewById(R.id.layout_dose_sick)
        private val sickDose: TextView = view.findViewById(R.id.tv_dose_sick)
        private val stressLayout: View = view.findViewById(R.id.layout_dose_stress)
        private val stressDose: TextView = view.findViewById(R.id.tv_dose_stress)
        private val exerciseLayout: View = view.findViewById(R.id.layout_dose_exercise)
        private val exerciseDose: TextView = view.findViewById(R.id.tv_dose_exercise)
        private val iobLayout: View = view.findViewById(R.id.layout_dose_iob)
        private val iobDose: TextView = view.findViewById(R.id.tv_dose_iob)
        private val finalDose: TextView = view.findViewById(R.id.tv_dose_final)

        fun bind(msg: ChatMessage.BotDoseResult) {
            val r = msg.doseResult

            // Reset all optional rows
            correctionLayout.visibility = View.GONE
            sickLayout.visibility = View.GONE
            stressLayout.visibility = View.GONE
            exerciseLayout.visibility = View.GONE
            iobLayout.visibility = View.GONE

            carbDose.text = String.format("+%.2f u", r.carbDose)

            if (r.correctionDose != 0f) {
                correctionLayout.visibility = View.VISIBLE
                correctionDose.text = String.format("%+.2f u", r.correctionDose)
            }
            if (r.sickAdj != 0f) {
                sickLayout.visibility = View.VISIBLE
                sickDose.text = String.format("+%.2f u", r.sickAdj)
            }
            if (r.stressAdj != 0f) {
                stressLayout.visibility = View.VISIBLE
                stressDose.text = String.format("+%.2f u", r.stressAdj)
            }
            if (r.exerciseAdj != 0f) {
                exerciseLayout.visibility = View.VISIBLE
                exerciseDose.text = String.format("-%.2f u", r.exerciseAdj)
            }
            if (r.iob != 0f) {
                iobLayout.visibility = View.VISIBLE
                iobDose.text = String.format("-%.2f u", r.iob)
            }

            finalDose.text = String.format("%.2f u", r.roundedDose)
        }
    }

    inner class BotSummaryCardVH(view: View) : RecyclerView.ViewHolder(view) {
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
}

class ChatDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem == newItem
}