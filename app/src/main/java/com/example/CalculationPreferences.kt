package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for persisting user calculation settings, location coordinates,
 * astronomical offsets, and theme preferences for offline access.
 */
@Entity(tableName = "calculation_preferences")
data class CalculationPreferences(
    @PrimaryKey val id: Int = 1, // Single active profile key
    val locationName: String = "New York, USA",
    val latitude: Double = 40.7128,
    val longitude: Double = -74.0060,
    val sunriseOverride: String = "06:00:00",
    val sunsetOverride: String = "18:00:00",
    val tomorrowSunriseOverride: String = "06:00:00",
    val darkTheme: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val tattvaDisplayMode: String = "ALL", // ALL, DAY_ONLY, NIGHT_ONLY
    val activeViewMode: String = "PLANETARY_HOURS", // PLANETARY_HOURS, TATTWIC_TIDES, COMBINED_VIEW
    val lastSelectedDate: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
