package com.example.insuscan.chat.viewholders

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.chat.ChatMessage
import com.example.insuscan.meal.FoodItem

class BotFoodCardVH(view: View) : RecyclerView.ViewHolder(view) {
    private val container: LinearLayout = view.findViewById(R.id.layout_food_items)
    private val totalText: TextView = view.findViewById(R.id.tv_total_carbs)

    fun bind(msg: ChatMessage.BotFoodCard) {
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
