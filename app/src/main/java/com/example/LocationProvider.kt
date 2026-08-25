package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Result data class containing location coordinates and resolved human-readable place name.
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f
)

/**
 * Modern Location Provider leveraging Google Play Services FusedLocationProviderClient
 * to acquire accurate user coordinates for dynamic sunrise, sunset, and astronomical calculations.
 */
class LocationProvider(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Checks if fine or coarse location permissions are granted.
     */
    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    /**
     * Acquires the current user location using FusedLocationProviderClient.
     * Tries [getCurrentLocation] with high accuracy first, falling back to [lastLocation].
     *
     * @return [UserLocation] with latitude, longitude, and geocoded name, or null if unavailable.
     */
    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(): UserLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext null
        }

        try {
            // Attempt 1: Fetch fresh current location
            val freshLocation = getFreshLocationFromFused()
            if (freshLocation != null) {
                return@withContext buildUserLocation(freshLocation)
            }

            // Attempt 2: Fallback to last known location
            val lastLoc = getLastKnownLocationFromFused()
            if (lastLoc != null) {
                return@withContext buildUserLocation(lastLoc)
            }
        } catch (e: SecurityException) {
            return@withContext null
        } catch (e: Exception) {
            return@withContext null
        }

        null
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFreshLocationFromFused(): Location? {
        return suspendCancellableCoroutine { continuation ->
            val cts = CancellationTokenSource()
            continuation.invokeOnCancellation { cts.cancel() }

            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(30_000)
                .setDurationMillis(10_000)
                .build()

            fusedLocationClient.getCurrentLocation(request, cts.token)
                .addOnSuccessListener { location: Location? ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocationFromFused(): Location? {
        return suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }
    }

    /**
     * Resolves human-readable address / city name using Geocoder.
     */
    private suspend fun buildUserLocation(location: Location): UserLocation {
        val lat = location.latitude
        val lon = location.longitude
        val placeName = reverseGeocode(lat, lon)

        return UserLocation(
            latitude = lat,
            longitude = lon,
            locationName = placeName,
            altitude = location.altitude,
            accuracy = location.accuracy
        )
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return@withContext suspendCancellableCoroutine { continuation ->
                    try {
                        geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val address = addresses.firstOrNull()
                                val name = formatAddressName(address, lat, lon)
                                if (continuation.isActive) continuation.resume(name)
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(formatCoordinatesFallback(lat, lon))
                            }
                        })
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resume(formatCoordinatesFallback(lat, lon))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                val address = addresses?.firstOrNull()
                return@withContext formatAddressName(address, lat, lon)
            }
        } catch (e: Exception) {
            return@withContext formatCoordinatesFallback(lat, lon)
        }
    }

    private fun formatAddressName(address: Address?, lat: Double, lon: Double): String {
        if (address == null) return formatCoordinatesFallback(lat, lon)
        val city = address.locality ?: address.subAdminArea ?: address.adminArea
        val country = address.countryCode ?: address.countryName
        return when {
            city != null && !country.isNullOrEmpty() -> "$city, $country"
            city != null -> city
            !country.isNullOrEmpty() -> country
            else -> formatCoordinatesFallback(lat, lon)
        }
    }

    private fun formatCoordinatesFallback(lat: Double, lon: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lonDir = if (lon >= 0) "E" else "W"
        return String.format(Locale.US, "%.2f°%s, %.2f°%s", Math.abs(lat), latDir, Math.abs(lon), lonDir)
    }
}
