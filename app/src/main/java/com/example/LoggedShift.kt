package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "logged_shifts")
data class LoggedShift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val dateString: String,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val planetName: String,
    val tattvaName: String,
    val notes: String
)
