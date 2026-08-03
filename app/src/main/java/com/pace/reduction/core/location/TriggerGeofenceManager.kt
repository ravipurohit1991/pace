package com.pace.reduction.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.pace.reduction.PaceApplication
import com.pace.reduction.core.notifications.PaceNotifications
import com.pace.reduction.domain.model.TriggerPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object TriggerGeofenceManager {
    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        42,
        Intent(context, TriggerGeofenceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    fun hasRequiredPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine && background
    }

    @SuppressLint("MissingPermission")
    fun sync(context: Context, places: List<TriggerPlace>) {
        val client = LocationServices.getGeofencingClient(context)
        val pendingIntent = pendingIntent(context)
        client.removeGeofences(pendingIntent).addOnCompleteListener {
            val enabled = places.filter { it.enabled && it.automaticCueEnabled }.take(20)
            if (!hasRequiredPermission(context) || enabled.isEmpty()) return@addOnCompleteListener
            val geofences = enabled.map { place ->
                Geofence.Builder()
                    .setRequestId(place.id)
                    .setCircularRegion(place.latitudeRounded, place.longitudeRounded, place.radiusMeters.toFloat())
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .build()
            }
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build()
            client.addGeofences(request, pendingIntent)
        }
    }

    fun removeAll(context: Context) {
        LocationServices.getGeofencingClient(context).removeGeofences(pendingIntent(context))
    }
}

class TriggerGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val event = intent?.let(GeofencingEvent::fromIntent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as PaceApplication).container.repository
                if (!repository.isQuietHoursNow() && PaceNotifications.canPost(context)) {
                    PaceNotifications.postLocationCue(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
