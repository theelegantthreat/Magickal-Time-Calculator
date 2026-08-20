package com.example

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

enum class ViewMode {
    PLANETARY_HOURS,
    TATTWIC_TIDES,
    COMBINED_VIEW
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = ShiftLogRepository(database.shiftLogDao())
    private val preferencesRepository = CalculationPreferencesRepository(database.calculationPreferencesDao())

    // UI state flows
    val allLogs: StateFlow<List<LoggedShift>> = repository.allItemsStateFlow(viewModelScope)
    val preferencesFlow: StateFlow<CalculationPreferences?> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _viewMode = MutableStateFlow(ViewMode.PLANETARY_HOURS)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(true)
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    // Location info (stored or defaults)
    private val _latitude = MutableStateFlow(40.7128) // default NYC
    val latitude: StateFlow<Double> = _latitude.asStateFlow()

    private val _longitude = MutableStateFlow(-74.0060)
    val longitude: StateFlow<Double> = _longitude.asStateFlow()

    private val _locationName = MutableStateFlow("New York, USA")
    val locationName: StateFlow<String> = _locationName.asStateFlow()

    // Interactive inputs
    private val _currentDateString = MutableStateFlow("")
    val currentDateString: StateFlow<String> = _currentDateString.asStateFlow()

    private val _isManualDateSelected = MutableStateFlow(false)
    val isManualDateSelected: StateFlow<Boolean> = _isManualDateSelected.asStateFlow()

    // Custom time string overrides (format: HH:MM:SS)
    private val _sunriseOverride = MutableStateFlow("06:00:00")
    val sunriseOverride: StateFlow<String> = _sunriseOverride.asStateFlow()

    private val _sunsetOverride = MutableStateFlow("18:00:00")
    val sunsetOverride: StateFlow<String> = _sunsetOverride.asStateFlow()

    private val _tomorrowSunriseOverride = MutableStateFlow("06:00:00")
    val tomorrowSunriseOverride: StateFlow<String> = _tomorrowSunriseOverride.asStateFlow()

    // Computational astronomical states
    data class CalculationResults(
        val date: String,
        val dayOfWeekIndex: Int,
        val sunriseSeconds: Double,
        val sunsetSeconds: Double,
        val tomorrowSunriseSeconds: Double,
        val planetaryHours: List<AstronomyEngine.PlanetaryHour>,
        val tattvas: List<AstronomyEngine.TattvaCycle>,
        val combined: List<AstronomyEngine.CombinedShift>,
        val dayHourLengthSeconds: Double,
        val nightHourLengthSeconds: Double,
        val tattvaLengthSeconds: Double
    )

    private val _calculationResults = MutableStateFlow<CalculationResults?>(null)
    val calculationResults: StateFlow<CalculationResults?> = _calculationResults.asStateFlow()

    // Live transitions states
    private val _currentTimeSeconds = MutableStateFlow(0.0)
    val currentTimeSeconds: StateFlow<Double> = _currentTimeSeconds.asStateFlow()

    private val _currentPlanetaryHour = MutableStateFlow<AstronomyEngine.PlanetaryHour?>(null)
    val currentPlanetaryHour: StateFlow<AstronomyEngine.PlanetaryHour?> = _currentPlanetaryHour.asStateFlow()

    private val _currentTattva = MutableStateFlow<AstronomyEngine.TattvaCycle?>(null)
    val currentTattva: StateFlow<AstronomyEngine.TattvaCycle?> = _currentTattva.asStateFlow()

    private val _currentCombined = MutableStateFlow<AstronomyEngine.CombinedShift?>(null)
    val currentCombined: StateFlow<AstronomyEngine.CombinedShift?> = _currentCombined.asStateFlow()

    // Filters
    private val _activePlanetFilters = MutableStateFlow(AstronomyEngine.PLANET_ORDER.toSet())
    val activePlanetFilters: StateFlow<Set<String>> = _activePlanetFilters.asStateFlow()

    private val _activeTattvaFilters = MutableStateFlow(AstronomyEngine.TATTVA_ORDER.map { it.name }.toSet())
    val activeTattvaFilters: StateFlow<Set<String>> = _activeTattvaFilters.asStateFlow()

    // Filter day/night mode
    enum class TattvaFilterMode { ALL, DAY_ONLY, NIGHT_ONLY }
    private val _tattvaDisplayMode = MutableStateFlow(TattvaFilterMode.ALL)
    val tattvaDisplayMode: StateFlow<TattvaFilterMode> = _tattvaDisplayMode.asStateFlow()

