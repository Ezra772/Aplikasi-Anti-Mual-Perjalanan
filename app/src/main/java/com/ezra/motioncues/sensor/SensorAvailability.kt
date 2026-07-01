package com.ezra.motioncues.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

/**
 * Snapshot of which motion sensors are physically present on this device.
 * Obtained once at startup and used throughout the app to decide filter/fusion strategy.
 */
data class SensorAvailability(
    val hasAccelerometer: Boolean,
    val hasGyroscope: Boolean
) {
    /** True if the app can provide any motion data at all. */
    val hasAnySensor: Boolean get() = hasAccelerometer || hasGyroscope

    companion object {
        /**
         * Queries the device's SensorManager and returns a [SensorAvailability] snapshot.
         * This call is cheap and can be made from any thread.
         */
        fun check(context: Context): SensorAvailability {
            val sensorManager =
                context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            return SensorAvailability(
                hasAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null,
                hasGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
            )
        }
    }
}
