package com.nobodyiran.nobodyvpn.util

import java.util.Locale

object Fmt {
    private fun fmt(value: Double, unit: String): String {
        val v = if (value >= 100) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
        return "$v $unit"
    }

    /** Human readable speed from bytes per second. */
    fun speed(bytesPerSec: Long): String = when {
        bytesPerSec <= 0 -> "0 B/s"
        bytesPerSec < 1024 -> "$bytesPerSec B/s"
        bytesPerSec < 1024 * 1024 -> fmt(bytesPerSec / 1024.0, "KB/s")
        bytesPerSec < 1024L * 1024 * 1024 -> fmt(bytesPerSec / 1024.0 / 1024, "MB/s")
        else -> fmt(bytesPerSec / 1024.0 / 1024 / 1024, "GB/s")
    }

    /** Human readable volume from bytes. */
    fun volume(bytes: Long): String = when {
        bytes <= 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> fmt(bytes / 1024.0, "KB")
        bytes < 1024L * 1024 * 1024 -> fmt(bytes / 1024.0 / 1024, "MB")
        bytes < 1024L * 1024 * 1024 * 1024 -> fmt(bytes / 1024.0 / 1024 / 1024, "GB")
        else -> fmt(bytes / 1024.0 / 1024 / 1024 / 1024, "TB")
    }

    /** HH:MM:SS from seconds. */
    fun duration(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, sec)
    }
}
