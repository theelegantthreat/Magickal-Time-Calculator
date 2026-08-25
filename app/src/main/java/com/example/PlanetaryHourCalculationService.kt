package com.example

import android.content.Context
import android.location.Location as AndroidLocation
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator
import com.luckycatlabs.sunrisesunset.dto.Location as SolarLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Detailed representation of the active planetary hour calculation result.
 */
data class PlanetaryHourResult(
    val currentHourNumber: Int,                 // 1 to 24 (1-12 Day, 13-24 Night)
    val planetName: String,                     // e.g., "Sun", "Venus", "Jupiter"
    val planetSymbol: String,                   // e.g., "☉", "♀", "♃"
    val planetColorHex: String,                 // Hex color for UI representation
    val planetDescription: String,             // Planetary properties / astrological attributes
    val isNight: Boolean,                       // True if in nocturnal period (after sunset)
    val astrologicalDayRuler: String,           // Ruler of the astrological day (starting at sunrise)
    val astrologicalDayName: String,            // Name of the day (e.g., "Sunday", "Monday")
    val astrologicalDate: Calendar,             // The calendar date anchor for this astrological cycle
    val hourStartMillis: Long,                  // Start timestamp in UTC millis
    val hourEndMillis: Long,                    // End timestamp in UTC millis
    val hourStartCalendar: Calendar,            // Start time as Calendar in local TimeZone
    val hourEndCalendar: Calendar,              // End time as Calendar in local TimeZone
    val hourDurationMillis: Long,               // Exact duration of this planetary hour in millis
    val elapsedMillis: Long,                    // Time elapsed within current hour
    val remainingMillis: Long,                  // Time remaining until next planetary hour
    val progress: Float,                        // Progress ratio within current hour (0.0f to 1.0f)
    val sunriseMillis: Long,                    // Sunrise timestamp of the astrological day
    val sunsetMillis: Long,                     // Sunset timestamp of the astrological day
    val nextSunriseMillis: Long,                // Next sunrise timestamp ending the 24-hour cycle
    val allHoursOfDay: List<AstronomyEngine.PlanetaryHour>, // All 24 calculated hours
    val coordinates: Pair<Double, Double>       // (Latitude, Longitude) used
) {
    val durationMinutes: Double get() = hourDurationMillis / 60000.0
    val remainingMinutes: Long get() = TimeUnit.MILLISECONDS.toMinutes(remainingMillis)
    val remainingSeconds: Long get() = TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60
}

/**
 * Service responsible for computing the active planetary hour and astrological cycles
 * using the SunriseSunsetCalculator library, based on device location and local time.
 */
