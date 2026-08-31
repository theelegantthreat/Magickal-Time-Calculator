package com.example

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PlanetaryObservationRepository(private val dao: PlanetaryObservationDao) {
    val allObservations: Flow<List<PlanetaryObservation>> = dao.getAllObservations()

    fun allItemsStateFlow(scope: CoroutineScope): StateFlow<List<PlanetaryObservation>> =
        allObservations.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun getObservationsForDate(dateStr: String): Flow<List<PlanetaryObservation>> =
        dao.getObservationsForDate(dateStr)

    fun getObservationsForPlanet(planet: String): Flow<List<PlanetaryObservation>> =
        dao.getObservationsForPlanet(planet)

    suspend fun insert(observation: PlanetaryObservation): Long =
        dao.insertObservation(observation)

    suspend fun update(observation: PlanetaryObservation) =
        dao.updateObservation(observation)

    suspend fun deleteById(id: Long) =
        dao.deleteObservationById(id)

    suspend fun clearAll() =
        dao.clearAllObservations()
}
