package com.example

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object TimeFormatUtils {

    private val DATE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val DATE_ONLY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun formatCurrentDateTime(): String {
        return synchronized(DATE_TIME_FORMAT) {
            DATE_TIME_FORMAT.format(Date())
        }
    }

    fun formatDateTime(timestamp: Long): String {
        return synchronized(DATE_TIME_FORMAT) {
            DATE_TIME_FORMAT.format(Date(timestamp))
        }
    }

    fun formatTodayDate(): String {
        return synchronized(DATE_ONLY_FORMAT) {
            DATE_ONLY_FORMAT.format(Date())
        }
    }

    /**
     * Converts seconds of day into "HH:mm:ss" formatted string.
     */
    fun formatSecToHms(secondsOfDay: Double): String {
        val normalized = ((secondsOfDay % 86400) + 86400) % 86400
        val totalSecs = normalized.toInt()
        val h = (totalSecs / 3600) % 24
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }

    /**
     * Converts seconds of day into "HH:mm" formatted string.
     */
    fun formatSecToLocalTime(secondsOfDay: Double): String {
        val normalized = ((secondsOfDay % 86400) + 86400) % 86400
        val totalMins = (normalized / 60.0).roundToInt()
        val h = (totalMins / 60) % 24
        val m = totalMins % 60
        return String.format(Locale.getDefault(), "%02d:%02d", h, m)
    }

    /**
     * Formats remaining duration into "mm:ss".
     */
    fun formatRemainingTime(remainingSeconds: Int): String {
        val safeSec = remainingSeconds.coerceAtLeast(0)
        val m = safeSec / 60
        val s = safeSec % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }
}
