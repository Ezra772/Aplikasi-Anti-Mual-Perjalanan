package com.ezra.motioncues.motion

/**
 * High-pass filter implemented as the complement of a low-pass filter.
 *
 * By subtracting the low-frequency (gravity) component from the raw reading
 * we isolate rapid linear acceleration, which represents actual vehicle motion
 * rather than a constant gravitational tilt.
 *
 * Formula per axis: high = alpha * (high + input - previous)
 *
 * IMPORTANT: [apply] performs ZERO heap allocations. The caller must supply
 * both [input] and [previous] arrays.
 */
class HighPassFilter(private val alpha: Float = 0.8f) {

    init {
        require(alpha in 0f..1f) { "alpha must be in the range [0.0, 1.0], got $alpha" }
    }

    /**
     * Apply the high-pass filter.
     *
     * @param input    The raw sensor reading at the current timestep.
     * @param previous The raw sensor reading at the previous timestep (updated in-place to [input]).
     * @return A new isolated high-frequency component array (one allocation per call;
     *         the caller should reuse this if possible by switching to the in-place overload).
     */
    fun apply(input: FloatArray, previous: FloatArray): FloatArray {
        val output = FloatArray(input.size)
        applyInPlace(input, previous, output)
        return output
    }

    /**
     * In-place variant — writes the result into [output] and updates [previous] to [input].
     * Use this inside tight loops to avoid any allocations.
     */
    fun applyInPlace(input: FloatArray, previous: FloatArray, output: FloatArray) {
        val len = minOf(input.size, previous.size, output.size)
        for (i in 0 until len) {
            output[i] = alpha * (output[i] + input[i] - previous[i])
            previous[i] = input[i]
        }
    }

    /** Reset the previous reading to zero (used during calibration). */
    fun reset(previous: FloatArray) {
        previous.fill(0f)
    }
}
