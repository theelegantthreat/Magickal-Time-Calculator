package com.example

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanetaryObservationDao {
    @Query("SELECT * FROM planetary_observations ORDER BY timestamp DESC")
    fun getAllObservations(): Flow<List<PlanetaryObservation>>

    @Query("SELECT * FROM planetary_observations WHERE dateString = :dateStr ORDER BY hourNumber ASC, timestamp ASC")
    fun getObservationsForDate(dateStr: String): Flow<List<PlanetaryObservation>>

    @Query("SELECT * FROM planetary_observations WHERE planetName = :planet ORDER BY timestamp DESC")
    fun getObservationsForPlanet(planet: String): Flow<List<PlanetaryObservation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservation(observation: PlanetaryObservation): Long

    @Update
    suspend fun updateObservation(observation: PlanetaryObservation)

    @Query("DELETE FROM planetary_observations WHERE id = :id")
    suspend fun deleteObservationById(id: Long)

    @Query("DELETE FROM planetary_observations")
    suspend fun clearAllObservations()
}
