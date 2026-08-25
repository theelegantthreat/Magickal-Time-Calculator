package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AstroChronos", appName)
  }

  @Test
  fun `calculate dynamic sunrise and sunset using SunriseSunsetHelper`() {
    // New York coordinates
    val lat = 40.7128
    val lon = -74.0060
    val tz = "America/New_York"
    val cal = Calendar.getInstance(TimeZone.getTimeZone(tz)).apply {
      set(2026, Calendar.JUNE, 21, 12, 0, 0)
    }

    val solar = SunriseSunsetHelper.calculateSolarTimes(lat, lon, tz, cal)

    // Sunrise should be around 5:25 AM local time (approx 5.4 hours)
    // Sunset should be around 8:30 PM local time (approx 20.5 hours)
    assertTrue("Sunrise should be between 4:00 and 7:00", solar.sunriseHours in 4.0..7.0)
    assertTrue("Sunset should be between 19.0 and 22.0", solar.sunsetHours in 19.0..22.0)
    assertTrue("Sunrise millis should be before Sunset millis", solar.sunriseMillis < solar.sunsetMillis)
    assertTrue("Sunset millis should be before Next Sunrise millis", solar.sunsetMillis < solar.nextSunriseMillis)
  }

  @Test
  fun `calculate planetary hours dynamically divides day and night into 12 equal hours`() {
    val lat = 51.5074 // London
    val lon = -0.1278
    val tz = "Europe/London"
    val cal = Calendar.getInstance(TimeZone.getTimeZone(tz)).apply {
      set(2026, Calendar.MARCH, 20, 12, 0, 0) // Equinox
    }

    val hours = SunriseSunsetHelper.calculatePlanetaryHoursForDate(lat, lon, tz, cal)
    assertEquals(24, hours.size)

    val dayHours = hours.filter { !it.isNight }
    val nightHours = hours.filter { it.isNight }

    assertEquals(12, dayHours.size)
    assertEquals(12, nightHours.size)

    // All day hours should have identical duration
    val dayDur = dayHours.first().durationSeconds
    for (h in dayHours) {
      assertEquals(dayDur, h.durationSeconds, 0.001)
    }

    // All night hours should have identical duration
    val nightDur = nightHours.first().durationSeconds
    for (h in nightHours) {
      assertEquals(nightDur, h.durationSeconds, 0.001)
    }
  }
}

