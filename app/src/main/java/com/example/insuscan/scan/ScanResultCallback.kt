package com.example.insuscan.scan

import com.example.insuscan.meal.Meal

interface ScanResultCallback {
    fun onScanSuccess(meal: Meal)
    fun onScanCancelled() {}
}