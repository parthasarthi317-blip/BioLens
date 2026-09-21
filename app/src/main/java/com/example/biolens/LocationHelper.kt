package com.example.biolens

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

object LocationHelper {

    data class LocationResult(
        val latitude: Double?,
        val longitude: Double?,
        val locationString: String?
    )

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation(context: Context, callback: (LocationResult) -> Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cts.token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                reverseGeocode(context, location.latitude, location.longitude) { cityCountry ->
                    callback(LocationResult(location.latitude, location.longitude, cityCountry))
                }
            } else {
                callback(LocationResult(null, null, null))
            }
        }.addOnFailureListener {
            callback(LocationResult(null, null, null))
        }
    }

    private fun reverseGeocode(
        context: Context,
        lat: Double,
        lng: Double,
        callback: (String?) -> Unit
    ) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lng, 1) { addresses ->
                    val address = addresses.firstOrNull()
                    if (address != null) {
                        val city = address.locality ?: address.subAdminArea ?: address.adminArea
                        val country = address.countryName
                        val result = if (city != null && country != null) "$city, $country" else country ?: city
                        callback(result)
                    } else {
                        callback(null)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                val address = addresses?.firstOrNull()
                if (address != null) {
                    val city = address.locality ?: address.subAdminArea ?: address.adminArea
                    val country = address.countryName
                    val result = if (city != null && country != null) "$city, $country" else country ?: city
                    callback(result)
                } else {
                    callback(null)
                }
            }
        } catch (e: Exception) {
            callback(null)
        }
    }
}
