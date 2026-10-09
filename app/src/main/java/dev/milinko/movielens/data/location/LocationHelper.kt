package dev.milinko.movielens.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last known location, used to sort cinemas by distance on the movie detail screen.
 *
 * Uses the built-in LocationManager instead of play-services-location: a coarse "last known"
 * position is enough for city-level distances, so no extra dependency is needed.
 */
@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** True if coarse or fine location permission is granted. */
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Freshest cached location from all enabled providers (GPS, network, passive), without asking
     * for a new fix (that would need a callback and slow down the screen). Null without permission
     * or when no provider has a cached position (possible on an emulator).
     */
    fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        return try {
            locationManager.allProviders
                .mapNotNull { provider ->
                    try {
                        locationManager.getLastKnownLocation(provider)
                    } catch (_: SecurityException) {
                        null
                    }
                }
                .maxByOrNull { it.time }
        } catch (_: Exception) {
            null
        }
    }
}
