package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

data class UserCoordinates(
    val latitude: Double,
    val longitude: Double,
    val areaLabel: String? = null,
)

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentCoordinates(context: Context): UserCoordinates? {
        if (!hasLocationPermission(context)) return null
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val location = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
                ?: client.lastLocation.await()
                ?: return null
            val area = resolveAreaLabel(context, location.latitude, location.longitude)
            UserCoordinates(
                latitude = location.latitude,
                longitude = location.longitude,
                areaLabel = area,
            )
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveAreaLabel(context: Context, lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) return@withContext null
                val geocoder = Geocoder(context, Locale.GERMAN)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var label: String? = null
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        val addr = addresses.firstOrNull()
                        label = listOfNotNull(addr?.subLocality, addr?.locality)
                            .distinct()
                            .joinToString(", ")
                            .takeIf { it.isNotBlank() }
                    }
                    label
                } else {
                    val addr = geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
                    listOfNotNull(addr?.subLocality, addr?.locality)
                        .distinct()
                        .joinToString(", ")
                        .takeIf { it.isNotBlank() }
                }
            } catch (_: Exception) {
                null
            }
        }

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }

    fun formatDistanceGerman(meters: Float): String {
        return if (meters < 950f) {
            val rounded = ((meters / 10f).roundToInt() * 10).coerceAtLeast(10)
            "$rounded m"
        } else {
            val km = meters / 1000f
            String.format(Locale.GERMAN, "%.1f km", km)
        }
    }
}
