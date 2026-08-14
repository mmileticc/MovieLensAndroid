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
 * Sortiranje bioskopa po udaljenosti na Movie Detail ekranu (videti MOVIELENS_ROADMAP.md).
 * Namerno bez play-services-location (FusedLocationProviderClient) - ne dodajemo novi Gradle
 * dependency samo za "poslednju poznatu lokaciju", ugrađeni LocationManager je dovoljan za ovaj
 * slučaj upotrebe (nije potrebno kontinuirano/precizno praćenje, samo gruba udaljenost do bioskopa
 * na nivou grada).
 */
@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Najsvežija keširana lokacija preko svih uključenih provajdera (GPS/network/passive) - bez
     * aktivnog zahteva za novo očitavanje (to bi tražilo callback/coroutine kanal i usporilo
     * učitavanje ekrana). Vraća null ako nema dozvole ili nijedan provajder nema keširanu poziciju
     * (npr. uređaj bez GPS istorije - retko, ali moguće na emulatoru).
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
