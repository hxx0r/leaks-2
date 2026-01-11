package com.quarx.leaks

import android.graphics.Color
import android.graphics.drawable.Drawable
import java.io.Serializable

data class TrafficData(
    val timestamp: Long = System.currentTimeMillis(),
    val appName: String,
    val packageName: String,
    val appIcon: Drawable? = null,
    val dataSent: Long = 0,
    val dataReceived: Long = 0,
    val trackers: List<Tracker> = emptyList(),
    val permissions: List<String> = emptyList(),
    val riskLevel: RiskLevel = RiskLevel.LOW
) : Serializable

data class Tracker(
    val name: String,
    val category: TrackerCategory,
    val description: String,
    val domains: List<String>
) : Serializable

enum class TrackerCategory {
    ANALYTICS, ADVERTISING, CRASH_REPORTING, SOCIAL_MEDIA, LOCATION, PROFILING
}

enum class RiskLevel(val color: Int, val description: String) {
    LOW(Color.GREEN, "Низкий риск"),
    MEDIUM(Color.YELLOW, "Средний риск"),
    HIGH(Color.RED, "Высокий риск"),
    CRITICAL(Color.parseColor("#8B0000"), "Критический риск")
}