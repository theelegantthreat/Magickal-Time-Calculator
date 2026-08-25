package com.example

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class SunriseSunsetApiResponse(
    @param:Json(name = "results") val results: SunriseSunsetApiResult?,
    @param:Json(name = "status") val status: String?
)

@JsonClass(generateAdapter = true)
data class SunriseSunsetApiResult(
    @param:Json(name = "sunrise") val sunrise: String?,
    @param:Json(name = "sunset") val sunset: String?,
    @param:Json(name = "solar_noon") val solarNoon: String?,
    @param:Json(name = "day_length") val dayLength: Any?
)

interface SunriseSunsetApi {
    @GET("json")
    suspend fun getSunriseSunset(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("date") date: String,
        @Query("formatted") formatted: Int = 0
    ): SunriseSunsetApiResponse
}

object SunriseSunsetApiClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.sunrise-sunset.org/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: SunriseSunsetApi = retrofit.create(SunriseSunsetApi::class.java)

    /**
     * Fetches sunrise and sunset for a given date, coordinates, and converts to local hour values.
     * Returns null if network fails so that NOAA calculation can be used seamlessly.
     */
    suspend fun fetchSolarTimes(
        lat: Double,
        lon: Double,
        dateStr: String,
        timezone: TimeZone = TimeZone.getDefault()
    ): AstronomyEngine.SolarTimes? = withContext(Dispatchers.IO) {
        try {
            val response = api.getSunriseSunset(lat = lat, lng = lon, date = dateStr, formatted = 0)
            if (response.status == "OK" && response.results != null) {
                val res = response.results
                val sunriseUtcStr = res.sunrise // e.g. "2026-08-25T13:21:45+00:00"
                val sunsetUtcStr = res.sunset

                if (!sunriseUtcStr.isNullOrEmpty() && !sunsetUtcStr.isNullOrEmpty()) {
                    val sdfIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val sunriseDate = sdfIso.parse(sunriseUtcStr)
                    val sunsetDate = sdfIso.parse(sunsetUtcStr)

                    if (sunriseDate != null && sunsetDate != null) {
                        val calSunrise = Calendar.getInstance(timezone).apply { time = sunriseDate }
                        val calSunset = Calendar.getInstance(timezone).apply { time = sunsetDate }

                        val sunriseHours = calSunrise.get(Calendar.HOUR_OF_DAY) +
                                (calSunrise.get(Calendar.MINUTE) / 60.0) +
                                (calSunrise.get(Calendar.SECOND) / 3600.0)

                        val sunsetHours = calSunset.get(Calendar.HOUR_OF_DAY) +
                                (calSunset.get(Calendar.MINUTE) / 60.0) +
                                (calSunset.get(Calendar.SECOND) / 3600.0)

                        return@withContext AstronomyEngine.SolarTimes(
                            sunriseHours = sunriseHours,
                            sunsetHours = sunsetHours
                        )
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
