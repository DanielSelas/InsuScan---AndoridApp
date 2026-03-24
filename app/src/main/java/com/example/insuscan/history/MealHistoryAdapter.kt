package com.example.insuscan.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.history.models.HistoryUiModel
import com.example.insuscan.history.viewholders.HeaderViewHolder
import com.example.insuscan.history.viewholders.MealViewHolder

class MealHistoryAdapter : PagingDataAdapter<HistoryUiModel, RecyclerView.ViewHolder>(UI_MODEL_COMPARATOR) {

    private val expandedPositions = mutableSetOf<String>()

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HistoryUiModel.Header -> R.layout.item_history_header
            is HistoryUiModel.MealItem -> R.layout.item_meal_history_expandable
            else -> throw UnsupportedOperationException("Unknown view")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == R.layout.item_history_header) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_meal_history_expandable, parent, false)
            MealViewHolder(view) { mealId, adapterPos ->
                if (expandedPositions.contains(mealId)) {
                    expandedPositions.remove(mealId)
                } else {
                    expandedPositions.add(mealId)
                }
                notifyItemChanged(adapterPos)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is MealViewHolder && item is HistoryUiModel.MealItem) {
            val isExpanded = expandedPositions.contains(item.meal.serverId ?: "")
            holder.bind(item, isExpanded)
        } else if (holder is HeaderViewHolder && item is HistoryUiModel.Header) {
            holder.bind(item.date)
        }
    }

    companion object {
        private val UI_MODEL_COMPARATOR = object : DiffUtil.ItemCallback<HistoryUiModel>() {
            override fun areItemsTheSame(oldItem: HistoryUiModel, newItem: HistoryUiModel): Boolean {
                return (oldItem is HistoryUiModel.MealItem && newItem is HistoryUiModel.MealItem &&
                        oldItem.meal.serverId == newItem.meal.serverId) ||
                        (oldItem is HistoryUiModel.Header && newItem is HistoryUiModel.Header &&
                                oldItem.date == newItem.date)
            }

            override fun areContentsTheSame(oldItem: HistoryUiModel, newItem: HistoryUiModel): Boolean {
                return oldItem == newItem
            }
        }
    }
}