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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {

    enum class ExportType(val title: String, val description: String) {
        SHIFT_LOGS_ONLY(
            title = "Shift Logs & Ritual Notes",
            description = "Recorded transitions with planet, tattva & location context"
        ),
        TRACKED_CYCLES_ONLY(
            title = "Tracked Daily Cycles Schedule",
            description = "All 24h planetary hours, tattwic tides & alignments for active date"
        ),
        COMPLETE_ALL(
            title = "Complete Archive (All Cycles & Logs)",
            description = "Comprehensive dataset with calculated cycle timelines and user notes"
        )
    }

    /**
     * Generates a well-formatted CSV string of user-recorded shift experience logs.
     */
    fun generateLogsCsv(logs: List<LoggedShift>): String {
        val sb = StringBuilder()
        sb.append("# Magickal Time - Shift Log History Export\n")
        sb.append("# Generated: ${TimeFormatUtils.formatCurrentDateTime()}\n")
        sb.append("# Total Logged Shifts: ${logs.size}\n\n")
        appendShiftLogRows(sb, logs)
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
        sb.append("# Sunrise Today: ${TimeFormatUtils.formatSecToHms(calc.sunriseSeconds)}, Sunset: ${TimeFormatUtils.formatSecToHms(calc.sunsetSeconds)}, Sunrise Next Day: ${TimeFormatUtils.formatSecToHms(calc.tomorrowSunriseSeconds)}\n")
        sb.append("# Day Hour Length: ${String.format(Locale.US, "%.1f", calc.dayHourLengthSeconds / 60.0)} mins, Night Hour Length: ${String.format(Locale.US, "%.1f", calc.nightHourLengthSeconds / 60.0)} mins, Tattva Length: ${String.format(Locale.US, "%.1f", calc.tattvaLengthSeconds / 60.0)} mins\n")
        sb.append("# Generated: ${TimeFormatUtils.formatCurrentDateTime()}\n\n")

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
                .append("\"${TimeFormatUtils.formatSecToHms(h.startSecondOfDay)}\",")
                .append("\"${TimeFormatUtils.formatSecToHms(h.endSecondOfDay)}\",")
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
                .append("\"${TimeFormatUtils.formatSecToHms(tv.startSecondOfDay)}\",")
                .append("\"${TimeFormatUtils.formatSecToHms(tv.endSecondOfDay)}\",")
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
                .append("\"${TimeFormatUtils.formatSecToHms(cb.startSecondOfDay)}\",")
                .append("\"${TimeFormatUtils.formatSecToHms(cb.endSecondOfDay)}\",")
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
        sb.append("# Generated: ${TimeFormatUtils.formatCurrentDateTime()}\n")
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
            appendShiftLogRows(sb, logs)
        }

        return sb.toString()
    }

    private fun appendShiftLogRows(sb: StringBuilder, logs: List<LoggedShift>) {
        sb.append("Log_ID,Timestamp_Epoch_Ms,Local_DateTime,Date_String,Location_Name,Latitude,Longitude,Active_Planetary_Hour,Active_Tattwic_Tide,User_Notes\n")
        for (log in logs) {
            val formattedTime = TimeFormatUtils.formatDateTime(log.timestamp)
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

    /**
     * Saves the CSV string directly to a file in the user's local device Downloads folder.
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
                    saveToAppStorage(context, fileName, csvContent)
                }
            } else {
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
}
