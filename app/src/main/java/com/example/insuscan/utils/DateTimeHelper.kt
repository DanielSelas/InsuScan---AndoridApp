package com.example.insuscan.utils

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeHelper {

    private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val FULL_ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    private val DISPLAY_FORMAT = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

    // Parses server timestamp - handles both ISO string and millis
    fun parseTimestamp(ts: String?): Long {
        if (ts == null) return System.currentTimeMillis()

        // Try parsing as Long (millis)
        ts.toLongOrNull()?.let { return it }

        // Try parsing as ISO date string
        return try {
            // Handle potentially different server formats
             if (ts.endsWith("Z")) {
                 FULL_ISO_FORMAT.parse(ts)?.time ?: System.currentTimeMillis()
             } else {
                 ISO_FORMAT.parse(ts)?.time ?: System.currentTimeMillis()
             }
        } catch (e: Exception) {
            e.printStackTrace()
            System.currentTimeMillis()
        }
    }

    // Formats formatted timestamp for display inside cards (e.g. 19:04 or 12 Jan, 19:04)
    fun formatDate(timestamp: Long): String {
        return when {
            DateUtils.isToday(timestamp) -> "Today, " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
            DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS) -> "Yesterday, " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
            else -> DISPLAY_FORMAT.format(Date(timestamp))
        }
    }

    // New: Formats date strictly for Headers (Grouping by Day)
    fun formatHeaderDate(timestamp: Long): String {
        return when {
            DateUtils.isToday(timestamp) -> "Today"
            DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
            else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
        }
    }

    fun formatForApi(timestamp: Long): String {
        return FULL_ISO_FORMAT.format(Date(timestamp))
    }

    // Formats date for filtering (YYYY-MM-DD) - matches server expectation
    fun formatDateForFilter(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestamp))
    }
}