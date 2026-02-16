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
import kotlinx.coroutines.launch

class EditMealBottomSheet(
    private val initialItems: List<FoodItem>,
    private val onSave: (List<FoodItem>) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var adapter: EditMealAdapter
    private var recyclerView: RecyclerView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_meal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rv_edit_food_items)
        val addButton = view.findViewById<Button>(R.id.btn_add_item)
        val saveButton = view.findViewById<Button>(R.id.btn_save_changes)

        // Create a defensive copy of the list
        val mutableItems = initialItems.toMutableList()

        adapter = EditMealAdapter(
            mutableItems,
            onItemRemoved = { position -> adapter.removeItem(position) },
            onCarbLookup = { position, name, grams -> lookupCarbsFromUsda(position, name, grams) }
        )

        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        recyclerView?.adapter = adapter

        addButton.setOnClickListener {
            adapter.addItem(FoodItem(name = "", carbsGrams = null, weightGrams = null))
            recyclerView?.smoothScrollToPosition(adapter.itemCount - 1)
        }

        saveButton.setOnClickListener {
            if (adapter.hasPendingCalc()) {
                // Warn user that some items have unsaved weight changes
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Unsaved weight changes")
                    .setMessage("You changed the grams for some items but didn't press Calc to update the carbs. Save anyway?")
                    .setPositiveButton("Save anyway") { _, _ ->
                        doSave()
                    }
                    .setNegativeButton("Go back", null)
                    .show()
            } else {
                doSave()
            }
        }
    }

    private fun doSave() {
        val updatedItems = adapter.getItems().filter { it.name.isNotBlank() }
        onSave(updatedItems)
        dismiss()
    }

    /**
     * Add items to the already-open sheet (called when LLM parses typed food items).
     */
    fun addItems(items: List<FoodItem>) {
        if (!::adapter.isInitialized) return
        items.forEach { item ->
            adapter.addItem(item)
            val position = adapter.itemCount - 1
            // If the item has a name and weight but no carbs, trigger lookup
            if (item.name.isNotBlank() && (item.weightGrams ?: 0f) > 0f && (item.carbsGrams == null || item.carbsGrams == 0f)) {
                lookupCarbsFromUsda(position, item.name, item.weightGrams ?: 100f)
            }
        }
        recyclerView?.smoothScrollToPosition(adapter.itemCount - 1)
    }

    private fun lookupCarbsFromUsda(position: Int, foodName: String, grams: Float) {
        // Show loading indicator
        adapter.setItemLoading(position, true)

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                val request = com.example.insuscan.network.dto.ChatParseRequestDto(
                    text = "$foodName ${grams.toInt()} grams",
                    state = "REVIEWING_FOOD"
                )

                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.insuscan.network.RetrofitClient.api.parseChatText(request)
                }

                // Hide loading indicator
                adapter.setItemLoading(position, false)

                if (response.isSuccessful) {
                    val carbs = response.body()?.items?.firstOrNull()?.estimatedCarbsGrams
                    if (carbs != null && carbs > 0f) {
                        adapter.updateItemCarbs(position, carbs)
                    } else {
                        // Food item was not recognized
                        android.widget.Toast.makeText(
                            requireContext(),
                            "\"$foodName\" was not recognized. Please enter carbs manually.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Could not look up \"$foodName\". Please enter carbs manually.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                adapter.setItemLoading(position, false)
                com.example.insuscan.utils.FileLogger.log("CHAT", "Carb lookup failed: ${e.message}")
                android.widget.Toast.makeText(
                    requireContext(),
                    "Lookup failed for \"$foodName\". Please enter carbs manually.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
