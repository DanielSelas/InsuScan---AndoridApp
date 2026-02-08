package com.example.insuscan

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.utils.ToastHelper

class MainActivity : AppCompatActivity() {
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Handle edge-to-edge display
        val rootLayout = findViewById<View>(android.R.id.content)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Debug Logger
        com.example.insuscan.utils.FileLogger.init(applicationContext)
        com.example.insuscan.utils.FileLogger.log("MAIN", "App Started")

        initializeViews()
        setupBottomNavigationListener()

        // Removed prefetchProfile() call to avoid double-fetch race condition with LoginFragment
    }

    private fun initializeViews() {
        // Get navController from the NavHostFragment
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        navController = navHostFragment.navController

        bottomNav = findViewById(R.id.bottom_nav)

        // Connect bottom navigation to navigation controller
        bottomNav.setupWithNavController(navController)

        // Hide bottom nav on login screen
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.loginFragment -> bottomNav.visibility = View.GONE
                else -> bottomNav.visibility = View.VISIBLE
            }
        }
    }

    private fun setupBottomNavigationListener() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.summaryFragment -> {
// Navigate directly instead of using NavigationUI
                    navController.navigate(item.itemId)
                    true
                }

                else -> {
                    navController.navigate(item.itemId)
                    true
                }
            }
        }
    }

    private fun hasMeal(): Boolean {
        return MealSessionManager.currentMeal != null
    }

    // Helper to switch programmatically to the Scan tab
    fun selectScanTab() {
        // Always navigate explicitly to ScanFragment
        // Pop up to the root graph so we do not sit on top of an old Summary
        val navOptions = androidx.navigation.NavOptions.Builder()
            .setPopUpTo(R.id.nav_graph, false) // keep back stack root, but clear deeper stack
            .build()

        navController.navigate(R.id.scanFragment, null, navOptions)

        // Also update bottom navigation selection so the Scan tab is highlighted
        bottomNav.selectedItemId = R.id.scanFragment
    }
}