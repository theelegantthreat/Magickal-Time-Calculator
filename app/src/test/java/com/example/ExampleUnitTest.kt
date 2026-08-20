package com.example

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testTimeFormatUtils() {
    assertEquals("06:00:00", TimeFormatUtils.formatSecToHms(21600.0))
    assertEquals("18:30:15", TimeFormatUtils.formatSecToHms(66615.0))
    assertEquals("06:00", TimeFormatUtils.formatSecToLocalTime(21600.0))
    assertEquals("18:30", TimeFormatUtils.formatSecToLocalTime(66615.0))
    assertEquals("05:30", TimeFormatUtils.formatRemainingTime(330))
    assertEquals("00:00", TimeFormatUtils.formatRemainingTime(-10))
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
      sunriseSeconds = 21600.0,
      sunsetSeconds = 64800.0,
      tomorrowSunriseSeconds = 21600.0,
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
}
