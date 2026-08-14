package com.example

import kotlinx.coroutines.flow.Flow

class CalculationPreferencesRepository(
    private val calculationPreferencesDao: CalculationPreferencesDao
) {
    val preferencesFlow: Flow<CalculationPreferences?> = calculationPreferencesDao.getPreferencesFlow(1)

    suspend fun getPreferences(): CalculationPreferences? {
        return calculationPreferencesDao.getPreferences(1)
    }

    suspend fun savePreferences(preferences: CalculationPreferences) {
        calculationPreferencesDao.insertOrUpdatePreferences(preferences.copy(id = 1, updatedAt = System.currentTimeMillis()))
    }
}
