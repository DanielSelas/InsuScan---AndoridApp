package com.example.insuscan.utils

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeHelper {

    private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val DISPLAY_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val API_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    // Parses server timestamp - handles both ISO string and millis
    fun parseTimestamp(ts: String?): Long {
        if (ts == null) return System.currentTimeMillis()

        // Try parsing as Long (millis)
        ts.toLongOrNull()?.let { return it }

        // Try parsing as ISO date string
        return try {
            ISO_FORMAT.parse(ts)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    // Formats timestamp for display (Today, Yesterday, or date)
    fun formatDate(timestamp: Long): String {
        return when {
            DateUtils.isToday(timestamp) -> "Today"
            DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
            else -> DISPLAY_FORMAT.format(Date(timestamp))
        }
    }

    fun formatForApi(timestamp: Long): String {
        return API_DATE_FORMAT.format(Date(timestamp))
    }
}