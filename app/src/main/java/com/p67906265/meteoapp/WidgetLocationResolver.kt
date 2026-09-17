package com.p67906265.meteoapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class WidgetPlace(val city: String, val lat: Double, val lon: Double)

object WidgetLocationResolver {

    suspend fun resolve(context: Context): WidgetPlace = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("meteo_preferences", Context.MODE_PRIVATE)
        val fallback = WidgetPlace(
            city = prefs.getString("widget_city", "Roma") ?: "Roma",
            lat = java.lang.Double.longBitsToDouble(
                prefs.getLong("widget_lat", java.lang.Double.doubleToRawLongBits(41.9028))
            ),
            lon = java.lang.Double.longBitsToDouble(
                prefs.getLong("widget_lon", java.lang.Double.doubleToRawLongBits(12.4964))
            )
        )

        val location = freshLocation(context) ?: newestLastKnownLocation(context)
        if (location == null) return@withContext fallback

        val city = resolveCity(context, location.latitude, location.longitude)
            ?: fallback.city.takeUnless { it == "Posizione attuale" }
            ?: "Posizione attuale"
        val updated = WidgetPlace(city, location.latitude, location.longitude)
        prefs.edit()
            .putString("widget_city", updated.city)
            .putLong("widget_lat", java.lang.Double.doubleToRawLongBits(updated.lat))
            .putLong("widget_lon", java.lang.Double.doubleToRawLongBits(updated.lon))
            .apply()
        updated
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshLocation(context: Context): Location? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val providers = buildList {
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
            if (hasFine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
        }
        if (providers.isEmpty()) return null

        return withTimeoutOrNull(4_000L) {
            coroutineScope {
                providers.map { provider -> async { currentFromProvider(context, manager, provider) } }
                    .awaitAll()
                    .filterNotNull()
                    .maxByOrNull { it.time }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentFromProvider(
        context: Context,
        manager: LocationManager,
        provider: String
    ): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        try {
            manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        } catch (_: Exception) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun newestLastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun resolveCity(context: Context, lat: Double, lon: Double): String? = try {
        val address = Geocoder(context, Locale.ITALIAN).getFromLocation(lat, lon, 1)?.firstOrNull()
        address?.locality ?: address?.subAdminArea
    } catch (_: Exception) {
        null
    }
}
