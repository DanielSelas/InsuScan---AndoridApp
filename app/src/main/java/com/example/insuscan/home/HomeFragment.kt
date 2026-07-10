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

import com.example.insuscan.profile.UserProfileManager
import kotlinx.coroutines.launch

import com.example.insuscan.home.helpers.HomeDailySummaryHelper
import com.example.insuscan.meal.MealSessionManager

import android.text.format.DateUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.insuscan.appdata.AppDataStore
import com.example.insuscan.appdata.DataState
import com.example.insuscan.mapping.InsulinPlanMapper
import com.example.insuscan.meal.Meal

/**
 * Home screen: shows the greeting, the daily meal summary, the glucose gauge,
 * and the active insulin plan selector. Observes state from AppDataStore.
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var greetingText: TextView
    private lateinit var profileImageHelper: ProfileImageHelper
    private lateinit var planSelector: InsulinPlanSelector
    private lateinit var greetingSubText: TextView
    private lateinit var dailySummaryHelper: HomeDailySummaryHelper

    private val ctx get() = requireContext()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)
        renderGreeting()
        profileImageHelper.loadImage()
        planSelector.loadPlans(null)
        setupNavigationListeners(view)
        observeDataStore()
    }

    override fun onResume() {
        super.onResume()
        renderGreeting()
        profileImageHelper.loadImage()
        AppDataStore.refreshAll()
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
            (activity as? MainActivity)?.selectScanTab()
        }
        view.findViewById<Button>(R.id.btn_open_chat).setOnClickListener {
            findNavController().navigate(R.id.action_home_to_chat)
        }
        view.findViewById<TextView>(R.id.tv_manage_plans).setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
        }
    }

    private fun applySelectedPlan() {
        val plan = planSelector.getSelectedPlan()
        if (plan != null) {
            MealSessionManager.setActivePlan(plan.name ?: PLAN_NAME_CUSTOM, plan.icr, plan.isf, plan.targetGlucose)
        } else {
            MealSessionManager.setActivePlan(PLAN_NAME_DEFAULT, null, null, null)
        }
    }

    private fun renderGreeting() {
        val displayName = UserProfileManager.getUserName(ctx) ?: getString(R.string.default_greeting_name)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> getString(R.string.greeting_morning)
            hour < 17 -> getString(R.string.greeting_afternoon)
            else -> getString(R.string.greeting_evening)
        }
        greetingSubText.text = greeting
        greetingText.text = displayName
    }


    private fun observeDataStore() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { AppDataStore.profileState.collect { handleProfileState(it) } }
                launch { AppDataStore.mealsState.collect { handleMealsState(it) } }
            }
        }
    }

    private fun handleProfileState(state: DataState<Long>) {
        when (state) {
            is DataState.Loading -> planSelector.loadPlans(UserProfileManager.getInsulinPlans(ctx))
            is DataState.Ready -> {
                renderGreeting()
                val rawRatio = UserProfileManager.getInsulinCarbRatioRaw(ctx)
                val placeholder = getString(R.string.value_placeholder)
                val defaultIcr = rawRatio?.split(":")?.lastOrNull()?.trim() ?: placeholder
                val defaultIsf = UserProfileManager.getCorrectionFactor(ctx)?.toInt()?.toString() ?: placeholder
                val defaultTg = UserProfileManager.getTargetGlucose(ctx)?.toString() ?: placeholder
                view?.findViewById<TextView>(R.id.tv_default_plan_details)?.text =
                    getString(R.string.plan_details_format, defaultIcr, defaultIsf, defaultTg)
                val plans = UserProfileManager.getInsulinPlans(ctx)
                planSelector.loadPlans(plans)
                MealSessionManager.availablePlans = InsulinPlanMapper.toModelList(plans)
            }
            is DataState.Error -> Log.e(TAG, "Profile refresh failed: ${state.cause.message}")
        }
    }

    private fun handleMealsState(state: DataState<List<Meal>>) {
        when (state) {
            is DataState.Loading -> dailySummaryHelper.showLoading()
            is DataState.Ready -> {
                val todayMeals = state.data.filter { DateUtils.isToday(it.timestamp) }
                dailySummaryHelper.displaySummary(todayMeals)
            }
            is DataState.Error -> dailySummaryHelper.showError(
                com.example.insuscan.network.NetworkErrorPresenter.message(state.cause)
            )
        }
    }

    override fun onPause() {
        super.onPause()
        applySelectedPlan()
    }

    companion object {
        private const val TAG = "HomeFragment"
        private const val PLAN_NAME_DEFAULT = "Default"
        private const val PLAN_NAME_CUSTOM = "Custom Plan"

    }
}
