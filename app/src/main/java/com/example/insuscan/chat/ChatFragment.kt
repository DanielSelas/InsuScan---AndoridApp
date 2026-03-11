package com.example.insuscan.chat

import android.app.AlertDialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.scan.ReferenceChipsController
import com.example.insuscan.utils.TopBarHelper
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.io.File

// Main chat screen — RecyclerView + compact sticky buttons + input bar
class ChatFragment : Fragment(R.layout.fragment_chat) {
    private lateinit var viewModel: ChatViewModel
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var inputField: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var cameraButton: ImageButton
    private lateinit var stickyContainer: LinearLayout

    // Keep references to open sheets for item injection
    private var openEditMealSheet: EditMealBottomSheet? = null



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        findViews(view)
        setupTopBar(view)
        setupRecyclerView()
        setupInputListeners()
        observeMessages()
        observeStickyActions()
        observeEvents()
    }

    private fun findViews(view: View) {
        recyclerView = view.findViewById(R.id.rv_chat_messages)
        inputField = view.findViewById(R.id.et_chat_input)
        sendButton = view.findViewById(R.id.btn_send)
        cameraButton = view.findViewById(R.id.btn_camera)
        stickyContainer = view.findViewById(R.id.ll_sticky_container)
    }

    private fun setupTopBar(rootView: View) {
        TopBarHelper.setupTopBar(
            rootView = rootView,
            title = "Chat Assistant",
            onBack = {
                androidx.navigation.fragment.NavHostFragment.findNavController(this).navigateUp()
            }
        )
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            onActionButton = { actionId -> viewModel.onActionButton(actionId) }
        )
        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = chatAdapter
    }

    private fun setupInputListeners() {
        sendButton.setOnClickListener {
            val text = inputField.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.onUserSendText(text)
                inputField.text.clear()
            }
        }
        cameraButton.setOnClickListener {
            openScanDialog(false)
        }
    }

    private fun openScanDialog(openGalleryDirectly: Boolean) {
        val dialog = ChatScanDialogFragment.newInstance(openGalleryDirectly)
        dialog.onResult = { meal ->
            viewModel.onScanResultFromScanFragment(meal, meal.imagePath)
        }
        dialog.show(parentFragmentManager, "ChatScan")
    }

    private fun observeMessages() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            chatAdapter.submitList(messages) {
                if (messages.isNotEmpty()) {
                    recyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    /**
     * Renders sticky action buttons grouped by row.
     * Each row is a centered ChipGroup. The "save_meal" chip is larger.
     */
    private fun observeStickyActions() {
        viewModel.stickyActions.observe(viewLifecycleOwner) { actions ->
            stickyContainer.removeAllViews()

            if (actions.isNullOrEmpty()) {
                stickyContainer.visibility = View.GONE
                return@observe
            }

            stickyContainer.visibility = View.VISIBLE

            // Group buttons by row, preserving order
            val rows = actions.groupBy { it.row }
                .toSortedMap()

            rows.forEach { (rowIndex, rowButtons) ->
                val chipGroup = ChipGroup(requireContext()).apply {
                    isSingleLine = false
                    chipSpacingHorizontal = 10.dpToPx()
                    chipSpacingVertical = 6.dpToPx()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.CENTER_HORIZONTAL
                        topMargin = 4.dpToPx()
                        bottomMargin = 4.dpToPx()
                    }
                }

                rowButtons.forEach { button ->
                    val chip = Chip(requireContext()).apply {
                        text = button.label
                        isClickable = true
                        isCheckable = false
                        textSize = 14f
                        chipMinHeight = 48f
                        chipStartPadding = 16f
                        chipEndPadding = 16f

                        // Save button — larger and highlighted
                        if (button.actionId == "save_meal") {
                            textSize = 17f
                            chipMinHeight = 56f
                            chipStartPadding = 24f
                            chipEndPadding = 24f
                            setChipBackgroundColorResource(R.color.primary)
                            setTextColor(resources.getColor(R.color.white, null))
                        }

                        // Edit adjustments — bordered to stand out
                        if (button.actionId == "edit_adjustments") {
                            setChipStrokeWidth(2f)
                            setChipStrokeColorResource(R.color.primary)
                        }

                        // Active adjustments (✓) — tinted background
                        if (button.label.contains("✓")) {
                            setChipBackgroundColorResource(R.color.primary_light)
                        }

                        setOnClickListener { viewModel.onActionButton(button.actionId) }
                    }
                    chipGroup.addView(chip)
                }

                stickyContainer.addView(chipGroup)
            }
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun observeEvents() {
        viewModel.imagePickEvent.observe(viewLifecycleOwner) { event ->
            when (event) {
                "camera" -> openScanDialog(false)
                "gallery" -> openScanDialog(true)
            }
        }

        viewModel.editFoodEvent.observe(viewLifecycleOwner) { show ->
            if (show) showEditMealDialog()
        }

        viewModel.navigationEvent.observe(viewLifecycleOwner) { target ->
            when (target) {
                "profile" -> {
                    val navController = androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    navController.navigate(R.id.profileFragment)
                }
                "history" -> {
                    val navController = androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    navController.navigate(R.id.historyFragment)
                }
                "home" -> {
                    val navController = androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    navController.navigate(R.id.homeFragment)
                }
            }
        }
        viewModel.editMedicalEvent.observe(viewLifecycleOwner) { shouldEdit ->
            if (shouldEdit) {
                showEditMedicalBottomSheet()
            }
        }

        // Inject parsed food items into the open edit sheet
        viewModel.addFoodItemsEvent.observe(viewLifecycleOwner) { items ->
            if (items != null && items.isNotEmpty()) {
                openEditMealSheet?.addItems(items)
            }
        }

        // Show adjustment percentages edit dialog
        viewModel.editActivityEvent.observe(viewLifecycleOwner) { show ->
            if (show) showEditActivityDialog()
        }

        viewModel.editSickStressEvent.observe(viewLifecycleOwner) { show ->
            if (show) showEditSickStressDialog()
        }
    }


    private fun showEditMedicalBottomSheet() {
        val sheet = EditMedicalBottomSheet { icr, isf, target ->
            viewModel.updateMedicalSettings(icr, isf, target)
        }
        sheet.show(parentFragmentManager, "EditMedicalBottomSheet")
    }


    private fun showEditMealDialog() {
        val currentMeal = com.example.insuscan.meal.MealSessionManager.currentMeal ?: return
        val items = currentMeal.foodItems ?: emptyList()

        val sheet = EditMealBottomSheet(items) { updatedItems ->
            viewModel.updateMealItems(updatedItems)
            openEditMealSheet = null
        }
        openEditMealSheet = sheet
        sheet.show(parentFragmentManager, "EditMealBottomSheet")
    }


    private fun showEditActivityDialog() {
        val ctx = requireContext()
        val pm = UserProfileManager

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }

        fun addLabel(text: String) {
            layout.addView(android.widget.TextView(ctx).apply {
                this.text = text
                textSize = 14f
                setPadding(0, 8, 0, 4)
            })
        }

        addLabel("🏃 Light Exercise Reduction %")
        val lightInput = EditText(ctx).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(pm.getLightExerciseAdjustment(ctx).toString()) }
        layout.addView(lightInput)

        addLabel("🏋️ Intense Exercise Reduction %")
        val intenseInput = EditText(ctx).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(pm.getIntenseExerciseAdjustment(ctx).toString()) }
        layout.addView(intenseInput)

        AlertDialog.Builder(ctx)
            .setTitle("⚙️ Edit Activity Adjustments")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val light = lightInput.text.toString().toIntOrNull()
                val intense = intenseInput.text.toString().toIntOrNull()
                viewModel.updateAdjustmentPercentages(light = light, intense = intense)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditSickStressDialog() {
        val ctx = requireContext()
        val pm = UserProfileManager

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }

        fun addLabel(text: String) {
            layout.addView(android.widget.TextView(ctx).apply {
                this.text = text
                textSize = 14f
                setPadding(0, 8, 0, 4)
            })
        }

        addLabel("🤒 Sick Day Increase %")
        val sickInput = EditText(ctx).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(pm.getSickDayAdjustment(ctx).toString()) }
        layout.addView(sickInput)

        addLabel("😫 Stress Increase %")
        val stressInput = EditText(ctx).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(pm.getStressAdjustment(ctx).toString()) }
        layout.addView(stressInput)

        AlertDialog.Builder(ctx)
            .setTitle("⚙️ Edit Health Adjustments")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val sick = sickInput.text.toString().toIntOrNull()
                val stress = stressInput.text.toString().toIntOrNull()
                viewModel.updateAdjustmentPercentages(sick = sick, stress = stress)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    override fun onResume() {
        super.onResume()
    }
}