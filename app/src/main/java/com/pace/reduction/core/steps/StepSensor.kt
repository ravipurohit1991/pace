package com.pace.reduction.core.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * Reads the platform's hardware step counter, once, on demand.
 *
 * Deliberately not a service and not a persistent listener. `TYPE_STEP_COUNTER` is maintained by
 * the sensor hub whether or not anything is listening, so sampling it when the app comes to the
 * foreground and from the existing periodic worker recovers every step taken in between at no
 * battery cost — where keeping a foreground service alive to watch a counter that is already being
 * kept for us would cost a permanent notification and a wakelock for nothing.
 */
class StepSensor(private val context: Context) {

    private val sensorManager: SensorManager?
        get() = ContextCompat.getSystemService(context, SensorManager::class.java)

    /** Whether this device has a step counter at all; many emulators and older phones do not. */
    val isAvailable: Boolean
        get() = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    /** Activity recognition became a runtime permission in Android 10. */
    val hasPermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The counter's current value, or null if it cannot be read.
     *
     * The sensor only reports when it has something to say, so this waits briefly for the first
     * event and gives up rather than hanging: a user standing still has no new steps to deliver,
     * and that is a normal outcome, not a failure.
     */
    suspend fun readCounter(): Long? {
        if (!isAvailable || !hasPermission) return null
        val manager = sensorManager ?: return null
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return try {
            withTimeout(READ_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent?) {
                            val value = event?.values?.firstOrNull() ?: return
                            manager.unregisterListener(this)
                            if (continuation.isActive) continuation.resume(value.toLong())
                        }

                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                    }
                    // SENSOR_DELAY_FASTEST only affects how quickly the first (and only) sample
                    // arrives; the listener is gone again immediately afterwards.
                    val registered = manager.registerListener(
                        listener,
                        sensor,
                        SensorManager.SENSOR_DELAY_FASTEST,
                    )
                    if (!registered && continuation.isActive) {
                        continuation.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    continuation.invokeOnCancellation { manager.unregisterListener(listener) }
                }
            }
        } catch (_: TimeoutCancellationException) {
            null
        }
    }

    private companion object {
        const val READ_TIMEOUT_MS = 2_500L
    }
}
