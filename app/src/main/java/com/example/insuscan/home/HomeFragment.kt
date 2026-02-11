package com.example.insuscan.home

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.MainActivity
import com.example.insuscan.R
import com.example.insuscan.network.repository.UserRepositoryImpl
import com.example.insuscan.profile.UserProfileManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var profileImage: android.widget.ImageView
    private lateinit var startScanButton: Button
    private lateinit var openChatButton: Button
    private lateinit var greetingText: TextView
    private lateinit var subtitleText: TextView

    // Temporary modes
    private lateinit var sickModeSwitch: SwitchCompat
    private lateinit var stressModeSwitch: SwitchCompat
    private lateinit var exerciseModeSwitch: SwitchCompat
    private lateinit var sickWarningCard: CardView
    private lateinit var sickWarningText: TextView
    private lateinit var activeModesText: TextView
    private val userRepository = UserRepositoryImpl()
    private val ctx get() = requireContext()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)
        renderGreeting()
        loadProfileImage()
        loadTemporaryModes()
        initializeListeners()
        fetchUserProfile()
    }

    override fun onResume() {
        super.onResume()
        // Refresh modes when returning to home
        loadTemporaryModes()
        renderGreeting()
        loadProfileImage()
    }

    private fun findViews(view: View) {
        profileImage = view.findViewById(R.id.iv_home_avatar)
        startScanButton = view.findViewById(R.id.btn_start_scan)
        openChatButton = view.findViewById(R.id.btn_open_chat)
        greetingText = view.findViewById(R.id.tv_home_greeting)
        subtitleText = view.findViewById(R.id.tv_home_subtitle)

        // Temporary modes
        sickModeSwitch = view.findViewById(R.id.switch_sick_mode)
        stressModeSwitch = view.findViewById(R.id.switch_stress_mode)
        exerciseModeSwitch = view.findViewById(R.id.switch_exercise_mode)
        sickWarningCard = view.findViewById(R.id.card_sick_warning)
        sickWarningText = view.findViewById(R.id.tv_sick_warning)
        activeModesText = view.findViewById(R.id.tv_active_modes)
    }

    private fun loadProfileImage() {
        val ctx = context ?: return
        val localPhotoUrl = UserProfileManager.getProfilePhotoUrl(ctx)
        val googlePhotoUrl = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl

        if (!localPhotoUrl.isNullOrEmpty()) {
            // 1. Custom Profile Picture (Top Priority)
            com.bumptech.glide.Glide.with(this)
                .load(localPhotoUrl)
                .circleCrop()
                .placeholder(R.drawable.duck)
                .error(R.drawable.duck)
                .into(profileImage)
        } else if (googlePhotoUrl != null) {
            // 2. Google Profile Picture (Fallback)
            com.bumptech.glide.Glide.with(this)
                .load(googlePhotoUrl)
                .circleCrop()
                .placeholder(R.drawable.duck)
                .error(R.drawable.duck)
                .into(profileImage)
        } else {
            // 3. Default Duck (Last Resort)
            com.bumptech.glide.Glide.with(this)
                .load(R.drawable.duck)
                .circleCrop()
                .into(profileImage)
        }
    }

    private fun fetchUserProfile() {
        val email = UserProfileManager.getUserEmail(ctx) ?: return
        val name = UserProfileManager.getUserName(ctx) ?: DEFAULT_NAME

        lifecycleScope.launch {
            try {
                val result = userRepository.getUser(email)

                if (result.isSuccess) {
                    val userDto = result.getOrNull()
                    if (userDto != null) {
                        UserProfileManager.syncFromServer(ctx, userDto)
                        renderGreeting()
                        loadProfileImage()
                    }
                } else {
                    try {
                        userRepository.register(email, name)
                    } catch (e: Exception) {
                        android.util.Log.e("HomeFragment", "Registration failed", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error fetching user profile", e)
            }
        }
    }

    private fun renderGreeting() {
        val displayName = getDisplayName()
        greetingText.text = "Hello, $displayName"
    }

    private fun getDisplayName(): String {
        val storedName = UserProfileManager.getUserName(ctx)
        return if (!storedName.isNullOrBlank()) storedName else DEFAULT_NAME
    }

    private fun loadTemporaryModes() {
        val pm = UserProfileManager

        // Load saved states
        sickModeSwitch.isChecked = pm.isSickModeEnabled(ctx)
        stressModeSwitch.isChecked = pm.isStressModeEnabled(ctx)
        exerciseModeSwitch.isChecked = pm.isExerciseModeEnabled(ctx)

        // Check sick mode duration warning
        updateSickWarning()
        updateActiveModesText()
    }

    private fun initializeListeners() {
        // Scan button
        startScanButton.setOnClickListener {
            (activity as? MainActivity)?.selectScanTab()
        }

        // Chat button
        openChatButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_chat)
        }

        // Sick mode toggle
        sickModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            UserProfileManager.setSickModeEnabled(ctx, isChecked)
            updateSickWarning()
            updateActiveModesText()
        }

        // Stress mode toggle
        stressModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            UserProfileManager.setStressModeEnabled(ctx, isChecked)
            updateActiveModesText()
        }

        // Exercise mode toggle
        exerciseModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            UserProfileManager.setExerciseModeEnabled(ctx, isChecked)
            updateActiveModesText()
        }
    }

    private fun updateSickWarning() {
        val isSick = UserProfileManager.isSickModeEnabled(ctx)
        val days = UserProfileManager.getSickModeDays(ctx)

        if (isSick && days >= 3) {
            sickWarningCard.visibility = View.VISIBLE
            sickWarningText.text = "Sick mode has been ON for $days days. Consider consulting your doctor."
        } else {
            sickWarningCard.visibility = View.GONE
        }
    }

    private fun updateActiveModesText() {
        val pm = UserProfileManager
        val activeModes = mutableListOf<String>()

        if (pm.isSickModeEnabled(ctx)) {
            val adj = pm.getSickDayAdjustment(ctx)
            activeModes.add("Sick +$adj%")
        }
        if (pm.isStressModeEnabled(ctx)) {
            val adj = pm.getStressAdjustment(ctx)
            activeModes.add("Stress +$adj%")
        }
        if (pm.isExerciseModeEnabled(ctx)) {
            val adj = pm.getLightExerciseAdjustment(ctx)
            activeModes.add("Exercise -$adj%")
        }

        if (activeModes.isNotEmpty()) {
            activeModesText.visibility = View.VISIBLE
            activeModesText.text = "Active: ${activeModes.joinToString(" • ")}"
        } else {
            activeModesText.visibility = View.GONE
        }
    }

    companion object {
        private const val DEFAULT_NAME = "Daniel"
    }
}