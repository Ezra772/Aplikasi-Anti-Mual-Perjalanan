package com.ezra.motioncues.motion

import com.ezra.motioncues.sensor.SensorData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Core processing pipeline that converts raw [SensorData] into [MotionState].
 *
 * Pipeline per frame:
 *   1. Low-pass filter applied to accelerometer to remove jitter.
 *   2. Complementary sensor fusion (gyroscope + accelerometer) → pitch/roll angles.
 *   3. Map angles to pixel offsets clamped to [±maxOffsetPixels].
 *   4. Calculate intensity from the displacement magnitude.
 *
 * All heavy computation is moved off the Main thread via [Dispatchers.Default].
 * [conflate] ensures stale frames are dropped if the consumer is slow.
 *
 * @param sensitivityMultiplier Scales the raw angle → pixel mapping. Default 1.0.
 * @param maxOffsetPixels       Hard clamp on dot displacement in either axis. Default 80f.
 */
class MotionEngine(
    private var sensitivityMultiplier: Float = 1.0f,
    private val maxOffsetPixels: Float = 80f
) {
    // Filters — reused across frames to maintain state
    private val lowPassFilter = LowPassFilter(alpha = 0.80f)
    private val sensorFusion = SensorFusion()

    // Pre-allocated working arrays (avoids heap allocation per sensor event)
    private val filteredAccel = FloatArray(3)

    // Scaling factor: maps 1 degree of tilt to this many pixels of dot offset.
    // Tuned so a typical road vibration (~5°) produces a visible but not jarring movement.
    private val degreesPerPixel = 0.6f

    /**
     * Returns a cold [Flow] of [MotionState] derived from the provided [sensorFlow].
     * Collection and processing happen on [Dispatchers.Default].
     */
    fun motionStateFlow(sensorFlow: Flow<SensorData>): Flow<MotionState> =
        sensorFlow
            .conflate()                              // Drop stale readings if downstream is slow
            .map { sensorData -> processSample(sensorData) }
            .flowOn(Dispatchers.Default)             // Keep heavy work off the Main thread

    private fun processSample(data: SensorData): MotionState {
        // Step 1: Smooth the accelerometer data
        lowPassFilter.apply(data.accelerometer, filteredAccel)

        // Step 2: Fuse sensors into pitch / roll (index 0 = pitch, 1 = roll, 2 = yaw)
        val angles = sensorFusion.update(
            data.copy(accelerometer = filteredAccel.copyOf())
        )

        val pitch = angles[0]  // Tilt forward/backward → Y-axis displacement
        val roll = angles[1]   // Tilt left/right      → X-axis displacement

        // Step 3: Convert angles to pixel offsets
        val rawOffsetX = roll * degreesPerPixel * sensitivityMultiplier
        val rawOffsetY = pitch * degreesPerPixel * sensitivityMultiplier

        val clampedOffsetX = rawOffsetX.coerceIn(-maxOffsetPixels, maxOffsetPixels)
        val clampedOffsetY = rawOffsetY.coerceIn(-maxOffsetPixels, maxOffsetPixels)

        // Step 4: Intensity 0..1 based on how far from centre we are
        val magnitude = sqrt(clampedOffsetX * clampedOffsetX + clampedOffsetY * clampedOffsetY)
        val intensity = min(magnitude / maxOffsetPixels, 1f)

        return MotionState(
            dotsOffsetX = clampedOffsetX,
            dotsOffsetY = clampedOffsetY,
            intensity = intensity,
            timestamp = data.timestampNanos
        )
    }

    /**
     * Update the sensitivity at runtime without restarting the flow.
     * Thread-safe write because [sensitivityMultiplier] is only read on [Dispatchers.Default]
     * and this field is written from the same thread (or before collection starts).
     */
    fun updateSensitivity(value: Float) {
        sensitivityMultiplier = value.coerceIn(0.1f, 5.0f)
    }

    /** Reset all filter states — call when the user requests calibration. */
    fun calibrate() {
        filteredAccel.fill(0f)
        sensorFusion.reset()
    }

    /** Returns true if the two offsets are close enough to skip a view update. */
    fun isChangeSignificant(oldState: MotionState, newState: MotionState): Boolean {
        return abs(newState.dotsOffsetX - oldState.dotsOffsetX) > 0.5f ||
                abs(newState.dotsOffsetY - oldState.dotsOffsetY) > 0.5f
    }
}
