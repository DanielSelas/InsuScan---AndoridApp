package com.example.insuscan.utils

import android.content.Context
import android.widget.Toast

// Use 'object' for Singleton pattern (no need to create an instance)
object ToastHelper {

    fun showShort(context: Context, message: String) {
        // Safe check for null context before showing the Toast
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }


    fun showLong(context: Context, message: String) {
        // 'let' safely executes the code block only if context is not null
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_LONG).show()
        }
    }
}