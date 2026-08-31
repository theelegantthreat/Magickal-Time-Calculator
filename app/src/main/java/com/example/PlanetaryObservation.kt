package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "planetary_observations")
data class PlanetaryObservation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val dateString: String,
    val planetName: String,
    val planetSymbol: String = "",
    val hourNumber: Int = 1,
    val isNight: Boolean = false,
    val tattwaName: String = "",
    val tattwaSymbol: String = "",
    val title: String = "",
    val content: String = "",
    val moodOrEnergy: String = "Balanced",
    val tags: String = "",
    val locationName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
