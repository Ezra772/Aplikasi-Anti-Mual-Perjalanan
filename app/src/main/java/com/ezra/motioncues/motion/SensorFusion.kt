package com.ezra.motioncues.motion

import com.ezra.motioncues.sensor.SensorData
import kotlin.math.sqrt

/**
 * Complementary filter that fuses gyroscope and accelerometer data into
 * smoothed pitch / roll / yaw angles.
 *
 * The complementary filter trusts the gyroscope for short-term rapid changes
 * (98% weight) and the accelerometer for long-term drift correction (2% weight).
 *
 * If no gyroscope is present the filter falls back to accelerometer-only
 * tilt estimation using atan2.
 *
 * All arithmetic uses pre-allocated arrays — no heap allocation per frame.
 */
class SensorFusion {

    companion object {
        private const val GYRO_WEIGHT = 0.98f
        private const val ACCEL_WEIGHT = 1f - GYRO_WEIGHT   // 0.02f
        private const val NANOS_TO_SECONDS = 1f / 1_000_000_000f
        private const val RAD_TO_DEG = 180f / Math.PI.toFloat()
    }

    // Output angle state (degrees): [pitch, roll, yaw]
    private val angles = FloatArray(3)

    // Tracks the previous event timestamp to derive deltaTime
    private var lastTimestampNanos = 0L

    // Reusable working arrays — avoids allocation inside update()
    private val gyroAngularDelta = FloatArray(3)
    private val accelAngles = FloatArray(3)

    /**
     * Update the fusion state with a new [SensorData] reading and return
     * a [FloatArray] containing [pitch, roll, yaw] in degrees.
     *
     * The returned array is the internal [angles] array — callers must copy
     * it if they need to hold it beyond the next [update] call.
     */
    fun update(data: SensorData): FloatArray {
        val nowNanos = data.timestampNanos

        // Skip the very first sample — we need a previous timestamp for deltaTime
        if (lastTimestampNanos == 0L) {
            lastTimestampNanos = nowNanos
            estimateAnglesFromAccelerometer(data.accelerometer, angles)
            return angles
        }

        val deltaTimeSeconds = (nowNanos - lastTimestampNanos) * NANOS_TO_SECONDS
        lastTimestampNanos = nowNanos

        if (data.hasGyroscope) {
            fuseWithGyroscope(data, deltaTimeSeconds)
        } else {
            // Accelerometer-only fallback: directly compute tilt angles
            estimateAnglesFromAccelerometer(data.accelerometer, angles)
        }

        return angles
    }

    private fun fuseWithGyroscope(data: SensorData, deltaTimeSeconds: Float) {
        // 1. Integrate gyroscope rates to get angular delta
        //    gyroscope values are in rad/s
        gyroAngularDelta[0] = data.gyroscope[0] * deltaTimeSeconds * RAD_TO_DEG
        gyroAngularDelta[1] = data.gyroscope[1] * deltaTimeSeconds * RAD_TO_DEG
        gyroAngularDelta[2] = data.gyroscope[2] * deltaTimeSeconds * RAD_TO_DEG

        // 2. Compute accelerometer tilt estimate
        estimateAnglesFromAccelerometer(data.accelerometer, accelAngles)

        // 3. Complementary filter: blend gyro (fast) with accel (slow)
        angles[0] = GYRO_WEIGHT * (angles[0] + gyroAngularDelta[0]) + ACCEL_WEIGHT * accelAngles[0]
        angles[1] = GYRO_WEIGHT * (angles[1] + gyroAngularDelta[1]) + ACCEL_WEIGHT * accelAngles[1]
        // Yaw cannot be corrected by accelerometer alone (it doesn't measure rotation about Z-gravity)
        angles[2] = angles[2] + gyroAngularDelta[2]
    }

    /**
     * Compute pitch and roll from a raw accelerometer reading using atan2.
     * Yaw is set to zero since the accelerometer cannot measure it.
     */
    private fun estimateAnglesFromAccelerometer(accel: FloatArray, out: FloatArray) {
        val ax = accel[0]
        val ay = accel[1]
        val az = accel[2]
        val magnitude = sqrt(ax * ax + ay * ay + az * az)
        if (magnitude < 0.001f) return // avoid divide-by-zero on flat / no gravity

        out[0] = Math.atan2(ay.toDouble(), az.toDouble()).toFloat() * RAD_TO_DEG  // pitch
        out[1] = Math.atan2(-ax.toDouble(), sqrt((ay * ay + az * az).toDouble())).toFloat() * RAD_TO_DEG // roll
        // out[2] (yaw) unchanged — cannot derive from accelerometer alone
    }

    /** Reset all state — call during calibration. */
    fun reset() {
        angles.fill(0f)
        gyroAngularDelta.fill(0f)
        accelAngles.fill(0f)
        lastTimestampNanos = 0L
    }
}
