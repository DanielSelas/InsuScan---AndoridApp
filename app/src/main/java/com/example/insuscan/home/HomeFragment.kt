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
import com.example.insuscan.home.helpers.HomeDailySummaryHelper
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.InsulinPlan



class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var greetingText: TextView
    private lateinit var profileImageHelper: ProfileImageHelper
    private lateinit var planSelector: InsulinPlanSelector
    private lateinit var greetingSubText: TextView
    private lateinit var dailySummaryHelper: HomeDailySummaryHelper

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
        planSelector.loadPlans(null)

        loadDailySummary()

    }

    private fun findViews(view: View) {
        greetingText = view.findViewById(R.id.tv_home_greeting)
        greetingSubText = view.findViewById(R.id.tv_home_greeting_sub)


        profileImageHelper = ProfileImageHelper(
            fragment = this,
            profileImage = view.findViewById(R.id.iv_home_avatar)
        )

        planSelector = InsulinPlanSelector(
            defaultRow = view.findViewById(R.id.row_default_plan),
            defaultRadio = view.findViewById(R.id.rb_default_plan),
            recyclerView = view.findViewById(R.id.rv_plans)
        )

        dailySummaryHelper = HomeDailySummaryHelper(
            context = ctx,
            tvMealsLogged = view.findViewById(R.id.tv_meals_logged),
            tvTotalCarbs = view.findViewById(R.id.tv_total_carbs),
            tvTotalInsulin = view.findViewById(R.id.tv_total_insulin),
            tvGlucoseValue = view.findViewById(R.id.tv_glucose_value),
            tvGlucoseStatus = view.findViewById(R.id.tv_glucose_status),
            gaugeGlucose = view.findViewById(R.id.gauge_glucose)
        )
    }

    private fun setupNavigationListeners(view: View) {
        view.findViewById<Button>(R.id.btn_start_scan).setOnClickListener {
            applySelectedPlan()
            (activity as? MainActivity)?.selectScanTab()
        }
        view.findViewById<Button>(R.id.btn_open_chat).setOnClickListener {
            applySelectedPlan()
            findNavController().navigate(R.id.action_home_to_chat)
        }
        view.findViewById<TextView>(R.id.tv_manage_plans).setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
        }
    }

    private fun applySelectedPlan() {
        val plan = planSelector.getSelectedPlan()
        if (plan != null) {
            MealSessionManager.setActivePlan(plan.name ?: "Custom Plan", plan.icr, plan.isf, plan.targetGlucose)
        } else {
            MealSessionManager.setActivePlan("Default", null, null, null)
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

                        val defaultIcr = userDto.insulinCarbRatio?.split(":")?.lastOrNull()?.trim() ?: "--"
                        val defaultIsf = userDto.correctionFactor?.toInt()?.toString() ?: "--"
                        val defaultTg = userDto.targetGlucose?.toString() ?: "--"
                        view?.findViewById<TextView>(R.id.tv_default_plan_details)?.text =
                            "ICR $defaultIcr · ISF $defaultIsf · TG $defaultTg"

                        planSelector.loadPlans(userDto.insulinPlans)
                        MealSessionManager.availablePlans = userDto.insulinPlans?.map { dto ->
                            InsulinPlan(
                                id = dto.id ?: "",
                                name = dto.name ?: "Custom",
                                isDefault = dto.isDefault,
                                icr = dto.icr,
                                isf = dto.isf,
                                targetGlucose = dto.targetGlucose
                            )
                        } ?: emptyList()
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
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        greetingSubText.text = greeting
        greetingText.text = displayName
    }

    private fun loadDailySummary() {
        val email = UserProfileManager.getUserEmail(ctx) ?: return
        lifecycleScope.launch {
            try {
                dailySummaryHelper.loadAndDisplay(email)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load daily summary", e)
            }
        }
    }

    companion object {
        private const val TAG = "HomeFragment"
        private const val DEFAULT_NAME = "Daniel"
    }
}
