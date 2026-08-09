package com.example

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftLogDao {
    @Query("SELECT * FROM logged_shifts ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LoggedShift>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LoggedShift)

    @Query("DELETE FROM logged_shifts WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("DELETE FROM logged_shifts")
    suspend fun clearAllLogs()
}
