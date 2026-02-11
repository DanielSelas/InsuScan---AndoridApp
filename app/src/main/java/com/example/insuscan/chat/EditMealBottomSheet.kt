package com.example.insuscan.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.meal.FoodItem
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class EditMealBottomSheet(
    private val initialItems: List<FoodItem>,
    private val onSave: (List<FoodItem>) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var adapter: EditMealAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_meal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_edit_food_items)
        val addButton = view.findViewById<Button>(R.id.btn_add_item)
        val saveButton = view.findViewById<Button>(R.id.btn_save_changes)

        // Create a defensive copy of the list
        val mutableItems = initialItems.toMutableList()

        adapter = EditMealAdapter(mutableItems) { position ->
            adapter.removeItem(position)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        addButton.setOnClickListener {
            adapter.addItem(FoodItem(name = "", carbsGrams = null, weightGrams = null))
            recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
        }

        saveButton.setOnClickListener {
            val updatedItems = adapter.getItems().filter { it.name.isNotBlank() } // Filter empty names
            onSave(updatedItems)
            dismiss()
        }
    }
}
