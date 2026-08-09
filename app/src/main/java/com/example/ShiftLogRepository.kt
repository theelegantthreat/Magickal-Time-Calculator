package com.example

import kotlinx.coroutines.flow.Flow

class ShiftLogRepository(private val shiftLogDao: ShiftLogDao) {
    val allLogs: Flow<List<LoggedShift>> = shiftLogDao.getAllLogs()

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
