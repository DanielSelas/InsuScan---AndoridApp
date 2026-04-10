package com.example.insuscan.chat

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.chat.helpers.StickyActionsHelper
import com.example.insuscan.utils.TopBarHelper

class ChatFragment : Fragment(R.layout.fragment_chat) {

    private lateinit var viewModel: ChatViewModel
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var inputField: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var cameraButton: ImageButton
    private lateinit var stickyContainer: LinearLayout
    private lateinit var stickyDivider: View

    private lateinit var dialogHelper: ChatDialogHelper
    private lateinit var stickyActionsHelper: StickyActionsHelper

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        dialogHelper = ChatDialogHelper(this, viewModel)

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
        stickyDivider = view.findViewById(R.id.sticky_divider)
    }

    private fun setupTopBar(rootView: View) {
        TopBarHelper.setupTopBar(
            rootView = rootView,
            title = "Chat Assistant",
            onBack = { androidx.navigation.fragment.NavHostFragment.findNavController(this).navigateUp() }
        )
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            onActionButton = { actionId -> viewModel.onActionButton(actionId) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
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
        cameraButton.setOnClickListener { openScanDialog(false) }
    }

    private fun openScanDialog(openGalleryDirectly: Boolean) {
        val dialog = ChatScanDialogFragment.newInstance(openGalleryDirectly)
        dialog.onResult = { meal -> viewModel.onScanCompleted(meal) }
        dialog.show(parentFragmentManager, "ChatScan")
    }

    private fun observeMessages() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            chatAdapter.submitList(messages) {
                if (messages.isNotEmpty()) recyclerView.scrollToPosition(messages.size - 1)
            }
        }
    }

    private fun observeStickyActions() {
        stickyActionsHelper = StickyActionsHelper(requireContext(), stickyContainer, stickyDivider, viewModel)
        viewModel.stickyActions.observe(viewLifecycleOwner) { actions ->
            stickyActionsHelper.render(actions)
        }
    }

    private fun observeEvents() {
        viewModel.imagePickEvent.observe(viewLifecycleOwner) { event ->
            when (event) {
                "camera" -> openScanDialog(false)
                "gallery" -> openScanDialog(true)
            }
        }
        viewModel.editFoodEvent.observe(viewLifecycleOwner) { show ->
            if (show) dialogHelper.showEditMealDialog()
        }
        viewModel.navigationEvent.observe(viewLifecycleOwner) { target ->
            val nav = androidx.navigation.fragment.NavHostFragment.findNavController(this)
            when (target) {
                "profile" -> nav.navigate(R.id.profileFragment)
                "history" -> nav.navigate(R.id.historyFragment)
                "home"    -> nav.navigate(R.id.homeFragment)
            }
        }
        viewModel.editMedicalEvent.observe(viewLifecycleOwner) { show ->
            if (show) dialogHelper.showEditMedicalBottomSheet()
        }
        viewModel.addFoodItemsEvent.observe(viewLifecycleOwner) { items ->
            if (!items.isNullOrEmpty()) dialogHelper.openEditMealSheet?.addItems(items)
        }
        viewModel.editActivityEvent.observe(viewLifecycleOwner) { show ->
            if (show) dialogHelper.showEditActivityDialog()
        }
        viewModel.editSickStressEvent.observe(viewLifecycleOwner) { show ->
            if (show) dialogHelper.showEditSickStressDialog()
        }
    }

    override fun onResume() {
        super.onResume()
    }
}