    // Ticker job
    private var tickerJob: Job? = null

    // For change detection
    private var lastObservedPlanet: String? = null
    private var lastObservedTattva: String? = null

    init {
        // Initialize notification channel
        NotificationHelper.createNotificationChannel(application)

        // Set initial date as today
        _currentDateString.value = TimeFormatUtils.formatTodayDate()

        // Load persisted calculation preferences from Room database
        viewModelScope.launch {
            val savedPrefs = preferencesRepository.getPreferences()
            if (savedPrefs != null) {
                _latitude.value = savedPrefs.latitude
                _longitude.value = savedPrefs.longitude
                _locationName.value = savedPrefs.locationName
                _sunriseOverride.value = savedPrefs.sunriseOverride
                _sunsetOverride.value = savedPrefs.sunsetOverride
                _tomorrowSunriseOverride.value = savedPrefs.tomorrowSunriseOverride
                _darkTheme.value = savedPrefs.darkTheme
                _notificationsEnabled.value = savedPrefs.notificationsEnabled
                _hapticsEnabled.value = savedPrefs.hapticsEnabled
                _tattvaDisplayMode.value = try {
                    TattvaFilterMode.valueOf(savedPrefs.tattvaDisplayMode)
                } catch (_: Exception) {
                    TattvaFilterMode.ALL
                }
                _viewMode.value = try {
                    ViewMode.valueOf(savedPrefs.activeViewMode)
                } catch (_: Exception) {
                    ViewMode.PLANETARY_HOURS
                }
            } else {
                persistCurrentPreferences()
            }
            recalculateAndRun()
        }

        recalculateAndRun()
        startClock()
    }

    private fun persistCurrentPreferences() {
        viewModelScope.launch {
            preferencesRepository.savePreferences(
                CalculationPreferences(
                    id = 1,
                    locationName = _locationName.value,
                    latitude = _latitude.value,
                    longitude = _longitude.value,
                    sunriseOverride = _sunriseOverride.value,
                    sunsetOverride = _sunsetOverride.value,
                    tomorrowSunriseOverride = _tomorrowSunriseOverride.value,
                    darkTheme = _darkTheme.value,
                    notificationsEnabled = _notificationsEnabled.value,
                    hapticsEnabled = _hapticsEnabled.value,
                    tattvaDisplayMode = _tattvaDisplayMode.value.name,
                    activeViewMode = _viewMode.value.name,
                    lastSelectedDate = _currentDateString.value
                )
            )
        }
    }

