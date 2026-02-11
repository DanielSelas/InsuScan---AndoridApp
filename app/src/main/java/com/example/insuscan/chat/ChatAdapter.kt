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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.io.File

// RecyclerView adapter for all chat message types.
// Callbacks are wired from ChatFragment → ChatViewModel for button actions.
class ChatAdapter(
    private val onFoodConfirm: (() -> Unit)? = null,
    private val onFoodEdit: (() -> Unit)? = null,
    private val onMedicalConfirm: (() -> Unit)? = null,
    private val onMedicalEdit: (() -> Unit)? = null,
    private val onActionButton: ((String) -> Unit)? = null,
    private val onSaveMeal: (() -> Unit)? = null
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatDiffCallback()) {

    companion object {
        private const val TYPE_BOT_TEXT = 0
        private const val TYPE_USER_TEXT = 1
        private const val TYPE_USER_IMAGE = 2
        private const val TYPE_BOT_LOADING = 3
        private const val TYPE_BOT_FOOD_CARD = 4
        private const val TYPE_BOT_MEDICAL_CARD = 5
        private const val TYPE_BOT_ACTION_BUTTONS = 6
        private const val TYPE_BOT_DOSE_RESULT = 7
        private const val TYPE_BOT_SAVED = 8
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ChatMessage.BotText -> TYPE_BOT_TEXT
            is ChatMessage.UserText -> TYPE_USER_TEXT
            is ChatMessage.UserImage -> TYPE_USER_IMAGE
            is ChatMessage.BotLoading -> TYPE_BOT_LOADING
            is ChatMessage.BotFoodCard -> TYPE_BOT_FOOD_CARD
            is ChatMessage.BotMedicalCard -> TYPE_BOT_MEDICAL_CARD
            is ChatMessage.BotActionButtons -> TYPE_BOT_ACTION_BUTTONS
            is ChatMessage.BotDoseResult -> TYPE_BOT_DOSE_RESULT
            is ChatMessage.BotSaved -> TYPE_BOT_SAVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER_TEXT -> UserTextViewHolder(inflater.inflate(R.layout.item_chat_user_text, parent, false))
            TYPE_USER_IMAGE -> UserImageViewHolder(inflater.inflate(R.layout.item_chat_user_image, parent, false))
            TYPE_BOT_LOADING -> BotLoadingViewHolder(inflater.inflate(R.layout.item_chat_loading, parent, false))
            TYPE_BOT_FOOD_CARD -> BotFoodCardViewHolder(inflater.inflate(R.layout.item_chat_food_card, parent, false))
            TYPE_BOT_MEDICAL_CARD -> BotMedicalCardViewHolder(inflater.inflate(R.layout.item_chat_medical_card, parent, false))
            TYPE_BOT_ACTION_BUTTONS -> BotActionButtonsViewHolder(inflater.inflate(R.layout.item_chat_buttons, parent, false))
            TYPE_BOT_DOSE_RESULT -> BotDoseResultViewHolder(inflater.inflate(R.layout.item_chat_dose_result, parent, false))
            TYPE_BOT_SAVED -> BotTextViewHolder(inflater.inflate(R.layout.item_chat_bot_text, parent, false)) // reuse bot text layout
            else -> BotTextViewHolder(inflater.inflate(R.layout.item_chat_bot_text, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val msg = getItem(position)) {
            is ChatMessage.BotText -> (holder as BotTextViewHolder).bind(msg.text)
            is ChatMessage.UserText -> (holder as UserTextViewHolder).bind(msg)
            is ChatMessage.UserImage -> (holder as UserImageViewHolder).bind(msg)
            is ChatMessage.BotLoading -> (holder as BotLoadingViewHolder).bind(msg)
            is ChatMessage.BotFoodCard -> (holder as BotFoodCardViewHolder).bind(msg)
            is ChatMessage.BotMedicalCard -> (holder as BotMedicalCardViewHolder).bind(msg)
            is ChatMessage.BotActionButtons -> (holder as BotActionButtonsViewHolder).bind(msg)
            is ChatMessage.BotDoseResult -> (holder as BotDoseResultViewHolder).bind(msg)
            is ChatMessage.BotSaved -> (holder as BotTextViewHolder).bind(msg.text)
        }
    }

    // --- ViewHolders ---

    inner class BotTextViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.tv_bot_message)
        fun bind(text: String) {
            messageText.text = text
        }
    }

    inner class UserTextViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.tv_user_message)
        fun bind(msg: ChatMessage.UserText) {
            messageText.text = msg.text
        }
    }

    inner class UserImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView: ImageView = view.findViewById(R.id.iv_user_image)
        fun bind(msg: ChatMessage.UserImage) {
            Glide.with(imageView.context)
                .load(File(msg.imagePath))
                .centerCrop()
                .into(imageView)
        }
    }

    inner class BotLoadingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val loadingText: TextView = view.findViewById(R.id.tv_loading_text)
        fun bind(msg: ChatMessage.BotLoading) {
            loadingText.text = msg.text
        }
    }

    inner class BotFoodCardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val foodItemsLayout: LinearLayout = view.findViewById(R.id.layout_food_items)
        private val totalCarbsText: TextView = view.findViewById(R.id.tv_total_carbs)
        fun bind(msg: ChatMessage.BotFoodCard) {
            foodItemsLayout.removeAllViews()
            val ctx = itemView.context

            msg.foodItems.forEachIndexed { index, item ->
                val row = createFoodItemRow(ctx, item, index == msg.foodItems.lastIndex)
                foodItemsLayout.addView(row)
            }

            totalCarbsText.text = String.format("Total: %.1f g carbs", msg.totalCarbs)
        }

        private fun createFoodItemRow(ctx: android.content.Context, item: FoodItem, isLast: Boolean): View {
            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 6, 0, 6)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val nameStr = item.nameHebrew ?: item.name
            val quantityStr = if (item.quantity != null && item.quantity > 0f) {
                // simple float formatting
                val q = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                " ($q ${item.quantityUnit ?: ""})"
            } else ""

            val displayName = nameStr + quantityStr
            val carbs = item.carbsGrams ?: 0f
            val weight = item.weightGrams?.toInt()
            val hasMissing = carbs == 0f

            val nameView = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = if (hasMissing) "⚠️ $displayName" else displayName
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, if (hasMissing) R.color.error else R.color.text_primary))
            }
            row.addView(nameView)

            if (weight != null && weight > 0) {
                val weightView = TextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = "${weight}g"
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                    setPadding(16, 0, 16, 0)
                }
                row.addView(weightView)
            }

            val carbsView = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = if (hasMissing) "? carbs" else String.format("%.1f g carbs", carbs)
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, if (hasMissing) R.color.error else R.color.primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            row.addView(carbsView)

            container.addView(row)

            if (!isLast) {
                val divider = View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(ContextCompat.getColor(ctx, R.color.divider_light))
                }
                container.addView(divider)
            }

            return container
        }
    }

    inner class BotMedicalCardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icrValue: TextView = view.findViewById(R.id.tv_icr_value)
        private val isfValue: TextView = view.findViewById(R.id.tv_isf_value)
        private val targetValue: TextView = view.findViewById(R.id.tv_target_value)
        fun bind(msg: ChatMessage.BotMedicalCard) {
            icrValue.text = String.format("1:%.0f g/u", msg.icr)
            isfValue.text = String.format("%.0f", msg.isf)
            val units = msg.glucoseUnits ?: "mg/dL"
            targetValue.text = "${msg.targetGlucose} $units"
        }
    }

    inner class BotActionButtonsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val chipGroup: ChipGroup = view.findViewById(R.id.chip_group_actions)

        fun bind(msg: ChatMessage.BotActionButtons) {
            chipGroup.removeAllViews()
            val ctx = itemView.context

            msg.buttons.forEach { button ->
                val chip = Chip(ctx).apply {
                    text = button.label
                    isClickable = true
                    isCheckable = false
                    setOnClickListener { onActionButton?.invoke(button.actionId) }
                }
                chipGroup.addView(chip)
            }
        }
    }

    inner class BotDoseResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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

            // Reset visibility to avoid recycled view issues
            correctionLayout.visibility = View.GONE
            sickLayout.visibility = View.GONE
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
}

// DiffUtil for efficient list updates
class ChatDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem == newItem
    }
}
