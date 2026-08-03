@file:Suppress("unused")

package com.leovp.kotlin.exts

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Author: Michael Leo
 * Date: 2023/12/5 10:35
 */
/**
 * Format this duration in milliseconds as `HH:mm:ss`.
 *
 * The receiver is a non-negative duration. A negative value is clamped to `0` (durations are never
 * negative); this prevents nonsensical negative fields in the output (remediation L-K1).
 */
fun Long.formatTimestamp(): String {
    val durationMs = coerceAtLeast(0L)
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) -
        TimeUnit.HOURS.toMinutes(hours)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) -
        TimeUnit.MINUTES.toSeconds(minutes) -
        TimeUnit.HOURS.toSeconds(hours)
    return String.format(Locale.ENGLISH, "%02d:%02d:%02d", hours, minutes, seconds)
}

/**
 * Format this duration in milliseconds as `mm:ss`.
 *
 * The receiver is a non-negative duration. A negative value is clamped to `0` (remediation L-K1).
 */
fun Long.formatTimestampShort(): String {
    val durationMs = coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) -
        TimeUnit.MINUTES.toSeconds(minutes)
    return String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds)
}
