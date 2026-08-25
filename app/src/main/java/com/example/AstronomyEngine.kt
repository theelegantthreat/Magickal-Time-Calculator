package com.example

import java.util.Calendar
import kotlin.math.*

object AstronomyEngine {

    val PLANET_ORDER = listOf("Saturn", "Jupiter", "Mars", "Sun", "Venus", "Mercury", "Moon")
    val DAY_RULERS = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn")
    val DAY_NAMES = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    val PLANET_SYMBOLS = mapOf(
        "Sun" to "☉",
        "Moon" to "☽",
        "Mars" to "♂",
        "Mercury" to "☿",
        "Jupiter" to "♃",
        "Venus" to "♀",
        "Saturn" to "♄"
    )

    val PLANET_COLORS = mapOf(
        "Sun" to "#FFD700",
        "Moon" to "#C0C0C0",
        "Mars" to "#FF4444",
        "Mercury" to "#FFA500",
        "Jupiter" to "#9370DB",
        "Venus" to "#FF69B4",
        "Saturn" to "#708090"
    )

    val PLANET_DESCRIPTIONS = mapOf(
        "Sun" to "Success, vitality, courage, leadership, ego",
        "Moon" to "Emotions, intuition, subconscious, home, change",
        "Mars" to "Energy, action, passion, drive, conflict, strength",
        "Mercury" to "Communication, learning, travel, intellect, commerce",
        "Jupiter" to "Growth, expansion, luck, wisdom, philosophy",
        "Venus" to "Love, beauty, relationships, art, luxury, harmony",
        "Saturn" to "Discipline, structure, boundaries, karma, time, focus"
    )

    data class TattvaDefinition(
        val name: String,
        val symbol: String,
        val element: String,
        val description: String,
        val colorHex: String
    )

    val TATTVA_ORDER = listOf(
        TattvaDefinition("Akasha", "⬭", "Ether", "Spiritual realm, meditation, cosmic consciousness, void and space", "#9888CC"),
        TattvaDefinition("Vayu", "○", "Air", "Movement, change, intellect, communication, light ideas", "#87CEEB"),
        TattvaDefinition("Tejas", "△", "Fire", "Transformation, energy, willpower, action, dynamic passion", "#FF4500"),
        TattvaDefinition("Apas", "☽", "Water", "Emotion, healing, intuition, flow, love, and magnetic force", "#4169E1"),
        TattvaDefinition("Prithivi", "□", "Earth", "Manifestation, stability, physical world, material gains, health", "#DAA520")
    )

    data class SolarTimes(
        val sunriseHours: Double,
        val sunsetHours: Double,
        val isPolarDay: Boolean = false,
        val isPolarNight: Boolean = false
    )

    data class PlanetaryHour(
        val number: Int,
        val planetName: String,
        val planetSymbol: String,
        val startSecondOfDay: Double,
        val endSecondOfDay: Double,
        val isNight: Boolean,
        val colorHex: String
    ) {
        val durationSeconds: Double get() = endSecondOfDay - startSecondOfDay
    }

    data class TattvaCycle(
        val index: Int,
        val name: String,
        val symbol: String,
        val element: String,
        val description: String,
        val startSecondOfDay: Double,
        val endSecondOfDay: Double,
        val colorHex: String
    ) {
        val durationSeconds: Double get() = endSecondOfDay - startSecondOfDay
    }

    data class CombinedShift(
        val startSecondOfDay: Double,
        val endSecondOfDay: Double,
        val planetName: String,
        val planetSymbol: String,
        val planetColorHex: String,
        val tattvaName: String,
        val tattvaSymbol: String,
        val tattvaColorHex: String
    ) {
        val durationSeconds: Double get() = endSecondOfDay - startSecondOfDay
    }

