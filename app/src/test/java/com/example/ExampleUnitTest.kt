package com.example

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testGenerateLogsCsv() {
    val logs = listOf(
      LoggedShift(
        id = 1L,
        timestamp = 1700000000000L,
        dateString = "2026-08-14",
        locationName = "New York, USA",
        latitude = 40.7128,
        longitude = -74.0060,
        planetName = "Sun",
        tattvaName = "Tejas",
        notes = "Noon Solar Meditation"
      )
    )
    val csv = ExportUtils.generateLogsCsv(logs)
    assertTrue(csv.contains("Log_ID,Timestamp_Epoch_Ms,Local_DateTime"))
    assertTrue(csv.contains("Noon Solar Meditation"))
    assertTrue(csv.contains("\"Sun\""))
    assertTrue(csv.contains("\"Tejas\""))
    assertTrue(csv.contains("New York, USA"))
  }

  @Test
  fun testGenerateCyclesCsv() {
    val ph = listOf(
      AstronomyEngine.PlanetaryHour(
        number = 1,
        planetName = "Sun",
        planetSymbol = "☉",
        startSecondOfDay = 21600.0,
        endSecondOfDay = 25200.0,
        isNight = false,
        colorHex = "#FFD700"
      )
    )
    val tv = listOf(
      AstronomyEngine.TattvaCycle(
        index = 0,
        name = "Akasha",
        symbol = "⬭",
        element = "Ether",
        description = "Cosmic consciousness",
        startSecondOfDay = 21600.0,
        endSecondOfDay = 23040.0,
        colorHex = "#9888CC"
      )
    )
    val cb = listOf(
      AstronomyEngine.CombinedShift(
        startSecondOfDay = 21600.0,
        endSecondOfDay = 23040.0,
        planetName = "Sun",
        planetSymbol = "☉",
        planetColorHex = "#FFD700",
        tattvaName = "Akasha",
        tattvaSymbol = "⬭",
        tattvaColorHex = "#9888CC"
      )
    )
    val calc = MainViewModel.CalculationResults(
      date = "2026-08-14",
      dayOfWeekIndex = 5,
      dayName = "Friday",
      dayRulerName = "Venus",
      dayRulerSymbol = "♀",
      sunriseSeconds = 21600.0,
      sunsetSeconds = 64800.0,
      tomorrowSunriseSeconds = 108000.0,
      planetaryHours = ph,
      tattvas = tv,
      combined = cb,
      dayHourLengthSeconds = 3600.0,
      nightHourLengthSeconds = 3600.0,
      tattvaLengthSeconds = 1440.0
    )
    val csv = ExportUtils.generateCyclesCsv(calc, "New York, USA", 40.7128, -74.0060)
    assertTrue(csv.contains("PLANETARY HOURS"))
    assertTrue(csv.contains("TATTWIC TIDES"))
    assertTrue(csv.contains("COMBINED PLANETARY & TATTWIC ALIGNMENTS"))
    assertTrue(csv.contains("06:00:00"))
  }

  @Test
  fun testPlanetaryHoursAndTattwaCalculations() {
    // Continuous timeline from Sunrise (06:00 = 21600s) to Sunset (18:00 = 64800s) to Next Sunrise (06:00 + 24h = 108000s)
    val sunriseSec = 21600.0
    val sunsetSec = 64800.0
    val nextSunriseSec = 108000.0
    val dowIndex = 2 // Tuesday -> Mars (♂)

    val hours = AstronomyEngine.calculatePlanetaryHours(sunriseSec, sunsetSec, nextSunriseSec, dowIndex)
    assertEquals(24, hours.size)
    assertEquals(1, hours.first().number)
    assertEquals("Mars", hours.first().planetName)
    assertFalse(hours.first().isNight)
    assertEquals(21600.0, hours.first().startSecondOfDay, 0.01)

    // Hour 12 is Day, Hour 13 is Night starting at Sunset
    assertFalse(hours[11].isNight)
    assertTrue(hours[12].isNight)
    assertEquals(64800.0, hours[12].startSecondOfDay, 0.01)
    assertEquals(24, hours.last().number)
    assertEquals(108000.0, hours.last().endSecondOfDay, 0.01)

    // Tattwas: 60 cycles across the full sunrise-to-next-sunrise span
    val tattvas = AstronomyEngine.calculateTattvas(sunriseSec, nextSunriseSec)
    assertEquals(60, tattvas.size)
    assertEquals("Akasha", tattvas[0].name)
    assertEquals("Vayu", tattvas[1].name)
    assertEquals("Tejas", tattvas[2].name)
    assertEquals("Apas", tattvas[3].name)
    assertEquals("Prithivi", tattvas[4].name)
    assertEquals("Akasha", tattvas[5].name)
    assertEquals(21600.0, tattvas.first().startSecondOfDay, 0.01)
    assertEquals(108000.0, tattvas.last().endSecondOfDay, 0.01)
  }

  @Test
  fun testNoaaSolarCalculations() {
    // New York (40.7128° N, -74.0060° W) on August 25, 2026 (EDT = UTC-4)
    val solar = AstronomyEngine.getSolarTimes(
      year = 2026,
      month = 8,
      day = 25,
      latitude = 40.7128,
      longitude = -74.0060,
      timezoneOffsetHours = -4.0
    )
    // In NYC on Aug 25, sunrise is approx 6:17 AM (6.28h) and sunset is approx 19:43 (19.72h)
    assertTrue(solar.sunriseHours in 6.1..6.5)
    assertTrue(solar.sunsetHours in 19.5..19.9)
    assertFalse(solar.isPolarDay)
    assertFalse(solar.isPolarNight)
  }

  @Test
  fun testSunriseSunsetHelperCalculation() {
    // Test dynamic calculation using SunriseSunsetCalculator
    val tz = TimeZone.getTimeZone("America/New_York")
    val cal = Calendar.getInstance(tz).apply {
      set(2026, Calendar.AUGUST, 25, 12, 0, 0)
    }
    val solar = SunriseSunsetHelper.calculateSolarTimes(40.7128, -74.0060, tz.id, cal)
    assertNotNull(solar.sunriseCalendar)
    assertNotNull(solar.sunsetCalendar)
    assertTrue(solar.sunriseHours in 6.0..6.7)
    assertTrue(solar.sunsetHours in 19.4..20.2)
  }

  @Test
  fun testCalculationPreferencesEntityDefaults() {
    val prefs = CalculationPreferences(
      id = 1,
      locationName = "London, UK",
      latitude = 51.5074,
      longitude = -0.1278,
      sunriseOverride = "05:45:00",
      sunsetOverride = "20:30:00",
      tomorrowSunriseOverride = "05:46:00",
      darkTheme = true,
      notificationsEnabled = true,
      hapticsEnabled = false,
      tattvaDisplayMode = "DAY_ONLY",
      activeViewMode = "COMBINED_VIEW",
      lastSelectedDate = "2026-08-14"
    )
    assertEquals(1, prefs.id)
    assertEquals("London, UK", prefs.locationName)
    assertEquals(51.5074, prefs.latitude, 0.0001)
    assertEquals(-0.1278, prefs.longitude, 0.0001)
    assertEquals("05:45:00", prefs.sunriseOverride)
    assertEquals("DAY_ONLY", prefs.tattvaDisplayMode)
    assertEquals("COMBINED_VIEW", prefs.activeViewMode)
    assertTrue(prefs.darkTheme)
    assertFalse(prefs.hapticsEnabled)
  }

  @Test
  fun testPlanetaryHourCalculationServiceMidday() {
    val service = PlanetaryHourCalculationService()
    val tz = TimeZone.getTimeZone("America/New_York")
    
    // Tuesday, Aug 25, 2026 at 12:00 PM (Noon)
    val cal = Calendar.getInstance(tz).apply {
      set(2026, Calendar.AUGUST, 25, 12, 0, 0)
    }

    val result = service.calculateCurrentPlanetaryHour(
      latitude = 40.7128,
      longitude = -74.0060,
      targetTime = cal,
      timeZone = tz
    )

    assertEquals("Tuesday", result.astrologicalDayName)
    assertEquals("Mars", result.astrologicalDayRuler)
    assertFalse("Midday should be a daytime hour", result.isNight)
    assertTrue("Hour number should be within 1..12 during day", result.currentHourNumber in 1..12)
    assertEquals(24, result.allHoursOfDay.size)
    assertNotNull(result.planetName)
    assertTrue(result.progress in 0.0f..1.0f)
    assertTrue("Sunrise should be before target time", result.sunriseMillis <= cal.timeInMillis)
    assertTrue("Sunset should be after target time", result.sunsetMillis >= cal.timeInMillis)
  }

  @Test
  fun testPlanetaryHourCalculationServiceBeforeSunriseBelongsToPreviousAstroDay() {
    val service = PlanetaryHourCalculationService()
    val tz = TimeZone.getTimeZone("America/New_York")
    
    // Wednesday, Aug 26, 2026 at 03:00 AM (Before Sunrise)
    // Astrological day should still be Tuesday (Mars)
    val cal = Calendar.getInstance(tz).apply {
      set(2026, Calendar.AUGUST, 26, 3, 0, 0)
    }

    val result = service.calculateCurrentPlanetaryHour(
      latitude = 40.7128,
      longitude = -74.0060,
      targetTime = cal,
      timeZone = tz
    )

    assertEquals("Tuesday", result.astrologicalDayName)
    assertEquals("Mars", result.astrologicalDayRuler)
    assertTrue("3 AM is a night hour", result.isNight)
    assertTrue("Night hours are numbered 13 to 24", result.currentHourNumber in 13..24)
    assertEquals(24, result.allHoursOfDay.size)
  }
}

