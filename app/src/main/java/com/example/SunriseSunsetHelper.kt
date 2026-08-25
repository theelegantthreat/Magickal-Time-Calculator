package com.example

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator
import com.luckycatlabs.sunrisesunset.dto.Location
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Encapsulates official sunrise, sunset, and next-sunrise timestamps and local hour values.
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
 * Reusable utility for calculating dynamic sunrise and sunset times offline
 * using the com.luckycatlabs:SunriseSunsetCalculator library.
 */
object SunriseSunsetHelper {

    /**
     * Requirement 1 & 2: Reusable utility function that accepts local coordinates (latitude, longitude),
     * a timezone ID, and a target date, and finds exact official sunrise and sunset milliseconds.
     *
     * @param latitude Target latitude in decimal degrees (-90.0 to 90.0).
     * @param longitude Target longitude in decimal degrees (-180.0 to 180.0).
     * @param timeZoneId Local TimeZone ID (e.g., "America/New_York", "UTC", TimeZone.getDefault().id).
     * @param targetDate Target Calendar object representing the day.
     * @return [SolarDayTimes] with exact milliseconds and local hours.
     */
    fun calculateSolarTimes(
        latitude: Double,
        longitude: Double,
        timeZoneId: String,
        targetDate: Calendar
    ): SolarDayTimes {
        val location = Location(latitude, longitude)
        val tz = TimeZone.getTimeZone(timeZoneId)
        val calculator = SunriseSunsetCalculator(location, tz)

        // Date anchor
        val curDayCal = (targetDate.clone() as Calendar).apply {
            timeZone = tz
        }

        // Official sunrise and sunset for the target date
        val sunriseCal = calculator.getOfficialSunriseCalendarForDate(curDayCal)
        val sunsetCal = calculator.getOfficialSunsetCalendarForDate(curDayCal)

        val sunriseMillis = sunriseCal?.timeInMillis ?: fallbackSunriseMillis(curDayCal)
        val sunsetMillis = sunsetCal?.timeInMillis ?: fallbackSunsetMillis(curDayCal)

        val finalSunriseCal = Calendar.getInstance(tz).apply { timeInMillis = sunriseMillis }
        val finalSunsetCal = Calendar.getInstance(tz).apply { timeInMillis = sunsetMillis }

        // Next day sunrise for completing the nocturnal frame (Hours 13-24)
        val nextDayCal = (curDayCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val nextSunriseCal = calculator.getOfficialSunriseCalendarForDate(nextDayCal)
        val nextSunriseMillis = nextSunriseCal?.timeInMillis ?: fallbackSunriseMillis(nextDayCal)
        val finalNextSunriseCal = Calendar.getInstance(tz).apply { timeInMillis = nextSunriseMillis }

        val sunriseHours = finalSunriseCal.get(Calendar.HOUR_OF_DAY) +
                finalSunriseCal.get(Calendar.MINUTE) / 60.0 +
                finalSunriseCal.get(Calendar.SECOND) / 3600.0 +
                finalSunriseCal.get(Calendar.MILLISECOND) / 3600000.0

        val sunsetHours = finalSunsetCal.get(Calendar.HOUR_OF_DAY) +
                finalSunsetCal.get(Calendar.MINUTE) / 60.0 +
                finalSunsetCal.get(Calendar.SECOND) / 3600.0 +
                finalSunsetCal.get(Calendar.MILLISECOND) / 3600000.0

        val nextSunriseHours = finalNextSunriseCal.get(Calendar.HOUR_OF_DAY) +
                finalNextSunriseCal.get(Calendar.MINUTE) / 60.0 +
                finalNextSunriseCal.get(Calendar.SECOND) / 3600.0 +
                finalNextSunriseCal.get(Calendar.MILLISECOND) / 3600000.0

        return SolarDayTimes(
            sunriseMillis = sunriseMillis,
            sunsetMillis = sunsetMillis,
            nextSunriseMillis = nextSunriseMillis,
            sunriseHours = sunriseHours,
            sunsetHours = sunsetHours,
            nextSunriseHours = nextSunriseHours,
            sunriseCalendar = finalSunriseCal,
            sunsetCalendar = finalSunsetCal,
            nextSunriseCalendar = finalNextSunriseCal
        )
    }

    /**
     * Overload accepting Date and timeZoneId.
     */
    fun calculateSolarTimes(
        latitude: Double,
        longitude: Double,
        timeZoneId: String,
        targetDate: Date
    ): SolarDayTimes {
        val cal = Calendar.getInstance(TimeZone.getTimeZone(timeZoneId)).apply {
            time = targetDate
        }
        return calculateSolarTimes(latitude, longitude, timeZoneId, cal)
    }

    /**
     * Overload accepting year, month (1-12), day (1-31).
     */
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
     * Requirement 4: Calculates 24 Planetary Hours (12 equal daytime hours + 12 equal nighttime hours)
     * using the dynamically computed timestamps from SunriseSunsetCalculator.
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