    fun getSolarTimes(year: Int, month: Int, day: Int, latitude: Double, longitude: Double, timezoneOffsetHours: Double): SolarTimes {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1 + (12.0 - longitude / 15.0) / 24.0)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) - 0.014615 * cos(2.0 * gamma) - 0.040849 * sin(2.0 * gamma))
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2.0 * gamma) + 0.000907 * sin(2.0 * gamma) - 0.002697 * cos(3.0 * gamma) + 0.00148 * sin(3.0 * gamma)

        val latRad = Math.toRadians(latitude)
        val cosHA = (cos(Math.toRadians(90.833)) / (cos(latRad) * cos(decl))) - (tan(latRad) * tan(decl))

        if (cosHA > 1.0) {
            return SolarTimes(6.0, 18.0, isPolarNight = true)
        } else if (cosHA < -1.0) {
            return SolarTimes(0.001, 23.999, isPolarDay = true)
        }

        val haRad = acos(cosHA)
        val haDegrees = Math.toDegrees(haRad)

        val sunriseUtcMin = 720.0 - 4.0 * (longitude + haDegrees) - eqTime
        val sunsetUtcMin = 720.0 - 4.0 * (longitude - haDegrees) - eqTime

        var sunriseLocalHours = (sunriseUtcMin / 60.0) + timezoneOffsetHours
        var sunsetLocalHours = (sunsetUtcMin / 60.0) + timezoneOffsetHours

        sunriseLocalHours = (sunriseLocalHours % 24.0 + 24.0) % 24.0
        sunsetLocalHours = (sunsetLocalHours % 24.0 + 24.0) % 24.0

        return SolarTimes(sunriseLocalHours, sunsetLocalHours)
    }

    fun getDayOfWeekIndex(year: Int, month: Int, day: Int): Int {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        return (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7
    }

    fun calculatePlanetaryHours(
        sunriseSec: Double,
        sunsetSec: Double,
        tomorrowSunriseSec: Double,
        dayOfWeekIndex: Int
    ): List<PlanetaryHour> {
        val dayLen = sunsetSec - sunriseSec
        val nightLen = tomorrowSunriseSec - sunsetSec
        val dayHourDuration = dayLen / 12.0
        val nightHourDuration = nightLen / 12.0

        val ruler = DAY_RULERS[dayOfWeekIndex]
        val startIdx = PLANET_ORDER.indexOf(ruler)
        val list = mutableListOf<PlanetaryHour>()

        // 12 Daytime Hours
        for (i in 0 until 12) {
            val pi = (startIdx + i) % 7
            val start = sunriseSec + i * dayHourDuration
            val end = sunriseSec + (i + 1) * dayHourDuration
            val pName = PLANET_ORDER[pi]
            list.add(
                PlanetaryHour(
                    number = i + 1,
                    planetName = pName,
                    planetSymbol = PLANET_SYMBOLS[pName] ?: "",
                    startSecondOfDay = start,
                    endSecondOfDay = end,
                    isNight = false,
                    colorHex = PLANET_COLORS[pName] ?: "#FFFFFF"
                )
            )
        }

        // 12 Nighttime Hours
        val nightStartIdx = (startIdx + 12) % 7
        for (i in 0 until 12) {
            val pi = (nightStartIdx + i) % 7
            val start = sunsetSec + i * nightHourDuration
            val end = sunsetSec + (i + 1) * nightHourDuration
            val pName = PLANET_ORDER[pi]
            list.add(
                PlanetaryHour(
                    number = i + 13,
                    planetName = pName,
                    planetSymbol = PLANET_SYMBOLS[pName] ?: "",
                    startSecondOfDay = start,
                    endSecondOfDay = end,
                    isNight = true,
                    colorHex = PLANET_COLORS[pName] ?: "#FFFFFF"
                )
            )
        }
        return list
    }

    fun calculateTattvas(sunriseSec: Double, tomorrowSunriseSec: Double): List<TattvaCycle> {
        val totalDuration = tomorrowSunriseSec - sunriseSec
        val cycleLength = totalDuration / 60.0
        val list = mutableListOf<TattvaCycle>()

        for (i in 0 until 60) {
            val def = TATTVA_ORDER[i % 5]
            val start = sunriseSec + i * cycleLength
            val end = start + cycleLength
            list.add(
                TattvaCycle(
                    index = i,
                    name = def.name,
                    symbol = def.symbol,
                    element = def.element,
                    description = def.description,
                    startSecondOfDay = start,
                    endSecondOfDay = end,
                    colorHex = def.colorHex
                )
            )
        }
        return list
    }

    fun calculateCombinedView(
        planetaryHours: List<PlanetaryHour>,
        tattvas: List<TattvaCycle>
    ): List<CombinedShift> {
        val boundaries = mutableSetOf<Double>()
        planetaryHours.forEach {
            boundaries.add(it.startSecondOfDay)
            boundaries.add(it.endSecondOfDay)
        }
        tattvas.forEach {
            boundaries.add(it.startSecondOfDay)
            boundaries.add(it.endSecondOfDay)
        }
        val sortedBoundaries = boundaries.sorted()
        val list = mutableListOf<CombinedShift>()

        for (i in 0 until sortedBoundaries.size - 1) {
            val start = sortedBoundaries[i]
            val end = sortedBoundaries[i + 1]
            if (end <= start) continue

            val mid = (start + end) / 2.0
            val ph = planetaryHours.find { mid >= it.startSecondOfDay && mid < it.endSecondOfDay }
            val tv = tattvas.find { mid >= it.startSecondOfDay && mid < it.endSecondOfDay }

            if (ph != null && tv != null) {
                val last = list.lastOrNull()
                if (last != null && last.planetName == ph.planetName && last.tattvaName == tv.name) {
                    list[list.size - 1] = last.copy(endSecondOfDay = end)
                } else {
                    list.add(
                        CombinedShift(
                            startSecondOfDay = start,
                            endSecondOfDay = end,
                            planetName = ph.planetName,
                            planetSymbol = ph.planetSymbol,
                            planetColorHex = ph.colorHex,
                            tattvaName = tv.name,
                            tattvaSymbol = tv.symbol,
                            tattvaColorHex = tv.colorHex
                        )
                    )
                }
            }
        }
        return list
    }
}
