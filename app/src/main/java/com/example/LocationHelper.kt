package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale

object LocationHelper {

    private val FALLBACK_LOCATIONS = listOf(
        Triple(51.5074, -0.1278, "London, UK"),
        Triple(30.0444, 31.2357, "Cairo, Egypt"),
        Triple(27.1751, 78.0421, "Taj Mahal, India"),
        Triple(35.6762, 139.6503, "Tokyo, Japan"),
        Triple(-33.8688, 151.2093, "Sydney, Australia")
    )

    fun handleLocationDetection(
        context: Context,
        onLocationDetected: (Double, Double, String) -> Unit,
        onStatusToast: (String) -> Unit
    ) {
        performRealLocationDetection(
            context = context,
            onUpdate = { lat, lon, name ->
                onLocationDetected(lat, lon, name)
                onStatusToast("Location updated: $name")
            },
            onFailure = { error ->
                onStatusToast("GPS unavailable ($error). Loaded fallback location.")
                val picked = FALLBACK_LOCATIONS.random()
                onLocationDetected(picked.first, picked.second, picked.third)
            }
        )
    }

    private fun performRealLocationDetection(
        context: Context,
        onUpdate: (Double, Double, String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            onFailure("Location service not available on device")
            return
        }

        val hasFine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            onFailure("Required location permissions not granted")
            return
        }

        var isGpsEnabled = false
        var isNetworkEnabled = false
        try {
            isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            onFailure("Could not query providers: ${e.localizedMessage}")
            return
        }

        if (!isGpsEnabled && !isNetworkEnabled) {
            onFailure("GPS and network location providers are both disabled")
            return
        }

        var bestLocation: Location? = null
        try {
            if (isGpsEnabled) {
                val loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (loc != null) bestLocation = loc
            }
            if (bestLocation == null && isNetworkEnabled) {
                val loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (loc != null) bestLocation = loc
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}

        if (bestLocation != null) {
            resolveLocationAndGeocode(context, bestLocation, onUpdate)
            return
        }

        val provider = if (isNetworkEnabled) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    resolveLocationAndGeocode(context, location, onUpdate)
                    try {
                        locationManager.removeUpdates(this)
                    } catch (_: Exception) {}
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            onFailure("Security error: ${e.localizedMessage}")
        } catch (e: Exception) {
            onFailure("Network/GPS fault: ${e.localizedMessage}")
        }
    }

    private fun resolveLocationAndGeocode(
        context: Context,
        location: Location,
        onUpdate: (Double, Double, String) -> Unit
    ) {
        val lat = location.latitude
        val lon = location.longitude

        Thread {
            var solvedName: String
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                val address = addresses?.firstOrNull()
                if (address != null) {
                    val city = address.locality ?: address.subAdminArea ?: address.adminArea
                    val country = address.countryCode ?: address.countryName ?: ""
                    solvedName = if (city != null) "$city, $country" else if (country.isNotEmpty()) country else "Lat: ${String.format(Locale.US, "%.3f", lat)}"
                } else {
                    solvedName = "Latitude: ${String.format(Locale.US, "%.3f", lat)}"
                }
            } catch (_: Exception) {
                solvedName = "Location at ${String.format(Locale.US, "%.3f", lat)}, ${String.format(Locale.US, "%.3f", lon)}"
            }

            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                onUpdate(lat, lon, solvedName)
            }
        }.start()
    }
}
