package com.example

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {

    enum class ExportType {
        SHIFT_LOGS_ONLY,
        TRACKED_CYCLES_ONLY,
        COMPLETE_ALL
    }

    /**
     * Generates a well-formatted CSV string of user-recorded shift experience logs.
     */
    fun generateLogsCsv(logs: List<LoggedShift>): String {
        val sb = StringBuilder()
        // CSV Metadata and Header
        sb.append("# Magickal Time - Shift Log History Export\n")
        sb.append("# Generated: ${formatCurrentDateTime()}\n")
        sb.append("# Total Logged Shifts: ${logs.size}\n\n")
        sb.append("Log_ID,Timestamp_Epoch_Ms,Local_DateTime,Date_String,Location_Name,Latitude,Longitude,Active_Planetary_Hour,Active_Tattwic_Tide,User_Notes\n")

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        for (log in logs) {
            val formattedTime = sdf.format(Date(log.timestamp))
            val escapedNotes = escapeCsvField(log.notes)
            val escapedLocationName = escapeCsvField(log.locationName)
            val escapedPlanet = escapeCsvField(log.planetName)
            val escapedTattva = escapeCsvField(log.tattvaName)

            sb.append("${log.id},")
                .append("${log.timestamp},")
                .append("\"$formattedTime\",")
                .append("\"${log.dateString}\",")
                .append("\"$escapedLocationName\",")
                .append("${log.latitude},")
                .append("${log.longitude},")
                .append("\"$escapedPlanet\",")
                .append("\"$escapedTattva\",")
                .append("\"$escapedNotes\"\n")
        }
        return sb.toString()
    }

    /**
     * Generates a detailed CSV schedule of calculated tracked cycles (Planetary Hours, Tattwic Tides, Combined Shifts).
     */
    fun generateCyclesCsv(
        calc: MainViewModel.CalculationResults,
        locationName: String,
        latitude: Double,
        longitude: Double
    ): String {
        val sb = StringBuilder()
        sb.append("# Magickal Time - Astronomical & Elemental Cycles Schedule\n")
        sb.append("# Date: ${calc.date}\n")
        sb.append("# Location: ${escapeCsvField(locationName)} (Lat: $latitude, Lon: $longitude)\n")
        sb.append("# Sunrise Today: ${formatSecToHms(calc.sunriseSeconds)}, Sunset: ${formatSecToHms(calc.sunsetSeconds)}, Sunrise Next Day: ${formatSecToHms(calc.tomorrowSunriseSeconds)}\n")
        sb.append("# Day Hour Length: ${String.format(Locale.US, "%.1f", calc.dayHourLengthSeconds / 60.0)} mins, Night Hour Length: ${String.format(Locale.US, "%.1f", calc.nightHourLengthSeconds / 60.0)} mins, Tattva Length: ${String.format(Locale.US, "%.1f", calc.tattvaLengthSeconds / 60.0)} mins\n")
        sb.append("# Generated: ${formatCurrentDateTime()}\n\n")

        // 1. Planetary Hours Section
        sb.append("=== SECTION: PLANETARY HOURS ===\n")
        sb.append("Hour_Number,Phase,Planet_Name,Symbol,Start_Time_Local,End_Time_Local,Duration_Minutes,Start_Second_Of_Day,End_Second_Of_Day,Color_Hex\n")
        for (h in calc.planetaryHours) {
            val phase = if (h.isNight) "Night" else "Day"
            val durationMin = String.format(Locale.US, "%.1f", h.durationSeconds / 60.0)
            sb.append("${h.number},")
                .append("\"$phase\",")
                .append("\"${escapeCsvField(h.planetName)}\",")
                .append("\"${h.planetSymbol}\",")
                .append("\"${formatSecToHms(h.startSecondOfDay)}\",")
                .append("\"${formatSecToHms(h.endSecondOfDay)}\",")
                .append("$durationMin,")
                .append("${h.startSecondOfDay.toInt()},")
                .append("${h.endSecondOfDay.toInt()},")
                .append("\"${h.colorHex}\"\n")
        }
        sb.append("\n")

        // 2. Tattwic Tides Section
        sb.append("=== SECTION: TATTWIC TIDES ===\n")
        sb.append("Cycle_Index,Tattva_Name,Symbol,Element,Start_Time_Local,End_Time_Local,Duration_Minutes,Start_Second_Of_Day,End_Second_Of_Day,Description\n")
        for (tv in calc.tattvas) {
            val durationMin = String.format(Locale.US, "%.1f", tv.durationSeconds / 60.0)
            sb.append("${tv.index + 1},")
                .append("\"${escapeCsvField(tv.name)}\",")
                .append("\"${tv.symbol}\",")
                .append("\"${escapeCsvField(tv.element)}\",")
                .append("\"${formatSecToHms(tv.startSecondOfDay)}\",")
                .append("\"${formatSecToHms(tv.endSecondOfDay)}\",")
                .append("$durationMin,")
                .append("${tv.startSecondOfDay.toInt()},")
                .append("${tv.endSecondOfDay.toInt()},")
                .append("\"${escapeCsvField(tv.description)}\"\n")
        }
        sb.append("\n")

        // 3. Combined Planetary + Tattwic Alignments Section
        sb.append("=== SECTION: COMBINED PLANETARY & TATTWIC ALIGNMENTS ===\n")
        sb.append("Alignment_Index,Planet_Name,Planet_Symbol,Tattva_Name,Tattva_Symbol,Start_Time_Local,End_Time_Local,Duration_Minutes\n")
        calc.combined.forEachIndexed { idx, cb ->
            val durationMin = String.format(Locale.US, "%.1f", cb.durationSeconds / 60.0)
            sb.append("${idx + 1},")
                .append("\"${escapeCsvField(cb.planetName)}\",")
                .append("\"${cb.planetSymbol}\",")
                .append("\"${escapeCsvField(cb.tattvaName)}\",")
                .append("\"${cb.tattvaSymbol}\",")
                .append("\"${formatSecToHms(cb.startSecondOfDay)}\",")
                .append("\"${formatSecToHms(cb.endSecondOfDay)}\",")
                .append("$durationMin\n")
        }

        return sb.toString()
    }

    /**
     * Generates a comprehensive single CSV report containing both tracked cycle definitions and user shift logs.
     */
    fun generateCompleteExportCsv(
        logs: List<LoggedShift>,
        calc: MainViewModel.CalculationResults?,
        locationName: String,
        latitude: Double,
        longitude: Double
    ): String {
        val sb = StringBuilder()
        sb.append("# ========================================================\n")
        sb.append("# MAGICKAL TIME - COMPLETE CYCLES & SHIFT LOGS ARCHIVE\n")
        sb.append("# Generated: ${formatCurrentDateTime()}\n")
        sb.append("# Location: ${escapeCsvField(locationName)} (Lat: $latitude, Lon: $longitude)\n")
        sb.append("# Total Logged Shifts in Database: ${logs.size}\n")
        sb.append("# ========================================================\n\n")

        if (calc != null) {
            sb.append(generateCyclesCsv(calc, locationName, latitude, longitude))
            sb.append("\n\n")
        }

        sb.append("=== SECTION: USER SHIFT LOGS & RITUAL NOTES ===\n")
        if (logs.isEmpty()) {
            sb.append("# No recorded user shift logs found in database.\n")
        } else {
            sb.append("Log_ID,Timestamp_Epoch_Ms,Local_DateTime,Date_String,Location_Name,Latitude,Longitude,Active_Planetary_Hour,Active_Tattwic_Tide,User_Notes\n")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            for (log in logs) {
                val formattedTime = sdf.format(Date(log.timestamp))
                val escapedNotes = escapeCsvField(log.notes)
                val escapedLocationName = escapeCsvField(log.locationName)
                val escapedPlanet = escapeCsvField(log.planetName)
                val escapedTattva = escapeCsvField(log.tattvaName)

                sb.append("${log.id},")
                    .append("${log.timestamp},")
                    .append("\"$formattedTime\",")
                    .append("\"${log.dateString}\",")
                    .append("\"$escapedLocationName\",")
                    .append("${log.latitude},")
                    .append("${log.longitude},")
                    .append("\"$escapedPlanet\",")
                    .append("\"$escapedTattva\",")
                    .append("\"$escapedNotes\"\n")
            }
        }

        return sb.toString()
    }

    /**
     * Saves the CSV string directly to a file in the user's local device Downloads folder.
     * Compatible with Android 10+ MediaStore as well as standard external directory.
     * Returns a human-readable confirmation string with the file location, or null if error.
     */
    fun saveCsvToDownloads(
        context: Context,
        baseName: String,
        csvContent: String
    ): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${baseName}_$timeStamp.csv"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MagickalTime")
                }
                val resolver = context.contentResolver
                val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(csvContent.toByteArray(Charsets.UTF_8))
                        outputStream.flush()
                    }
                    "Saved to Downloads/MagickalTime/$fileName"
                } else {
                    // Fallback to internal/external files dir
                    saveToAppStorage(context, fileName, csvContent)
                }
            } else {
                // Pre-Android Q external storage write
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val subDir = File(downloadsDir, "MagickalTime")
                if (!subDir.exists()) {
                    subDir.mkdirs()
                }
                val file = File(subDir, fileName)
                FileOutputStream(file).use { os ->
                    os.write(csvContent.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
                "Saved to ${file.absolutePath}"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to context files directory
            try {
                saveToAppStorage(context, fileName, csvContent)
            } catch (ex: Exception) {
                ex.printStackTrace()
                null
            }
        }
    }

    private fun saveToAppStorage(context: Context, fileName: String, csvContent: String): String {
        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val file = File(targetDir, fileName)
        FileOutputStream(file).use { os ->
            os.write(csvContent.toByteArray(Charsets.UTF_8))
            os.flush()
        }
        return "Saved to ${file.name} in App Storage"
    }

    /**
     * Shares the CSV data via standard Android Share Intent.
     */
    fun shareCsvData(context: Context, title: String, csvData: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, csvData)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooserIntent = Intent.createChooser(intent, "Export / Share CSV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun escapeCsvField(field: String): String {
        return field.replace("\"", "\"\"").replace("\n", " ").replace("\r", "")
    }

    private fun formatCurrentDateTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun formatSecToHms(secondsOfDay: Double): String {
        val totalSecs = secondsOfDay.toInt()
        val h = (totalSecs / 3600) % 24
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }
}
