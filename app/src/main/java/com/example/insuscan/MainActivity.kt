package com.example.insuscan

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.ui.NavigationUI
import com.example.insuscan.meal.MealSessionManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // Connect bottom navigation to navigation controller
        bottomNav.setupWithNavController(navController)

        // Summary tab should be disabled until the user scans a meal
        val summaryItem = bottomNav.menu.findItem(R.id.summaryFragment)
        summaryItem.isEnabled = MealSessionManager.currentMeal != null

        // Custom item selection logic for Summary tab
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {

                // Summary tab requires an active meal
                R.id.summaryFragment -> {
                    val hasMeal = MealSessionManager.currentMeal != null
                    if (!hasMeal) {
                        Toast.makeText(
                            this,
                            "Scan a meal first to view summary",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnItemSelectedListener false
                    }

                    // Navigate normally when enabled
                    NavigationUI.onNavDestinationSelected(item, navController)
                    return@setOnItemSelectedListener true
                }

                else -> {
                    // Default navigation for all other tabs
                    NavigationUI.onNavDestinationSelected(item, navController)
                    return@setOnItemSelectedListener true
                }
            }
        }

        // TODO: Consider styling the Scan tab to be visually larger (main action button style)
    }

    // Helper to switch programmatically to the Scan tab
    fun selectScanTab() {
        // Get navController from the NavHostFragment
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Always navigate explicitly to ScanFragment
        // Pop up to the root graph so we do not sit on top of an old Summary
        val navOptions = androidx.navigation.NavOptions.Builder()
            .setPopUpTo(R.id.nav_graph, false) // keep back stack root, but clear deeper stack
            .build()

        navController.navigate(R.id.scanFragment, null, navOptions)

        // Also update bottom navigation selection so the Scan tab is highlighted
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.scanFragment
    }

    // Enable Summary tab after a valid meal is scanned/set
    fun enableSummaryTab() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        val summaryItem = bottomNav.menu.findItem(R.id.summaryFragment)
        summaryItem.isEnabled = true
    }
}