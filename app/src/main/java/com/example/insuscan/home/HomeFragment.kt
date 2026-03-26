package com.example.insuscan.home

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.insuscan.MainActivity
import com.example.insuscan.R
import com.example.insuscan.home.helpers.ProfileImageHelper
import com.example.insuscan.home.helpers.InsulinPlanSelector
import com.example.insuscan.network.dto.InsulinPlanDto
import com.example.insuscan.network.repository.UserRepositoryImpl
import com.example.insuscan.profile.UserProfileManager
import kotlinx.coroutines.launch
import android.widget.LinearLayout
import android.widget.RadioButton
import androidx.recyclerview.widget.RecyclerView


class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var greetingText: TextView
    private lateinit var profileImageHelper: ProfileImageHelper
    private lateinit var planSelector: InsulinPlanSelector

    private val userRepository = UserRepositoryImpl()
    private val ctx get() = requireContext()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)
        renderGreeting()
        profileImageHelper.loadImage()
        planSelector.loadPlans(null)
        setupNavigationListeners(view)
        fetchUserProfile()
    }

    override fun onResume() {
        super.onResume()
        renderGreeting()
        profileImageHelper.loadImage()
    }

    private fun findViews(view: View) {
        greetingText = view.findViewById(R.id.tv_home_greeting)

        profileImageHelper = ProfileImageHelper(
            fragment = this,
            profileImage = view.findViewById(R.id.iv_home_avatar)
        )

        planSelector = InsulinPlanSelector(
            defaultRow = view.findViewById(R.id.row_default_plan),
            defaultRadio = view.findViewById(R.id.rb_default_plan),
            recyclerView = view.findViewById(R.id.rv_plans)
        )


    }

    private fun setupNavigationListeners(view: View) {
        view.findViewById<Button>(R.id.btn_start_scan).setOnClickListener {
            (activity as? MainActivity)?.selectScanTab()
        }
        view.findViewById<Button>(R.id.btn_open_chat).setOnClickListener {
            findNavController().navigate(R.id.action_home_to_chat)
        }
    }

    private fun fetchUserProfile() {
        val email = UserProfileManager.getUserEmail(ctx) ?: return

        lifecycleScope.launch {
            try {
                val result = userRepository.getUser(email)

                if (result.isSuccess) {
                    result.getOrNull()?.let { userDto ->
                        UserProfileManager.syncFromServer(ctx, userDto)
                        renderGreeting()
                        planSelector.loadPlans(userDto.insulinPlans)
                    }
                } else {
                    Log.e(TAG, "Server profile fetch failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching user profile", e)
            }
        }
    }

    private fun renderGreeting() {
        val displayName = UserProfileManager.getUserName(ctx) ?: DEFAULT_NAME
        greetingText.text = "Hello, $displayName"
    }

    companion object {
        private const val TAG = "HomeFragment"
        private const val DEFAULT_NAME = "Daniel"
    }
}