class PlanetaryHourCalculationService(
    private val context: Context? = null,
    private val locationProvider: LocationProvider? = context?.let { LocationProvider(it) }
) {

    /**
     * Calculates the current planetary hour for the given coordinates and target time.
     *
     * @param latitude Device latitude in decimal degrees.
     * @param longitude Device longitude in decimal degrees.
     * @param targetTime Target time/date (defaults to current system time).
     * @param timeZone Target timezone (defaults to targetTime's timezone or system default).
     * @return [PlanetaryHourResult] with full details of the active planetary hour and 24-hour cycle.
     */
    fun calculateCurrentPlanetaryHour(
        latitude: Double,
        longitude: Double,
        targetTime: Calendar = Calendar.getInstance(),
        timeZone: TimeZone = targetTime.timeZone ?: TimeZone.getDefault()
    ): PlanetaryHourResult {
        val targetMillis = targetTime.timeInMillis
        val solarLocation = SolarLocation(latitude, longitude)
        val calculator = SunriseSunsetCalculator(solarLocation, timeZone)

        // Step 1: Determine the astrological day anchor.
        // In traditional astrology, the new planetary day starts at official sunrise.
        // If targetTime is before today's sunrise, the active day is yesterday's astrological day.
        val todayCal = (targetTime.clone() as Calendar).apply {
            this.timeZone = timeZone
        }

        val todaySunriseCal = calculator.getOfficialSunriseCalendarForDate(todayCal)
        val todaySunriseMillis = todaySunriseCal?.timeInMillis ?: fallbackSunriseMillis(todayCal)

        val isBeforeTodaySunrise = targetMillis < todaySunriseMillis

        val astroDateCal = (todayCal.clone() as Calendar).apply {
            if (isBeforeTodaySunrise) {
                add(Calendar.DAY_OF_YEAR, -1)
            }
        }

        // Step 2: Compute official sunrise and sunset for the active astrological day
        val astroSunriseCal = calculator.getOfficialSunriseCalendarForDate(astroDateCal)
        val astroSunsetCal = calculator.getOfficialSunsetCalendarForDate(astroDateCal)

        val sunriseMillis = astroSunriseCal?.timeInMillis ?: fallbackSunriseMillis(astroDateCal)
        var sunsetMillis = astroSunsetCal?.timeInMillis ?: fallbackSunsetMillis(astroDateCal)

        // Step 3: Compute next day's sunrise (completing the 24 planetary hour cycle)
        val nextDayCal = (astroDateCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val nextSunriseCal = calculator.getOfficialSunriseCalendarForDate(nextDayCal)
        var nextSunriseMillis = nextSunriseCal?.timeInMillis ?: fallbackSunriseMillis(nextDayCal)

        // Sanity adjustments for polar edge cases or inverted order
        if (sunsetMillis <= sunriseMillis) {
            sunsetMillis = sunriseMillis + 12 * 3600 * 1000L
        }
        if (nextSunriseMillis <= sunsetMillis) {
            nextSunriseMillis = sunsetMillis + 12 * 3600 * 1000L
        }

        // Step 4: Determine Day of Week & Chaldean Day Ruler
        // Calendar.SUNDAY = 1, Astro dayOfWeekIndex: Sunday = 0, Monday = 1, ... Saturday = 6
        val dowIndex = (astroDateCal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7
        val dayRulerPlanet = AstronomyEngine.DAY_RULERS[dowIndex]
        val dayName = AstronomyEngine.DAY_NAMES[dowIndex]

        // Start index in Chaldean order (Saturn, Jupiter, Mars, Sun, Venus, Mercury, Moon)
        val startPlanetIndex = AstronomyEngine.PLANET_ORDER.indexOf(dayRulerPlanet).let { if (it >= 0) it else 0 }

        // Step 5: Calculate daytime hour duration (12 equal parts) and nighttime hour duration (12 equal parts)
        val dayTotalDurationMillis = sunsetMillis - sunriseMillis
        val nightTotalDurationMillis = nextSunriseMillis - sunsetMillis

        val dayHourDurationMillis = dayTotalDurationMillis / 12.0
        val nightHourDurationMillis = nightTotalDurationMillis / 12.0

        val all24Hours = mutableListOf<AstronomyEngine.PlanetaryHour>()

        // Build 12 Daytime Hours
        val baseSunriseSec = (astroDateCal.get(Calendar.HOUR_OF_DAY) * 3600.0) // reference basis
        val sunriseHourLocal = getLocalHourFraction(sunriseMillis, timeZone)
        val sunsetHourLocal = getLocalHourFraction(sunsetMillis, timeZone)
        val nextSunriseHourLocal = getLocalHourFraction(nextSunriseMillis, timeZone)

        var sunriseSec = sunriseHourLocal * 3600.0
        var sunsetSec = sunsetHourLocal * 3600.0
        var nextSunriseSec = nextSunriseHourLocal * 3600.0 + 86400.0
        if (sunsetSec <= sunriseSec) sunsetSec += 86400.0
        if (nextSunriseSec <= sunsetSec) nextSunriseSec += 86400.0

        val dayHourSec = (sunsetSec - sunriseSec) / 12.0
        val nightHourSec = (nextSunriseSec - sunsetSec) / 12.0

        for (i in 0 until 12) {
            val planetIdx = (startPlanetIndex + i) % 7
            val pName = AstronomyEngine.PLANET_ORDER[planetIdx]
            all24Hours.add(
                AstronomyEngine.PlanetaryHour(
                    number = i + 1,
                    planetName = pName,
                    planetSymbol = AstronomyEngine.PLANET_SYMBOLS[pName] ?: "",
                    startSecondOfDay = sunriseSec + i * dayHourSec,
                    endSecondOfDay = sunriseSec + (i + 1) * dayHourSec,
                    isNight = false,
                    colorHex = AstronomyEngine.PLANET_COLORS[pName] ?: "#FFFFFF"
                )
            )
        }

        // Build 12 Nighttime Hours
        val nightStartPlanetIdx = (startPlanetIndex + 12) % 7
        for (i in 0 until 12) {
            val planetIdx = (nightStartPlanetIdx + i) % 7
            val pName = AstronomyEngine.PLANET_ORDER[planetIdx]
            all24Hours.add(
                AstronomyEngine.PlanetaryHour(
                    number = i + 13,
                    planetName = pName,
                    planetSymbol = AstronomyEngine.PLANET_SYMBOLS[pName] ?: "",
                    startSecondOfDay = sunsetSec + i * nightHourSec,
                    endSecondOfDay = sunsetSec + (i + 1) * nightHourSec,
                    isNight = true,
                    colorHex = AstronomyEngine.PLANET_COLORS[pName] ?: "#FFFFFF"
                )
            )
        }

        // Step 6: Identify which of the 24 hours matches targetMillis
        val isNightPeriod = targetMillis >= sunsetMillis
        val hourNumber: Int
        val activePlanetName: String
        val activeStartMillis: Long
        val activeEndMillis: Long
        val activeHourDurationMillis: Long

        if (!isNightPeriod) {
            // Day hour (1 to 12)
            val elapsedInDay = (targetMillis - sunriseMillis).coerceAtLeast(0L)
            val index = (elapsedInDay / dayHourDurationMillis).toInt().coerceIn(0, 11)
            hourNumber = index + 1
            val planetIdx = (startPlanetIndex + index) % 7
            activePlanetName = AstronomyEngine.PLANET_ORDER[planetIdx]
            activeStartMillis = (sunriseMillis + index * dayHourDurationMillis).toLong()
            activeEndMillis = (sunriseMillis + (index + 1) * dayHourDurationMillis).toLong()
            activeHourDurationMillis = (activeEndMillis - activeStartMillis).coerceAtLeast(1L)
        } else {
            // Night hour (13 to 24)
            val elapsedInNight = (targetMillis - sunsetMillis).coerceAtLeast(0L)
            val index = (elapsedInNight / nightHourDurationMillis).toInt().coerceIn(0, 11)
            hourNumber = index + 13
            val planetIdx = (nightStartPlanetIdx + index) % 7
            activePlanetName = AstronomyEngine.PLANET_ORDER[planetIdx]
            activeStartMillis = (sunsetMillis + index * nightHourDurationMillis).toLong()
            activeEndMillis = (sunsetMillis + (index + 1) * nightHourDurationMillis).toLong()
            activeHourDurationMillis = (activeEndMillis - activeStartMillis).coerceAtLeast(1L)
        }

        val elapsedMillis = (targetMillis - activeStartMillis).coerceIn(0L, activeHourDurationMillis)
        val remainingMillis = (activeEndMillis - targetMillis).coerceAtLeast(0L)
        val progress = (elapsedMillis.toDouble() / activeHourDurationMillis.toDouble()).toFloat().coerceIn(0f, 1f)

        val startCal = Calendar.getInstance(timeZone).apply { timeInMillis = activeStartMillis }
        val endCal = Calendar.getInstance(timeZone).apply { timeInMillis = activeEndMillis }

        return PlanetaryHourResult(
            currentHourNumber = hourNumber,
            planetName = activePlanetName,
            planetSymbol = AstronomyEngine.PLANET_SYMBOLS[activePlanetName] ?: "",
            planetColorHex = AstronomyEngine.PLANET_COLORS[activePlanetName] ?: "#FFFFFF",
            planetDescription = AstronomyEngine.PLANET_DESCRIPTIONS[activePlanetName] ?: "",
            isNight = isNightPeriod,
            astrologicalDayRuler = dayRulerPlanet,
            astrologicalDayName = dayName,
            astrologicalDate = astroDateCal,
            hourStartMillis = activeStartMillis,
            hourEndMillis = activeEndMillis,
            hourStartCalendar = startCal,
            hourEndCalendar = endCal,
            hourDurationMillis = activeHourDurationMillis,
            elapsedMillis = elapsedMillis,
            remainingMillis = remainingMillis,
            progress = progress,
            sunriseMillis = sunriseMillis,
            sunsetMillis = sunsetMillis,
            nextSunriseMillis = nextSunriseMillis,
            allHoursOfDay = all24Hours,
            coordinates = Pair(latitude, longitude)
        )
    }

    /**
     * Calculates current planetary hour using an Android [AndroidLocation] and optional target time.
     */
    fun calculateFromAndroidLocation(
        location: AndroidLocation,
        targetTime: Calendar = Calendar.getInstance(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): PlanetaryHourResult {
        return calculateCurrentPlanetaryHour(
            latitude = location.latitude,
            longitude = location.longitude,
            targetTime = targetTime,
            timeZone = timeZone
        )
    }

    /**
     * Calculates current planetary hour using [UserLocation].
     */
    fun calculateFromUserLocation(
        userLocation: UserLocation,
        targetTime: Calendar = Calendar.getInstance(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): PlanetaryHourResult {
        return calculateCurrentPlanetaryHour(
            latitude = userLocation.latitude,
            longitude = userLocation.longitude,
            targetTime = targetTime,
            timeZone = timeZone
        )
    }

    /**
     * Automatically acquires the device's current location via [LocationProvider]
     * and calculates the current planetary hour for the current moment.
     *
     * @return [Result] containing [PlanetaryHourResult] on success, or exception on failure/permission denial.
     */
    suspend fun calculateForDeviceLocation(
        targetTime: Calendar = Calendar.getInstance(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Result<PlanetaryHourResult> = withContext(Dispatchers.IO) {
        val provider = locationProvider
            ?: return@withContext Result.failure(IllegalStateException("LocationProvider is not initialized (no Context provided)"))

        if (!provider.hasLocationPermission()) {
            return@withContext Result.failure(SecurityException("Location permission (FINE or COARSE) is required"))
        }

        val userLocation = provider.fetchCurrentLocation()
            ?: return@withContext Result.failure(IllegalStateException("Failed to acquire GPS coordinates from FusedLocationProviderClient"))

        val result = calculateCurrentPlanetaryHour(
            latitude = userLocation.latitude,
            longitude = userLocation.longitude,
            targetTime = targetTime,
            timeZone = timeZone
        )

        Result.success(result)
    }

    /**
     * Provides a continuous cold [Flow] that periodically recalculates the planetary hour
     * at the given interval (default 1000ms / 1 second) for real-time tracking and UI clocks.
     */
    fun observeCurrentPlanetaryHour(
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone = TimeZone.getDefault(),
        intervalMillis: Long = 1000L
    ): Flow<PlanetaryHourResult> = flow {
        while (true) {
            val result = calculateCurrentPlanetaryHour(
                latitude = latitude,
                longitude = longitude,
                targetTime = Calendar.getInstance(timeZone),
                timeZone = timeZone
            )
            emit(result)
            delay(intervalMillis)
        }
    }.flowOn(Dispatchers.Default)

    private fun getLocalHourFraction(timeMillis: Long, timeZone: TimeZone): Double {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = timeMillis }
        return cal.get(Calendar.HOUR_OF_DAY) +
                cal.get(Calendar.MINUTE) / 60.0 +
                cal.get(Calendar.SECOND) / 3600.0 +
                cal.get(Calendar.MILLISECOND) / 3600000.0
    }

    private fun fallbackSunriseMillis(cal: Calendar): Long {
        val fallback = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return fallback.timeInMillis
    }

    private fun fallbackSunsetMillis(cal: Calendar): Long {
        val fallback = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return fallback.timeInMillis
    }
}
