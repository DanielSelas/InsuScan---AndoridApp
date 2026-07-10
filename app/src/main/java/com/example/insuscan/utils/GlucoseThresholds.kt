package com.example.insuscan.utils

/**
 * Global clinical glucose boundaries used across the app (in mg/dL).
 *
 * Values below [LOW] or above [HIGH] trigger critical alerts
 * regardless of the user's personal target.
 */
object GlucoseThresholds {
    const val LOW = 70
    const val HIGH = 180
}