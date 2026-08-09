package com.example

import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {

    fun generateCsvString(logs: List<LoggedShift>): String {
        val sb = StringBuilder()
        // Header
        sb.append("ID,Timestamp,Local Time,Date String,Location Name,Latitude,Longitude,Active Planet,Active Tattva,Notes\n")
        
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        for (log in logs) {
            val formattedTime = sdf.format(Date(log.timestamp))
            // Escape notes and location names for CSV
            val escapedNotes = escapeCsvField(log.notes)
            val escapedLocationName = escapeCsvField(log.locationName)
            
            sb.append("${log.id},")
              .append("${log.timestamp},")
              .append("\"$formattedTime\",")
              .append("\"${log.dateString}\",")
              .append("\"$escapedLocationName\",")
              .append("${log.latitude},")
              .append("${log.longitude},")
              .append("\"${log.planetName}\",")
              .append("\"${log.tattvaName}\",")
              .append("\"$escapedNotes\"\n")
        }
        return sb.toString()
    }

    private fun escapeCsvField(field: String): String {
        return field.replace("\"", "\"\"").replace("\n", " ").replace("\r", "")
    }

    fun shareCsvData(context: Context, csvData: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Magickal Time Shift Logs")
                putExtra(Intent.EXTRA_TEXT, csvData)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooserIntent = Intent.createChooser(intent, "Export Shift Logs CSV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
