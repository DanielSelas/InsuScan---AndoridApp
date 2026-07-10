package com.example.insuscan.utils

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Parses and formats timestamps for display and API communication.
 *
 * Handles both ISO-8601 strings and epoch-millisecond values,
 * and produces locale-aware labels for cards, list headers, and API queries.
 */
object DateTimeHelper {

    private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val FULL_ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val DISPLAY_FORMAT = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

    /**
     * Parses a server timestamp that may be an ISO-8601 string or a raw epoch-millisecond value.
     * Returns the current time if the input is null or unparseable.
     */
    fun parseTimestamp(ts: String?): Long {
        if (ts == null) return System.currentTimeMillis()

        ts.toLongOrNull()?.let { return it }

        return try {
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

    /**
     * Formats a timestamp for display inside meal cards.
     * Shows "Today" or "Yesterday" when applicable, otherwise "dd MMM, HH:mm".
     */
    fun formatDate(timestamp: Long): String {
        return when {
            DateUtils.isToday(timestamp) -> "Today, " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
            DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS) -> "Yesterday, " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
            else -> DISPLAY_FORMAT.format(Date(timestamp))
        }
    }

    /**
     * Formats a timestamp as a day-group header label ("Today", "Yesterday", or "dd MMM").
     */
    fun formatHeaderDate(timestamp: Long): String {
        return when {
            DateUtils.isToday(timestamp) -> "Today"
            DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
            else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
        }
    }

    /** Formats a timestamp as a full ISO-8601 UTC string for API requests. */
    fun formatForApi(timestamp: Long): String {
        return FULL_ISO_FORMAT.format(Date(timestamp))
    }

    /** Formats a timestamp as a UTC date string (yyyy-MM-dd) for server-side date filtering. */
    fun formatDateForFilter(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestamp))
    }
}