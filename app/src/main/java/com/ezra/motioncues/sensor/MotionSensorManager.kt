package com.ezra.motioncues.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.ezra.motioncues.utils.Logger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Raw reading from one sensor event, copied out of the Android event array
 * to avoid sharing mutable state across threads.
 */
data class SensorData(
    val accelerometer: FloatArray = FloatArray(3),
    val gyroscope: FloatArray = FloatArray(3),
    val timestampNanos: Long = 0L,
    val hasGyroscope: Boolean = false
) {
    // FloatArray does not implement structural equality; override manually.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensorData) return false
        return timestampNanos == other.timestampNanos &&
                hasGyroscope == other.hasGyroscope &&
                accelerometer.contentEquals(other.accelerometer) &&
                gyroscope.contentEquals(other.gyroscope)
    }

    override fun hashCode(): Int {
        var result = accelerometer.contentHashCode()
        result = 31 * result + gyroscope.contentHashCode()
        result = 31 * result + timestampNanos.hashCode()
        result = 31 * result + hasGyroscope.hashCode()
        return result
    }
}

/**
 * Owns the Android [SensorManager] lifecycle and exposes a [Flow] of [SensorData].
 *
 * Responsibilities:
 * - Register / unregister accelerometer and gyroscope listeners.
 * - Automatically pause when the screen turns off, resume when it turns on.
 * - Merge the latest readings from both sensors into a single [SensorData] emission.
 * - Release every resource in [destroy].
 */
class MotionSensorManager(
    private val context: Context,
    private val samplingRate: SensorSamplingRate = SensorSamplingRate.GAME
) {
    private companion object {
        const val TAG = "MotionSensorManager"
    }

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometerSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gyroscopeSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    val availability: SensorAvailability = SensorAvailability(
        hasAccelerometer = accelerometerSensor != null,
        hasGyroscope = gyroscopeSensor != null
    )

    // Mutable working copies — only written from the sensor callback thread.
    private val latestAccelerometer = FloatArray(3)
    private val latestGyroscope = FloatArray(3)
    private var latestTimestampNanos = 0L

    private var isRegistered = false
    private var screenReceiver: BroadcastReceiver? = null

    // The active Flow channel, kept so we can emit from sensor callbacks.
    private var emitChannel: kotlinx.coroutines.channels.SendChannel<SensorData>? = null

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    event.values.copyInto(latestAccelerometer)
                    latestTimestampNanos = event.timestamp
                }
                Sensor.TYPE_GYROSCOPE -> {
                    event.values.copyInto(latestGyroscope)
                }
            }
            // Emit a merged snapshot; copying arrays avoids mutable-state sharing.
            val snapshot = SensorData(
                accelerometer = latestAccelerometer.copyOf(),
                gyroscope = latestGyroscope.copyOf(),
                timestampNanos = latestTimestampNanos,
                hasGyroscope = availability.hasGyroscope
            )
            emitChannel?.trySend(snapshot)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            Logger.d(TAG, "Sensor accuracy changed: ${sensor.name} accuracy=$accuracy")
        }
    }

    /**
     * Returns a cold [Flow] of [SensorData].
     * Collection starts sensor registration; cancellation unregisters sensors.
     * The screen on/off receiver is also set up inside this flow.
     */
    fun sensorDataFlow(): Flow<SensorData> = callbackFlow {
        emitChannel = channel
        registerSensors()
        registerScreenReceiver()

        awaitClose {
            Logger.d(TAG, "Flow collection cancelled — unregistering sensors")
            unregisterScreenReceiver()
            unregisterSensors()
            emitChannel = null
        }
    }

    private fun registerSensors() {
        if (isRegistered) return
        accelerometerSensor?.let {
            sensorManager.registerListener(
                sensorEventListener, it, samplingRate.microseconds
            )
        }
        gyroscopeSensor?.let {
            sensorManager.registerListener(
                sensorEventListener, it, samplingRate.microseconds
            )
        }
        isRegistered = true
        Logger.d(TAG, "Sensors registered: acc=${availability.hasAccelerometer} gyro=${availability.hasGyroscope}")
    }

    private fun unregisterSensors() {
        if (!isRegistered) return
        sensorManager.unregisterListener(sensorEventListener)
        isRegistered = false
        Logger.d(TAG, "Sensors unregistered")
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Logger.d(TAG, "Screen off — pausing sensors")
                        unregisterSensors()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Logger.d(TAG, "Screen on — resuming sensors")
                        registerSensors()
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)
        screenReceiver = receiver
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                Logger.w(TAG, "Screen receiver was already unregistered")
            }
            screenReceiver = null
        }
    }

    /** Call when the owning Service is destroyed to release all resources. */
    fun destroy() {
        unregisterScreenReceiver()
        unregisterSensors()
        emitChannel = null
        Logger.d(TAG, "MotionSensorManager destroyed")
    }

    /** Change the sampling rate while still running. */
    fun updateSamplingRate(newRate: SensorSamplingRate) {
        if (!isRegistered) return
        unregisterSensors()
        // Re-register with new rate
        accelerometerSensor?.let {
            sensorManager.registerListener(sensorEventListener, it, newRate.microseconds)
        }
        gyroscopeSensor?.let {
            sensorManager.registerListener(sensorEventListener, it, newRate.microseconds)
        }
        isRegistered = true
        Logger.d(TAG, "Sampling rate updated to $newRate")
    }
}
