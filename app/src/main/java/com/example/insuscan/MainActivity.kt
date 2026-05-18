package com.example.insuscan

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.InsulinPlan

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    private lateinit var navHome: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var navScan: LinearLayout
    private lateinit var navChat: LinearLayout
    private lateinit var navProfile: LinearLayout

    private val navItems get() = listOf(navHome, navHistory, navScan, navChat, navProfile)

    private val destinationToNav by lazy {
        mapOf(
            R.id.homeFragment    to navHome,
            R.id.historyFragment to navHistory,
            R.id.scanFragment    to navScan,
            R.id.chatFragment    to navChat,
            R.id.profileFragment to navProfile
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rootLayout = findViewById<View>(android.R.id.content)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        com.example.insuscan.utils.FileLogger.init(applicationContext)
        com.example.insuscan.utils.FileLogger.log("MAIN", "App Started")

        initializeViews()
        setupNavListeners()
        observeDestination()
    }

    private fun initializeViews() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val bottomNav = findViewById<View>(R.id.bottom_nav)
        navHome    = bottomNav.findViewById(R.id.nav_home)
        navHistory = bottomNav.findViewById(R.id.nav_history)
        navScan    = bottomNav.findViewById(R.id.nav_scan)
        navChat    = bottomNav.findViewById(R.id.nav_chat)
        navProfile = bottomNav.findViewById(R.id.nav_profile)
    }

    private fun setupNavListeners() {
        navHome.setOnClickListener    { navigateTo(R.id.homeFragment) }
        navHistory.setOnClickListener { navigateTo(R.id.historyFragment) }
        navScan.setOnClickListener    { navigateTo(R.id.scanFragment) }
        navChat.setOnClickListener    { navigateTo(R.id.chatFragment) }
        navProfile.setOnClickListener { navigateTo(R.id.profileFragment) }
    }

    private fun navigateTo(destinationId: Int) {
        val navOptions = androidx.navigation.NavOptions.Builder()
            .setPopUpTo(R.id.nav_graph, false)
            .build()
        navController.navigate(destinationId, null, navOptions)
    }

    private fun observeDestination() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isAuthScreen = destination.id == R.id.loginFragment ||
                    destination.id == R.id.splashAnimationFragment ||
                    destination.id == R.id.registrationStep1Fragment ||
                    destination.id == R.id.registrationStep2Fragment ||
                    destination.id == R.id.registrationStep3Fragment

            val bottomNavView = findViewById<View>(R.id.bottom_nav)
            bottomNavView.visibility = if (isAuthScreen) View.GONE else View.VISIBLE

            val activeNav = destinationToNav[destination.id]
            updateNavSelection(activeNav)
        }
    }

    private fun updateNavSelection(activeNav: LinearLayout?) {
        val primary = ContextCompat.getColor(this, R.color.primary)
        val secondary = ContextCompat.getColor(this, R.color.text_secondary)

        navItems.forEach { item ->
            val isActive = item == activeNav
            val isScan = item == navScan

            if (isScan) return@forEach

            item.findViewWithTag<ImageView>(null)
            val icon = item.getChildAt(0) as? ImageView
            val label = item.getChildAt(1) as? TextView

            icon?.setColorFilter(if (isActive) primary else secondary)
            label?.setTextColor(if (isActive) primary else secondary)
        }
    }

    fun selectScanTab() {
        navigateTo(R.id.scanFragment)
    }
}