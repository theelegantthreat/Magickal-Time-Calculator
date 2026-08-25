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

    private val sharedPrefs = application.getSharedPreferences("magick_time_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(application)
    private val repository = ShiftLogRepository(database.shiftLogDao())
    private val preferencesRepository = CalculationPreferencesRepository(database.calculationPreferencesDao())
    private val locationProvider = LocationProvider(application)
    val planetaryHourService = PlanetaryHourCalculationService(application, locationProvider)

    // UI state flows
    val allLogs: StateFlow<List<LoggedShift>> = repository.allItemsStateFlow(viewModelScope)
    val preferencesFlow: StateFlow<CalculationPreferences?> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.PLANETARY_HOURS)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _darkTheme = MutableStateFlow(sharedPrefs.getBoolean("dark_theme", true))
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(sharedPrefs.getBoolean("notifications_enabled", true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(sharedPrefs.getBoolean("haptics_enabled", true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    // Location info (stored or defaults)
    private val _latitude = MutableStateFlow(sharedPrefs.getFloat("lat", 40.7128f).toDouble()) // default NYC
    val latitude: StateFlow<Double> = _latitude.asStateFlow()

    private val _longitude = MutableStateFlow(sharedPrefs.getFloat("lon", -74.0060f).toDouble())
    val longitude: StateFlow<Double> = _longitude.asStateFlow()

    private val _locationName = MutableStateFlow(sharedPrefs.getString("location_name", "New York, USA") ?: "New York, USA")
    val locationName: StateFlow<String> = _locationName.asStateFlow()

    // Interactive inputs
    private val _currentDateString = MutableStateFlow("")
    val currentDateString: StateFlow<String> = _currentDateString.asStateFlow()

    private val _isManualDateSelected = MutableStateFlow(false)
    val isManualDateSelected: StateFlow<Boolean> = _isManualDateSelected.asStateFlow()

    private val _detailedSolarTimes = MutableStateFlow<DetailedSolarTimes?>(null)
    val detailedSolarTimes: StateFlow<DetailedSolarTimes?> = _detailedSolarTimes.asStateFlow()

    // Dynamic sunrise/sunset states computed offline using SunriseSunsetHelper
    private val _sunriseOverride = MutableStateFlow(
        formatHoursToTimeString(
            SunriseSunsetHelper.calculateSolarTimes(
                sharedPrefs.getFloat("lat", 40.7128f).toDouble(),
                sharedPrefs.getFloat("lon", -74.0060f).toDouble(),
                TimeZone.getDefault().id,
                Calendar.getInstance()
            ).sunriseHours
        )
    )
    val sunriseOverride: StateFlow<String> = _sunriseOverride.asStateFlow()

    private val _sunsetOverride = MutableStateFlow(
        formatHoursToTimeString(
            SunriseSunsetHelper.calculateSolarTimes(
                sharedPrefs.getFloat("lat", 40.7128f).toDouble(),
                sharedPrefs.getFloat("lon", -74.0060f).toDouble(),
                TimeZone.getDefault().id,
                Calendar.getInstance()
            ).sunsetHours
        )
    )
    val sunsetOverride: StateFlow<String> = _sunsetOverride.asStateFlow()

    private val _tomorrowSunriseOverride = MutableStateFlow(
        formatHoursToTimeString(
            SunriseSunsetHelper.calculateSolarTimes(
                sharedPrefs.getFloat("lat", 40.7128f).toDouble(),
                sharedPrefs.getFloat("lon", -74.0060f).toDouble(),
                TimeZone.getDefault().id,
                Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            ).sunriseHours
        )
    )
    val tomorrowSunriseOverride: StateFlow<String> = _tomorrowSunriseOverride.asStateFlow()

    // Computational astronomical states
    data class CalculationResults(
        val date: String,
        val dayOfWeekIndex: Int,
        val dayName: String,
        val dayRulerName: String,
        val dayRulerSymbol: String,
        val sunriseSeconds: Double,
        val sunsetSeconds: Double,
        val tomorrowSunriseSeconds: Double,
        val planetaryHours: List<AstronomyEngine.PlanetaryHour>,
        val tattvas: List<AstronomyEngine.TattvaCycle>,
        val combined: List<AstronomyEngine.CombinedShift>,
        val dayHourLengthSeconds: Double,
        val nightHourLengthSeconds: Double,
        val tattvaLengthSeconds: Double,
        val isBeforeSunrise: Boolean = false
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
        // Initialize channel
        NotificationHelper.createNotificationChannel(application)

        // Set initial date as today
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        _currentDateString.value = sdf.format(Date())

        // Load persisted calculation preferences from Room database if present
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
                // Initialize default preferences in Room database
                persistCurrentPreferences()
            }
            // Recalculate based on active settings
            recalculateAndRun()
        }

        // Calculate initially
        recalculateAndRun()

        // Start ticking
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
        sharedPrefs.edit().putBoolean("dark_theme", enabled).apply()
        persistCurrentPreferences()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        sharedPrefs.edit().putBoolean("notifications_enabled", enabled).apply()
        persistCurrentPreferences()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        sharedPrefs.edit().putBoolean("haptics_enabled", enabled).apply()
        persistCurrentPreferences()
    }

    fun hasLocationPermission(): Boolean = locationProvider.hasLocationPermission()

    fun acquireCurrentLocation(onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _isLocating.value = true
            try {
                if (!locationProvider.hasLocationPermission()) {
                    onComplete?.invoke(false, "Location permission not granted")
                    return@launch
                }

                val userLoc = locationProvider.fetchCurrentLocation()
                if (userLoc != null) {
                    updateLocation(userLoc.latitude, userLoc.longitude, userLoc.locationName)
                    onComplete?.invoke(true, "Location acquired: ${userLoc.locationName}")
                } else {
                    onComplete?.invoke(false, "Unable to acquire current GPS coordinates")
                }
            } catch (e: Exception) {
                onComplete?.invoke(false, "Location error: ${e.localizedMessage ?: "Unknown"}")
            } finally {
                _isLocating.value = false
            }
        }
    }

    fun updateLocation(lat: Double, lon: Double, name: String) {
        _latitude.value = lat
        _longitude.value = lon
        _locationName.value = name

        sharedPrefs.edit()
            .putFloat("lat", lat.toFloat())
            .putFloat("lon", lon.toFloat())
            .putString("location_name", name)
            .apply()

        // Trigger automatic recalculations of Sunrise/Sunset
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
        val todayYear = now.get(Calendar.YEAR)
        val todayMonth = now.get(Calendar.MONTH) + 1
        val todayDay = now.get(Calendar.DAY_OF_MONTH)

        val timezone = TimeZone.getDefault()
        val timeZoneId = timezone.id

        // Step 1: Get today's sunrise using SunriseSunsetHelper to compare against the current moment
        val todaySolar = SunriseSunsetHelper.calculateSolarTimes(_latitude.value, _longitude.value, timeZoneId, todayYear, todayMonth, todayDay)
        val todaySunriseSec = todaySolar.sunriseHours * 3600.0
        val currentSecOfDay = now.get(Calendar.HOUR_OF_DAY) * 3600.0 + now.get(Calendar.MINUTE) * 60.0 + now.get(Calendar.SECOND).toDouble()

        // Step 2: Determine which planetary day is currently active
        // If current time is before today's sunrise -> active planetary day is yesterday's.
        // If current time is at or after today's sunrise -> active planetary day is today's.
        val activeCal = (now.clone() as Calendar)
        val isBeforeSunrise = currentSecOfDay < todaySunriseSec
        if (isBeforeSunrise) {
            activeCal.add(Calendar.DAY_OF_MONTH, -1)
        }
        val activeDateStr = sdf.format(activeCal.time)

        if (_currentDateString.value != activeDateStr) {
            _currentDateString.value = activeDateStr
            calculateSuntimesFromCoordinates()
            recalculateAndRun()
        }
    }

    private var fetchSolarJob: Job? = null

    fun calculateOfflineSunriseSunsetForCurrentLocation(onComplete: ((DetailedSolarTimes?) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val times = SunriseSunsetHelper.calculateOfflineSolarTimesForCurrentLocation(
                    locationProvider = locationProvider,
                    targetDate = Calendar.getInstance(),
                    timeZoneId = TimeZone.getDefault().id
                )
                if (times != null) {
                    _detailedSolarTimes.value = times
                    _latitude.value = times.latitude
                    _longitude.value = times.longitude
                    _sunriseOverride.value = formatHoursToTimeString(times.sunriseHours)
                    _sunsetOverride.value = formatHoursToTimeString(times.sunsetHours)
                    _tomorrowSunriseOverride.value = formatHoursToTimeString(times.nextSunriseHours)
                    recalculateAndRun()
                    onComplete?.invoke(times)
                } else {
                    onComplete?.invoke(null)
                }
            } catch (_: Exception) {
                onComplete?.invoke(null)
            }
        }
    }

    private fun calculateSuntimesFromCoordinates() {
        val dateParts = _currentDateString.value.split("-")
        if (dateParts.size != 3) return
        val year = dateParts[0].toIntOrNull() ?: 2026
        val month = dateParts[1].toIntOrNull() ?: 6
        val day = dateParts[2].toIntOrNull() ?: 1

        val timezone = TimeZone.getDefault()
        val timeZoneId = timezone.id

        val targetCal = Calendar.getInstance(timezone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Step 1: Dynamic offline calculation via NOAA algorithm and SunriseSunsetCalculator
        val detailedTimes = SunriseSunsetHelper.calculateDetailedOfflineSolarTimes(
            latitude = _latitude.value,
            longitude = _longitude.value,
            timeZoneId = timeZoneId,
            targetDate = targetCal
        )

        _detailedSolarTimes.value = detailedTimes
        _sunriseOverride.value = formatHoursToTimeString(detailedTimes.sunriseHours)
        _sunsetOverride.value = formatHoursToTimeString(detailedTimes.sunsetHours)
        _tomorrowSunriseOverride.value = formatHoursToTimeString(detailedTimes.nextSunriseHours)
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

        // Step 2 & 4: Day ruler is the weekday of the anchor date
        val dowIndex = AstronomyEngine.getDayOfWeekIndex(year, month, day)
        val dayRulerName = AstronomyEngine.DAY_RULERS[dowIndex]
        val dayRulerSymbol = AstronomyEngine.PLANET_SYMBOLS[dayRulerName] ?: ""
        val dayName = AstronomyEngine.DAY_NAMES[dowIndex]

        // Step 3: Convert to continuous timeline (seconds since local midnight of anchor date)
        var sunriseSec = parseTimeToSeconds(_sunriseOverride.value)
        var sunsetSec = parseTimeToSeconds(_sunsetOverride.value)
        var tomorrowSunriseSec = parseTimeToSeconds(_tomorrowSunriseOverride.value) + 86400.0

        // Handle edge wraps
        if (sunsetSec <= sunriseSec) sunsetSec += 86400.0
        if (tomorrowSunriseSec <= sunsetSec) tomorrowSunriseSec += 86400.0

        // Step 4: Calculate 24 Planetary Hours (12 day + 12 night) in Chaldean order starting with day ruler
        val planetaryHours = AstronomyEngine.calculatePlanetaryHours(sunriseSec, sunsetSec, tomorrowSunriseSec, dowIndex)

        // Step 5: Calculate 60 Tattwa cycles dividing total span (sunrise to next sunrise)
        val tattvas = AstronomyEngine.calculateTattvas(sunriseSec, tomorrowSunriseSec)
        val combined = AstronomyEngine.calculateCombinedView(planetaryHours, tattvas)

        val dayLen = sunsetSec - sunriseSec
        val nightLen = tomorrowSunriseSec - sunsetSec
        val tattvaLen = (tomorrowSunriseSec - sunriseSec) / 60.0

        // Check if currently before today's sunrise in real time
        val now = Calendar.getInstance()
        val curSecOfDay = now.get(Calendar.HOUR_OF_DAY) * 3600.0 + now.get(Calendar.MINUTE) * 60.0 + now.get(Calendar.SECOND).toDouble()
        val isBeforeSunrise = !_isManualDateSelected.value && curSecOfDay < sunriseSec

        _calculationResults.value = CalculationResults(
            date = dateStr,
            dayOfWeekIndex = dowIndex,
            dayName = dayName,
            dayRulerName = dayRulerName,
            dayRulerSymbol = dayRulerSymbol,
            sunriseSeconds = sunriseSec,
            sunsetSeconds = sunsetSec,
            tomorrowSunriseSeconds = tomorrowSunriseSec,
            planetaryHours = planetaryHours,
            tattvas = tattvas,
            combined = combined,
            dayHourLengthSeconds = dayLen / 12.0,
            nightHourLengthSeconds = nightLen / 12.0,
            tattvaLengthSeconds = tattvaLen,
            isBeforeSunrise = isBeforeSunrise
        )

        // Force a clock tick evaluate
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

        // Step 6: Live-accurate check: If not manual, auto sync to active planetary day
        if (!_isManualDateSelected.value) {
            syncToActiveAstrologicalDay()
        }

        val calc = _calculationResults.value ?: return

        // Calculate elapsed seconds since midnight of anchor date (calc.date)
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

        // Step 6: If app stays open across sunrise, detect when real time passes next sunrise boundary
        if (!_isManualDateSelected.value && (curSec >= calc.tomorrowSunriseSeconds || curSec < calc.sunriseSeconds)) {
            syncToActiveAstrologicalDay()
            return
        }

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
            // Trigger haptic acknowledgment
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

    // Default export handler
    fun exportCsv(context: Context) {
        exportCompleteHistoryAndCyclesCsv(context, saveLocally = true) { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Format Utilities
    private fun parseTimeToSeconds(timeStr: String): Double {
        val parts = timeStr.split(":").map { it.toIntOrNull() ?: 0 }
        val hours = parts.getOrNull(0) ?: 0
        val mins = parts.getOrNull(1) ?: 0
        val secs = parts.getOrNull(2) ?: 0
        return hours * 3600.0 + mins * 60.0 + secs.toDouble()
    }

    private fun formatHoursToTimeString(hoursValue: Double): String {
        val totalSecs = (hoursValue * 3600.0).roundToInt()
        val h = (totalSecs / 3600) % 24
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }
}
