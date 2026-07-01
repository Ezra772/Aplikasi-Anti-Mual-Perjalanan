package com.ezra.motioncues.motion

import kotlin.math.sqrt

/**
 * Immutable snapshot of the computed motion state at one point in time.
 *
 * @param dotsOffsetX  Horizontal displacement for the overlay dots (pixels, ±maxOffset).
 * @param dotsOffsetY  Vertical displacement for the overlay dots (pixels, ±maxOffset).
 * @param intensity    Overall motion strength in the range [0.0, 1.0].
 * @param timestamp    System time in nanoseconds when this state was computed.
 */
data class MotionState(
    val dotsOffsetX: Float,
    val dotsOffsetY: Float,
    val intensity: Float,
    val timestamp: Long
) {
    companion object {
        /** Zero-motion state used before any sensor data arrives. */
        val IDLE = MotionState(
            dotsOffsetX = 0f,
            dotsOffsetY = 0f,
            intensity = 0f,
            timestamp = 0L
        )
    }

    /** Euclidean magnitude of the displacement vector. */
    val displacementMagnitude: Float
        get() = sqrt(dotsOffsetX * dotsOffsetX + dotsOffsetY * dotsOffsetY)
}