    private fun ShiftLogRepository.allItemsStateFlow(scope: kotlinx.coroutines.CoroutineScope) = this.allLogs
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        persistCurrentPreferences()
    }

    fun setDarkTheme(enabled: Boolean) {
        _darkTheme.value = enabled
        persistCurrentPreferences()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        persistCurrentPreferences()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        persistCurrentPreferences()
    }

    fun updateLocation(lat: Double, lon: Double, name: String) {
        _latitude.value = lat
        _longitude.value = lon
        _locationName.value = name

        calculateSuntimesFromCoordinates()
        persistCurrentPreferences()
        recalculateAndRun()
    }

    fun manualOverrideSunrise(sunset: String, type: Int) {
        // type: 0=Sunrise, 1=Sunset, 2=TomorrowSunrise
        when (type) {
            0 -> _sunriseOverride.value = sunset
            1 -> _sunsetOverride.value = sunset
            2 -> _tomorrowSunriseOverride.value = sunset
        }
        persistCurrentPreferences()
        recalculateAndRun()
    }

    fun setDateString(date: String, isManual: Boolean = true) {
        _isManualDateSelected.value = isManual
        _currentDateString.value = date
        calculateSuntimesFromCoordinates()
        recalculateAndRun()
    }

    fun goToPreviousDay() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        try {
            val parsed = sdf.parse(_currentDateString.value)
            if (parsed != null) cal.time = parsed
        } catch (_: Exception) {}
        cal.add(Calendar.DAY_OF_MONTH, -1)
        setDateString(sdf.format(cal.time), isManual = true)
    }

    fun goToNextDay() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        try {
            val parsed = sdf.parse(_currentDateString.value)
            if (parsed != null) cal.time = parsed
        } catch (_: Exception) {}
        cal.add(Calendar.DAY_OF_MONTH, 1)
        setDateString(sdf.format(cal.time), isManual = true)
    }

    fun goToToday() {
        _isManualDateSelected.value = false
        syncToActiveAstrologicalDay()
    }

    private fun syncToActiveAstrologicalDay() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val now = Calendar.getInstance()
        val todayStr = sdf.format(now.time)

        val dateParts = todayStr.split("-")
        val year = dateParts.getOrNull(0)?.toIntOrNull() ?: 2026
        val month = dateParts.getOrNull(1)?.toIntOrNull() ?: 6
        val day = dateParts.getOrNull(2)?.toIntOrNull() ?: 1

        val timezone = TimeZone.getDefault()
        val curDate = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
        }
        val tzOffsetHours = timezone.getOffset(curDate.timeInMillis).toDouble() / 1000.0 / 3600.0

        val todaySolar = AstronomyEngine.getSolarTimes(year, month, day, _latitude.value, _longitude.value, tzOffsetHours)
        val sunriseSec = todaySolar.sunriseHours * 3600.0
        val currentSecOfDay = now.get(Calendar.HOUR_OF_DAY) * 3600.0 + now.get(Calendar.MINUTE) * 60.0 + now.get(Calendar.SECOND).toDouble()

        val activeCal = (now.clone() as Calendar)
        if (currentSecOfDay < sunriseSec) {
            // Before sunrise today => Active astrological day is Yesterday
            activeCal.add(Calendar.DAY_OF_MONTH, -1)
        }
        val activeDateStr = sdf.format(activeCal.time)

        if (_currentDateString.value != activeDateStr) {
            _currentDateString.value = activeDateStr
            calculateSuntimesFromCoordinates()
            recalculateAndRun()
        }
    }

    private fun calculateSuntimesFromCoordinates() {
        val dateParts = _currentDateString.value.split("-")
        if (dateParts.size != 3) return
        val year = dateParts[0].toIntOrNull() ?: 2026
        val month = dateParts[1].toIntOrNull() ?: 6
        val day = dateParts[2].toIntOrNull() ?: 1

        val timezone = TimeZone.getDefault()
        val curDate = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
        }
        val tzOffsetHours = timezone.getOffset(curDate.timeInMillis).toDouble() / 1000.0 / 3600.0

        // Calculate for today
        val todaySolar = AstronomyEngine.getSolarTimes(year, month, day, _latitude.value, _longitude.value, tzOffsetHours)
        
        // Calculate for tomorrow
        val tomorrowCal = (curDate.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowSolar = AstronomyEngine.getSolarTimes(
            tomorrowCal.get(Calendar.YEAR),
            tomorrowCal.get(Calendar.MONTH) + 1,
            tomorrowCal.get(Calendar.DAY_OF_MONTH),
            _latitude.value,
            _longitude.value,
            tzOffsetHours
        )

        _sunriseOverride.value = TimeFormatUtils.formatSecToHms(todaySolar.sunriseHours * 3600.0)
        _sunsetOverride.value = TimeFormatUtils.formatSecToHms(todaySolar.sunsetHours * 3600.0)
        _tomorrowSunriseOverride.value = TimeFormatUtils.formatSecToHms(tomorrowSolar.sunriseHours * 3600.0)
    }

    fun recalculateAndRun() {
        if (_sunriseOverride.value.isEmpty() || _sunsetOverride.value.isEmpty() || _tomorrowSunriseOverride.value.isEmpty()) {
            calculateSuntimesFromCoordinates()
        }

        val dateStr = _currentDateString.value
        val dateParts = dateStr.split("-")
        val year = dateParts.getOrNull(0)?.toIntOrNull() ?: 2026
        val month = dateParts.getOrNull(1)?.toIntOrNull() ?: 6
        val day = dateParts.getOrNull(2)?.toIntOrNull() ?: 1

        val dowIndex = AstronomyEngine.getDayOfWeekIndex(year, month, day)

        var sunriseSec = parseTimeToSeconds(_sunriseOverride.value)
        var sunsetSec = parseTimeToSeconds(_sunsetOverride.value)
        var tomorrowSunriseSec = parseTimeToSeconds(_tomorrowSunriseOverride.value) + 86400.0

        // Handle edge wraps
        if (sunsetSec <= sunriseSec) sunsetSec += 86400.0
        if (tomorrowSunriseSec <= sunsetSec) tomorrowSunriseSec += 86400.0

        val planetaryHours = AstronomyEngine.calculatePlanetaryHours(sunriseSec, sunsetSec, tomorrowSunriseSec, dowIndex)
        val tattvas = AstronomyEngine.calculateTattvas(sunriseSec, tomorrowSunriseSec)
        val combined = AstronomyEngine.calculateCombinedView(planetaryHours, tattvas)

        val dayLen = sunsetSec - sunriseSec
        val nightLen = tomorrowSunriseSec - sunsetSec
        val tattvaLen = (tomorrowSunriseSec - sunriseSec) / 60.0

        _calculationResults.value = CalculationResults(
            date = dateStr,
            dayOfWeekIndex = dowIndex,
            sunriseSeconds = sunriseSec,
            sunsetSeconds = sunsetSec,
            tomorrowSunriseSeconds = tomorrowSunriseSec,
            planetaryHours = planetaryHours,
            tattvas = tattvas,
            combined = combined,
            dayHourLengthSeconds = dayLen / 12.0,
            nightHourLengthSeconds = nightLen / 12.0,
            tattvaLengthSeconds = tattvaLen
        )

        tickClockState()
    }

    private fun startClock() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                tickClockState()
                delay(1000)
            }
        }
    }

    private fun tickClockState() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        if (!_isManualDateSelected.value) {
            syncToActiveAstrologicalDay()
        }

        val calc = _calculationResults.value ?: return

        val selCal = Calendar.getInstance().apply {
            try {
                val parsed = sdf.parse(calc.date)
                if (parsed != null) time = parsed
            } catch (_: Exception) {}
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val selMidnightMillis = selCal.timeInMillis
        val nowMillis = System.currentTimeMillis()
        val curSec = (nowMillis - selMidnightMillis) / 1000.0

        _currentTimeSeconds.value = curSec

        // Find active planet
        val activePlanet = calc.planetaryHours.find { curSec >= it.startSecondOfDay && curSec < it.endSecondOfDay }
        _currentPlanetaryHour.value = activePlanet

        // Find active tattva
        val activeTattva = calc.tattvas.find { curSec >= it.startSecondOfDay && curSec < it.endSecondOfDay }
        _currentTattva.value = activeTattva

        // Find active combined shift
        val activeCombined = calc.combined.find { curSec >= it.startSecondOfDay && curSec < it.endSecondOfDay }
        _currentCombined.value = activeCombined

        // Detect shifts
        if (activePlanet != null) {
            if (lastObservedPlanet != null && lastObservedPlanet != activePlanet.planetName) {
                triggerPlanetTransition(activePlanet)
            }
            lastObservedPlanet = activePlanet.planetName
        }

        if (activeTattva != null) {
            if (lastObservedTattva != null && lastObservedTattva != activeTattva.name) {
                triggerTattvaTransition(activeTattva)
            }
            lastObservedTattva = activeTattva.name
        }
    }

    private fun triggerPlanetTransition(ph: AstronomyEngine.PlanetaryHour) {
        if (_hapticsEnabled.value) {
            triggerVibration()
        }
        if (_notificationsEnabled.value) {
            NotificationHelper.postShiftNotification(
                getApplication(),
                planetName = ph.planetName,
                planetSymbol = ph.planetSymbol,
                tattvaName = _currentTattva.value?.name ?: "—",
                tattvaSymbol = _currentTattva.value?.symbol ?: "",
                description = "We have entered the Hour of ${ph.planetName}."
            )
        }
    }

    private fun triggerTattvaTransition(tv: AstronomyEngine.TattvaCycle) {
        if (_hapticsEnabled.value) {
            triggerVibration()
        }
        if (_notificationsEnabled.value) {
            NotificationHelper.postShiftNotification(
                getApplication(),
                planetName = _currentPlanetaryHour.value?.planetName ?: "—",
                planetSymbol = _currentPlanetaryHour.value?.planetSymbol ?: "",
                tattvaName = tv.name,
                tattvaSymbol = tv.symbol,
                description = "The Elemental Tattva Tide has shifted into ${tv.name} (${tv.element})."
            )
        }
    }

    private fun triggerVibration() {
        val context = getApplication<Application>()
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(300)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Filters manipulations
    fun togglePlanetFilter(planet: String) {
        val current = _activePlanetFilters.value.toMutableSet()
        if (current.contains(planet)) {
            current.remove(planet)
        } else {
            current.add(planet)
        }
        _activePlanetFilters.value = current
    }

    fun toggleTattvaFilter(tattva: String) {
        val current = _activeTattvaFilters.value.toMutableSet()
        if (current.contains(tattva)) {
            current.remove(tattva)
        } else {
            current.add(tattva)
        }
        _activeTattvaFilters.value = current
    }

    fun setTattvaDisplayMode(mode: TattvaFilterMode) {
        _tattvaDisplayMode.value = mode
        persistCurrentPreferences()
    }

    // Room Database Shift Logging
    fun logShiftExperience(notesText: String) {
        val calc = _calculationResults.value ?: return
        val activePlanet = _currentPlanetaryHour.value?.planetName ?: "Unknown"
        val activeTattva = _currentTattva.value?.name ?: "Unknown"

        viewModelScope.launch {
            val newLog = LoggedShift(
                dateString = calc.date,
                locationName = _locationName.value,
                latitude = _latitude.value,
                longitude = _longitude.value,
                planetName = activePlanet,
                tattvaName = activeTattva,
                notes = notesText
            )
            repository.insert(newLog)
            if (_hapticsEnabled.value) {
                triggerVibration()
            }
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    // Export Actions
    fun exportShiftLogsCsv(context: Context, saveLocally: Boolean = true, onResult: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val logs = repository.allLogs.stateIn(this).value
            if (logs.isEmpty()) {
                onResult?.invoke("No shift logs to export.")
                return@launch
            }
            val csvText = ExportUtils.generateLogsCsv(logs)
            if (saveLocally) {
                val pathInfo = ExportUtils.saveCsvToDownloads(context, "magick_shift_logs", csvText)
                if (pathInfo != null) {
                    onResult?.invoke("Exported shift logs: $pathInfo")
                } else {
                    onResult?.invoke("Failed to write CSV file locally.")
                }
            } else {
                ExportUtils.shareCsvData(context, "Magickal Time - Shift Logs CSV", csvText)
                onResult?.invoke("Opened share sheet for Shift Logs CSV.")
            }
        }
    }

    fun exportTrackedCyclesCsv(context: Context, saveLocally: Boolean = true, onResult: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val calc = _calculationResults.value
            if (calc == null) {
                onResult?.invoke("No tracked cycles calculated yet.")
                return@launch
            }
            val csvText = ExportUtils.generateCyclesCsv(
                calc = calc,
                locationName = _locationName.value,
                latitude = _latitude.value,
                longitude = _longitude.value
            )
            if (saveLocally) {
                val cleanDate = calc.date.replace("-", "")
                val pathInfo = ExportUtils.saveCsvToDownloads(context, "tracked_cycles_${cleanDate}", csvText)
                if (pathInfo != null) {
                    onResult?.invoke("Exported tracked cycles: $pathInfo")
                } else {
                    onResult?.invoke("Failed to write CSV file locally.")
                }
            } else {
                ExportUtils.shareCsvData(context, "Magickal Time - Tracked Cycles Schedule (${calc.date})", csvText)
                onResult?.invoke("Opened share sheet for Tracked Cycles CSV.")
            }
        }
    }

    fun exportCompleteHistoryAndCyclesCsv(context: Context, saveLocally: Boolean = true, onResult: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val logs = repository.allLogs.stateIn(this).value
            val calc = _calculationResults.value
            val csvText = ExportUtils.generateCompleteExportCsv(
                logs = logs,
                calc = calc,
                locationName = _locationName.value,
                latitude = _latitude.value,
                longitude = _longitude.value
            )
            if (saveLocally) {
                val pathInfo = ExportUtils.saveCsvToDownloads(context, "magick_complete_history_and_cycles", csvText)
                if (pathInfo != null) {
                    onResult?.invoke("Exported complete archive: $pathInfo")
                } else {
                    onResult?.invoke("Failed to write CSV file locally.")
                }
            } else {
                ExportUtils.shareCsvData(context, "Magickal Time - Complete Cycles & Shift History CSV", csvText)
                onResult?.invoke("Opened share sheet for Complete Archive CSV.")
            }
        }
    }

    fun exportCsv(context: Context) {
        exportCompleteHistoryAndCyclesCsv(context, saveLocally = true) { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun parseTimeToSeconds(timeStr: String): Double {
        val parts = timeStr.split(":").map { it.toIntOrNull() ?: 0 }
        val hours = parts.getOrNull(0) ?: 0
        val mins = parts.getOrNull(1) ?: 0
        val secs = parts.getOrNull(2) ?: 0
        return hours * 3600.0 + mins * 60.0 + secs.toDouble()
    }
}
