package com.example

import android.content.Context
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator
import com.luckycatlabs.sunrisesunset.dto.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.*

/**
 * Detailed offline solar calculation result containing comprehensive astronomical markers.
 */
data class DetailedSolarTimes(
    val sunriseMillis: Long,
    val sunsetMillis: Long,
    val nextSunriseMillis: Long,
    val solarNoonMillis: Long,
    val nadirMillis: Long,
    val civilDawnMillis: Long,
    val civilDuskMillis: Long,
    val nauticalDawnMillis: Long,
    val nauticalDuskMillis: Long,
    val astronomicalDawnMillis: Long,
    val astronomicalDuskMillis: Long,
    val goldenHourMorningStartMillis: Long,
    val goldenHourMorningEndMillis: Long,
    val goldenHourEveningStartMillis: Long,
    val goldenHourEveningEndMillis: Long,
    val dayLengthMillis: Long,
    val nightLengthMillis: Long,
    val sunriseHours: Double,
    val sunsetHours: Double,
    val nextSunriseHours: Double,
    val solarNoonHours: Double,
    val solarElevationDegrees: Double,
    val solarAzimuthDegrees: Double,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
    val isPolarDay: Boolean = false,
    val isPolarNight: Boolean = false,
    val calculationSource: String = "NOAA Astronomical Algorithm (Offline)"
) {
    val dayLengthFormatted: String get() {
        val hours = TimeUnit.MILLISECONDS.toHours(dayLengthMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(dayLengthMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(dayLengthMillis) % 60
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds)
    }

    val nightLengthFormatted: String get() {
        val hours = TimeUnit.MILLISECONDS.toHours(nightLengthMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(nightLengthMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(nightLengthMillis) % 60
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds)
    }
}

/**
 * Backward-compatible wrapper for basic solar times.
 */
data class SolarDayTimes(
    val sunriseMillis: Long,
    val sunsetMillis: Long,
    val nextSunriseMillis: Long,
    val sunriseHours: Double,
    val sunsetHours: Double,
    val nextSunriseHours: Double,
    val sunriseCalendar: Calendar,
    val sunsetCalendar: Calendar,
    val nextSunriseCalendar: Calendar
)

/**
 * Reusable utility and standalone offline calculation engine for computing
 * sunrise, sunset, twilights, solar noon, and astronomical cycles using
 * device coordinates and local time without requiring an internet connection.
 */
object SunriseSunsetHelper {

    /**
     * Calculates comprehensive solar times offline using the NOAA solar calculations
     * and Jean Meeus astronomical algorithms.
     *
     * @param latitude Target latitude in decimal degrees.
     * @param longitude Target longitude in decimal degrees.
     * @param timeZoneId Target TimeZone ID.
     * @param targetDate Target Calendar representing the date.
     * @return [DetailedSolarTimes] with full astronomical markers.
     */
    fun calculateDetailedOfflineSolarTimes(
        latitude: Double,
        longitude: Double,
        timeZoneId: String,
        targetDate: Calendar = Calendar.getInstance()
    ): DetailedSolarTimes {
        val tz = TimeZone.getTimeZone(timeZoneId)
        val cal = (targetDate.clone() as Calendar).apply { timeZone = tz }

        // Primary calculation via NOAA Offline Algorithm
        val noaaTimes = calculateNOAASolarTimes(latitude, longitude, tz, cal)

        // Cross-verify with SunriseSunsetCalculator library
        val location = Location(latitude, longitude)
        val calculator = SunriseSunsetCalculator(location, tz)

        val libSunriseCal = calculator.getOfficialSunriseCalendarForDate(cal)
        val libSunsetCal = calculator.getOfficialSunsetCalendarForDate(cal)

        val sunriseMillis = libSunriseCal?.timeInMillis ?: noaaTimes.sunriseMillis
        val sunsetMillis = libSunsetCal?.timeInMillis ?: noaaTimes.sunsetMillis

        // Next Day Sunrise
        val nextDayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val libNextSunriseCal = calculator.getOfficialSunriseCalendarForDate(nextDayCal)
        val nextSunriseMillis = libNextSunriseCal?.timeInMillis ?: (sunriseMillis + 86400000L)

        // Day and Night durations
        val safeSunsetMillis = if (sunsetMillis <= sunriseMillis) sunriseMillis + 12 * 3600000L else sunsetMillis
        val safeNextSunriseMillis = if (nextSunriseMillis <= safeSunsetMillis) safeSunsetMillis + 12 * 3600000L else nextSunriseMillis
        val dayLengthMillis = (safeSunsetMillis - sunriseMillis).coerceAtLeast(0L)
        val nightLengthMillis = (safeNextSunriseMillis - safeSunsetMillis).coerceAtLeast(0L)

        val sunriseHours = getHoursFromMillis(sunriseMillis, tz)
        val sunsetHours = getHoursFromMillis(safeSunsetMillis, tz)
        val nextSunriseHours = getHoursFromMillis(safeNextSunriseMillis, tz)
        val solarNoonHours = getHoursFromMillis(noaaTimes.solarNoonMillis, tz)

        // Current solar position
        val currentCal = Calendar.getInstance(tz)
        val (elevation, azimuth) = calculateSolarPosition(latitude, longitude, currentCal)

        return DetailedSolarTimes(
            sunriseMillis = sunriseMillis,
            sunsetMillis = safeSunsetMillis,
            nextSunriseMillis = safeNextSunriseMillis,
            solarNoonMillis = noaaTimes.solarNoonMillis,
            nadirMillis = noaaTimes.nadirMillis,
            civilDawnMillis = noaaTimes.civilDawnMillis,
            civilDuskMillis = noaaTimes.civilDuskMillis,
            nauticalDawnMillis = noaaTimes.nauticalDawnMillis,
            nauticalDuskMillis = noaaTimes.nauticalDuskMillis,
            astronomicalDawnMillis = noaaTimes.astronomicalDawnMillis,
            astronomicalDuskMillis = noaaTimes.astronomicalDuskMillis,
            goldenHourMorningStartMillis = sunriseMillis,
            goldenHourMorningEndMillis = sunriseMillis + 3600000L,
            goldenHourEveningStartMillis = safeSunsetMillis - 3600000L,
            goldenHourEveningEndMillis = safeSunsetMillis,
            dayLengthMillis = dayLengthMillis,
            nightLengthMillis = nightLengthMillis,
            sunriseHours = sunriseHours,
            sunsetHours = sunsetHours,
            nextSunriseHours = nextSunriseHours,
            solarNoonHours = solarNoonHours,
            solarElevationDegrees = elevation,
            solarAzimuthDegrees = azimuth,
            latitude = latitude,
            longitude = longitude,
            timeZoneId = timeZoneId,
            calculationSource = "NOAA Offline Equations & SunriseSunsetCalculator"
        )
    }

    /**
     * Offline calculation method utilizing the device's current location via [LocationProvider].
     */
    suspend fun calculateOfflineSolarTimesForCurrentLocation(
        locationProvider: LocationProvider,
        targetDate: Calendar = Calendar.getInstance(),
        timeZoneId: String = TimeZone.getDefault().id
    ): DetailedSolarTimes? = withContext(Dispatchers.IO) {
        val userLoc = locationProvider.fetchCurrentLocation() ?: return@withContext null
        calculateDetailedOfflineSolarTimes(
            latitude = userLoc.latitude,
            longitude = userLoc.longitude,
            timeZoneId = timeZoneId,
            targetDate = targetDate
        )
    }

    /**
     * Standalone pure Kotlin offline implementation of NOAA Solar Calculations.
     */
    fun calculateNOAASolarTimes(
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone,
        calendar: Calendar
    ): NOAASolarResult {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val julianDay = calculateJulianDay(year, month, day)
        val julianCentury = (julianDay - 2451545.0) / 36525.0

        // Geom Mean Long Sun (deg)
        var geomMeanLongSun = (280.46646 + julianCentury * (36000.76983 + julianCentury * 0.0003032)) % 360.0
        if (geomMeanLongSun < 0) geomMeanLongSun += 360.0

        // Geom Mean Anom Sun (deg)
        val geomMeanAnomSun = 357.52911 + julianCentury * (35999.05029 - 0.0001537 * julianCentury)

        // Eccent Earth Orbit
        val eccentEarthOrbit = 0.016708634 - julianCentury * (0.000042037 + 0.0000001267 * julianCentury)

        // Sun Eq of Ctr
        val sunEqOfCtr = sin(Math.toRadians(geomMeanAnomSun)) * (1.914602 - julianCentury * (0.004817 + 0.000014 * julianCentury)) +
                sin(Math.toRadians(2.0 * geomMeanAnomSun)) * (0.019993 - 0.000101 * julianCentury) +
                sin(Math.toRadians(3.0 * geomMeanAnomSun)) * 0.000289

        // Sun True Long (deg) & Sun True Anom (deg)
        val sunTrueLong = geomMeanLongSun + sunEqOfCtr

        // Sun App Long (deg)
        val sunAppLong = sunTrueLong - 0.00569 - 0.00478 * sin(Math.toRadians(125.04 - 1934.136 * julianCentury))

        // Mean Obliq Ecliptic (deg) & Obliq Corr (deg)
        val meanObliqEcliptic = 23.0 + (26.0 + ((21.448 - julianCentury * (46.815 + julianCentury * (0.00059 - julianCentury * 0.001813)))) / 60.0) / 60.0
        val obliqCorr = meanObliqEcliptic + 0.00256 * cos(Math.toRadians(125.04 - 1934.136 * julianCentury))

        // Sun Declination (deg)
        val sunDeclin = Math.toDegrees(asin(sin(Math.toRadians(obliqCorr)) * sin(Math.toRadians(sunAppLong))))

        // Var y
        val varY = tan(Math.toRadians(obliqCorr / 2.0)).pow(2)

        // Eq of Time (minutes)
        val eqOfTime = 4.0 * Math.toDegrees(
            varY * sin(2.0 * Math.toRadians(geomMeanLongSun)) -
                    2.0 * eccentEarthOrbit * sin(Math.toRadians(geomMeanAnomSun)) +
                    4.0 * eccentEarthOrbit * varY * sin(Math.toRadians(geomMeanAnomSun)) * cos(2.0 * Math.toRadians(geomMeanLongSun)) -
                    0.5 * varY.pow(2) * sin(4.0 * Math.toRadians(geomMeanLongSun)) -
                    1.25 * eccentEarthOrbit.pow(2) * sin(2.0 * Math.toRadians(geomMeanAnomSun))
        )

        // Solar Noon in local minutes from midnight
        val tzOffsetHours = timeZone.getOffset(calendar.timeInMillis) / 3600000.0
        val solarNoonMinutes = (720.0 - 4.0 * longitude - eqOfTime + tzOffsetHours * 60.0)

        // Hour Angle for Official Sunrise/Sunset (zenith = 90.833 deg)
        val haOfficial = calculateHourAngle(latitude, sunDeclin, 90.8333)
        val haCivil = calculateHourAngle(latitude, sunDeclin, 96.0)
        val haNautical = calculateHourAngle(latitude, sunDeclin, 102.0)
        val haAstro = calculateHourAngle(latitude, sunDeclin, 108.0)

        val baseCal = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val midnightMillis = baseCal.timeInMillis

        fun minutesToMillis(minutes: Double): Long = midnightMillis + (minutes * 60.0 * 1000.0).toLong()

        val sunriseMin = if (haOfficial != null) solarNoonMinutes - haOfficial * 4.0 else solarNoonMinutes - 360.0
        val sunsetMin = if (haOfficial != null) solarNoonMinutes + haOfficial * 4.0 else solarNoonMinutes + 360.0

        val civilDawnMin = if (haCivil != null) solarNoonMinutes - haCivil * 4.0 else sunriseMin - 30.0
        val civilDuskMin = if (haCivil != null) solarNoonMinutes + haCivil * 4.0 else sunsetMin + 30.0

        val nautDawnMin = if (haNautical != null) solarNoonMinutes - haNautical * 4.0 else sunriseMin - 60.0
        val nautDuskMin = if (haNautical != null) solarNoonMinutes + haNautical * 4.0 else sunsetMin + 60.0

        val astroDawnMin = if (haAstro != null) solarNoonMinutes - haAstro * 4.0 else sunriseMin - 90.0
        val astroDuskMin = if (haAstro != null) solarNoonMinutes + haAstro * 4.0 else sunsetMin + 90.0

        return NOAASolarResult(
            sunriseMillis = minutesToMillis(sunriseMin),
            sunsetMillis = minutesToMillis(sunsetMin),
            solarNoonMillis = minutesToMillis(solarNoonMinutes),
            nadirMillis = minutesToMillis(solarNoonMinutes + 720.0),
            civilDawnMillis = minutesToMillis(civilDawnMin),
            civilDuskMillis = minutesToMillis(civilDuskMin),
            nauticalDawnMillis = minutesToMillis(nautDawnMin),
            nauticalDuskMillis = minutesToMillis(nautDuskMin),
            astronomicalDawnMillis = minutesToMillis(astroDawnMin),
            astronomicalDuskMillis = minutesToMillis(astroDuskMin),
            solarDeclination = sunDeclin,
            equationOfTimeMinutes = eqOfTime
        )
    }

    private fun calculateHourAngle(lat: Double, declin: Double, zenith: Double): Double? {
        val latRad = Math.toRadians(lat)
        val declinRad = Math.toRadians(declin)
        val zenithRad = Math.toRadians(zenith)

        val cosHA = (cos(zenithRad) - sin(latRad) * sin(declinRad)) / (cos(latRad) * cos(declinRad))
        return if (cosHA > 1.0 || cosHA < -1.0) null else Math.toDegrees(acos(cosHA))
    }

    private fun calculateJulianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /**
     * Computes the current Solar Elevation and Azimuth angle offline.
     */
    fun calculateSolarPosition(
        lat: Double,
        lon: Double,
        calendar: Calendar = Calendar.getInstance()
    ): Pair<Double, Double> {
        val tz = calendar.timeZone
        val noaa = calculateNOAASolarTimes(lat, lon, tz, calendar)

        val currentMinOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60.0 +
                calendar.get(Calendar.MINUTE) +
                calendar.get(Calendar.SECOND) / 60.0

        val tzOffsetHours = tz.getOffset(calendar.timeInMillis) / 3600000.0
        val trueSolarTimeMin = (currentMinOfDay + noaa.equationOfTimeMinutes + 4.0 * lon - 60.0 * tzOffsetHours) % 1440.0

        val hourAngleDeg = if (trueSolarTimeMin / 4.0 < 0) trueSolarTimeMin / 4.0 + 180 else trueSolarTimeMin / 4.0 - 180
        val latRad = Math.toRadians(lat)
        val declinRad = Math.toRadians(noaa.solarDeclination)
        val haRad = Math.toRadians(hourAngleDeg)

        // Solar Zenith & Elevation
        val sinElev = sin(latRad) * sin(declinRad) + cos(latRad) * cos(declinRad) * cos(haRad)
        val elevationDeg = Math.toDegrees(asin(sinElev.coerceIn(-1.0, 1.0)))

        // Solar Azimuth
        val cosAzimuth = (sin(declinRad) - sin(latRad) * sin(Math.toRadians(elevationDeg))) /
                (cos(latRad) * cos(Math.toRadians(elevationDeg)))
        val rawAzimuth = Math.toDegrees(acos(cosAzimuth.coerceIn(-1.0, 1.0)))
        val azimuthDeg = if (hourAngleDeg > 0) (360.0 - rawAzimuth) % 360.0 else rawAzimuth

        return Pair(elevationDeg, azimuthDeg)
    }

    /**
     * Returns standard SolarDayTimes for the given coordinates and date.
     */
    fun calculateSolarTimes(
        latitude: Double,
        longitude: Double,
        timeZoneId: String,
        targetDate: Calendar
    ): SolarDayTimes {
        val detailed = calculateDetailedOfflineSolarTimes(latitude, longitude, timeZoneId, targetDate)
        val tz = TimeZone.getTimeZone(timeZoneId)

        val sunriseCal = Calendar.getInstance(tz).apply { timeInMillis = detailed.sunriseMillis }
        val sunsetCal = Calendar.getInstance(tz).apply { timeInMillis = detailed.sunsetMillis }
        val nextSunriseCal = Calendar.getInstance(tz).apply { timeInMillis = detailed.nextSunriseMillis }

        return SolarDayTimes(
            sunriseMillis = detailed.sunriseMillis,
            sunsetMillis = detailed.sunsetMillis,
            nextSunriseMillis = detailed.nextSunriseMillis,
            sunriseHours = detailed.sunriseHours,
            sunsetHours = detailed.sunsetHours,
            nextSunriseHours = detailed.nextSunriseHours,
            sunriseCalendar = sunriseCal,
            sunsetCalendar = sunsetCal,
            nextSunriseCalendar = nextSunriseCal
        )
    }

    fun calculateSolarTimes(
        latitude: Double,
        longitude: Double,
        timeZoneId: String,
        year: Int,
        month: Int,
        day: Int
    ): SolarDayTimes {
        val tz = TimeZone.getTimeZone(timeZoneId)
        val cal = Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calculateSolarTimes(latitude, longitude, timeZoneId, cal)
    }

    /**
     * Calculates 24 Planetary Hours (12 equal daytime hours + 12 equal nighttime hours)
     * using the dynamically computed timestamps.
     */
    fun calculatePlanetaryHoursForDate(
        latitude: Double,
        longitude: Double,
        timeZoneId: String,
        targetDate: Calendar
    ): List<AstronomyEngine.PlanetaryHour> {
        val solarTimes = calculateSolarTimes(latitude, longitude, timeZoneId, targetDate)
        val dowIndex = (targetDate.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7

        // Express continuously in seconds since local midnight of targetDate
        val sunriseSec = solarTimes.sunriseHours * 3600.0
        var sunsetSec = solarTimes.sunsetHours * 3600.0
        var nextSunriseSec = (solarTimes.nextSunriseHours * 3600.0) + 86400.0

        if (sunsetSec <= sunriseSec) sunsetSec += 86400.0
        if (nextSunriseSec <= sunsetSec) nextSunriseSec += 86400.0

        return AstronomyEngine.calculatePlanetaryHours(
            sunriseSec = sunriseSec,
            sunsetSec = sunsetSec,
            tomorrowSunriseSec = nextSunriseSec,
            dayOfWeekIndex = dowIndex
        )
    }

    fun getHoursFromMillis(timeMillis: Long, timeZone: TimeZone): Double {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = timeMillis }
        return cal.get(Calendar.HOUR_OF_DAY) +
                cal.get(Calendar.MINUTE) / 60.0 +
                cal.get(Calendar.SECOND) / 3600.0 +
                cal.get(Calendar.MILLISECOND) / 3600000.0
    }

    fun fallbackSunriseMillis(cal: Calendar): Long {
        val fallback = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return fallback.timeInMillis
    }

    fun fallbackSunsetMillis(cal: Calendar): Long {
        val fallback = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return fallback.timeInMillis
    }
}

data class NOAASolarResult(
    val sunriseMillis: Long,
    val sunsetMillis: Long,
    val solarNoonMillis: Long,
    val nadirMillis: Long,
    val civilDawnMillis: Long,
    val civilDuskMillis: Long,
    val nauticalDawnMillis: Long,
    val nauticalDuskMillis: Long,
    val astronomicalDawnMillis: Long,
    val astronomicalDuskMillis: Long,
    val solarDeclination: Double,
    val equationOfTimeMinutes: Double
)


