package com.example.insuscan.utils

import android.content.Context
import android.widget.Toast

/**
 * Convenience wrapper for showing [Toast] messages.
 */
object ToastHelper {

    fun showShort(context: Context, message: String) {
        context.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun showLong(context: Context, message: String) {
        context.let {
            Toast.makeText(it, message, Toast.LENGTH_LONG).show()
        }
    }
}