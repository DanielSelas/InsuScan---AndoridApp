package com.example.insuscan

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.ui.NavigationUI
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.utils.ToastHelper

class MainActivity : AppCompatActivity() {
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()

        setupBottomNavigationListener()

        // TODO: Consider styling the Scan tab to be visually larger (main action button style)
    }

    private fun initializeViews() {
        // Get navController from the NavHostFragment
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        navController = navHostFragment.navController

        bottomNav = findViewById(R.id.bottom_nav)

        // Connect bottom navigation to navigation controller
        bottomNav.setupWithNavController(navController)
    }

    //    private fun setupBottomNavigationListener() {
//        bottomNav.setOnItemSelectedListener { item ->
//            when (item.itemId) {
//                R.id.summaryFragment -> {
//                    if (!hasMeal()) {
//                        ToastHelper.showShort(this, "Scan a meal first to view summary")
//                        return@setOnItemSelectedListener false
//                    }
//                    NavigationUI.onNavDestinationSelected(item, navController)
//                    return@setOnItemSelectedListener true
//                }
//                else -> {
//                    NavigationUI.onNavDestinationSelected(item, navController)
//                    return@setOnItemSelectedListener true
//                }
//            }
//        }
//    }
    private fun setupBottomNavigationListener() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.summaryFragment -> {
                    if (!hasMeal()) {
                        ToastHelper.showShort(this, "Scan a meal first to view summary")
                        return@setOnItemSelectedListener false
                    }
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