package com.example

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalculationPreferencesDao {
    @Query("SELECT * FROM calculation_preferences WHERE id = :id LIMIT 1")
    fun getPreferencesFlow(id: Int = 1): Flow<CalculationPreferences?>

    @Query("SELECT * FROM calculation_preferences WHERE id = :id LIMIT 1")
    suspend fun getPreferences(id: Int = 1): CalculationPreferences?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePreferences(preferences: CalculationPreferences)

    @Query("DELETE FROM calculation_preferences WHERE id = :id")
    suspend fun deletePreferences(id: Int = 1)
}
