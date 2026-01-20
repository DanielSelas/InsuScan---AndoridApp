package com.example.insuscan.manualentry

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import com.example.insuscan.R

class FoodSearchAdapter(
    context: Context,
    private val onItemSelected: (FoodSearchResult) -> Unit
) : ArrayAdapter<FoodSearchResult>(context, R.layout.item_food_search_result) {

    private var results: List<FoodSearchResult> = emptyList()

    fun updateResults(newResults: List<FoodSearchResult>) {
        results = newResults
        clear()
        addAll(newResults)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_food_search_result, parent, false)

        val item = getItem(position) ?: return view

        view.findViewById<TextView>(R.id.tv_food_name).text = item.name
        view.findViewById<TextView>(R.id.tv_food_carbs).text =
            "${item.carbsPer100g.toInt()}g carbs per 100g"

        return view
    }

    // Return the food name for display in AutoCompleteTextView
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                return FilterResults().apply {
                    values = results
                    count = results.size
                }
            }

            override fun publishResults(constraint: CharSequence?, filterResults: FilterResults?) {
                if (filterResults != null && filterResults.count > 0) {
                    notifyDataSetChanged()
                } else {
                    notifyDataSetInvalidated()
                }
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                return (resultValue as? FoodSearchResult)?.name ?: ""
            }
        }
    }
}