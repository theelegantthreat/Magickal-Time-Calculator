package com.example

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ShiftLogRepository(private val shiftLogDao: ShiftLogDao) {
    val allLogs: Flow<List<LoggedShift>> = shiftLogDao.getAllLogs()

    fun allItemsStateFlow(scope: CoroutineScope): StateFlow<List<LoggedShift>> =
        allLogs.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    suspend fun insert(log: LoggedShift) {
        shiftLogDao.insertLog(log)
    }

    suspend fun deleteById(id: Long) {
        shiftLogDao.deleteLogById(id)
    }

    suspend fun clearAll() {
        shiftLogDao.clearAllLogs()
    }
}
