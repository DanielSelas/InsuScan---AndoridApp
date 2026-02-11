package com.example.insuscan.chat

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.utils.TopBarHelper
import java.io.File

// Main chat screen — RecyclerView + input bar + camera/gallery
class ChatFragment : Fragment(R.layout.fragment_chat) {

    private lateinit var viewModel: ChatViewModel
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var inputField: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var cameraButton: ImageButton
    private lateinit var svStickyActions: android.widget.HorizontalScrollView
    private lateinit var llStickyActions: android.widget.LinearLayout

    // Camera launcher — takes a photo and returns the bitmap
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val file = File(requireContext().cacheDir, "chat_photo_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            viewModel.onImageReceived(file.absolutePath)
        }
    }

    // Gallery picker
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            processGalleryUri(uri)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        findViews(view)
        setupTopBar(view)
        setupRecyclerView()
        setupInputListeners()
        observeMessages()
        observeStickyActions()
    }

    private fun findViews(view: View) {
        recyclerView = view.findViewById(R.id.rv_chat_messages)
        inputField = view.findViewById(R.id.et_chat_input)
        sendButton = view.findViewById(R.id.btn_send)
        cameraButton = view.findViewById(R.id.btn_camera)
        svStickyActions = view.findViewById(R.id.sv_sticky_actions)
        llStickyActions = view.findViewById(R.id.ll_sticky_actions)
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
            onFoodConfirm = { viewModel.onFoodConfirmed() },
            onFoodEdit = { viewModel.onFoodEdit() },
            onMedicalConfirm = { viewModel.onMedicalConfirmed() },
            onMedicalEdit = { viewModel.onMedicalEdit() },
            onActionButton = { actionId -> viewModel.onActionButton(actionId) },
            onSaveMeal = { viewModel.onSaveMeal() }
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
            showImageSourceChooser()
        }
    }

    private fun showImageSourceChooser() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Add Image")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> cameraLauncher.launch(null)
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun processGalleryUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) {
                viewModel.onUserSendText("(Failed to load image)")
                return
            }

            val file = File(requireContext().cacheDir, "chat_gallery_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            viewModel.onImageReceived(file.absolutePath)
        } catch (e: Exception) {
            viewModel.onUserSendText("(Error loading image: ${e.message})")
        }
    }

    private fun observeMessages() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            chatAdapter.submitList(messages) {
                if (messages.isNotEmpty()) {
                    recyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }

        // Observe image pick events from action buttons
        viewModel.imagePickEvent.observe(viewLifecycleOwner) { event ->
            when (event) {
                "camera" -> cameraLauncher.launch(null)
                "gallery" -> galleryLauncher.launch("image/*")
            }
        }

        // Observe edit event
        viewModel.editFoodEvent.observe(viewLifecycleOwner) { shouldEdit ->
            if (shouldEdit) {
                showEditMealBottomSheet()
            }
        }

        // Observe navigation
        viewModel.navigationEvent.observe(viewLifecycleOwner) { destination ->
            destination?.let {
                val navController = androidx.navigation.fragment.NavHostFragment.findNavController(this)
                when (it) {
                    "profile" -> navController.navigate(R.id.profileFragment)
                    "history" -> navController.navigate(R.id.historyFragment)
                }
            }
        }
    }

    private fun observeStickyActions() {
        viewModel.stickyActions.observe(viewLifecycleOwner) { actions ->
            if (actions.isNullOrEmpty()) {
                svStickyActions.visibility = View.GONE
            } else {
                svStickyActions.visibility = View.VISIBLE
                llStickyActions.removeAllViews()
                actions.forEach { action ->
                    val btn = com.google.android.material.button.MaterialButton(requireContext()).apply {
                        text = action.label
                        setOnClickListener { viewModel.onActionButton(action.actionId) }
                    }
                    val params = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.marginEnd = 16
                    btn.layoutParams = params
                    llStickyActions.addView(btn)
                }
            }
        }
    }

    private fun showEditMealBottomSheet() {
        val currentMeal = com.example.insuscan.meal.MealSessionManager.currentMeal ?: return
        val items = currentMeal.foodItems ?: emptyList()
        val sheet = EditMealBottomSheet(items) { updatedItems ->
            viewModel.updateMealItems(updatedItems)
        }
        sheet.show(parentFragmentManager, "EditMealBottomSheet")
    }
}
