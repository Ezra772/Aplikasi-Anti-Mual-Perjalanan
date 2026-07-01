package com.ezra.motioncues.motion

/**
 * Simple RC (resistor-capacitor) low-pass filter implemented as a rolling weighted average.
 *
 * The filter smooths out high-frequency noise from sensor readings.
 * A higher [alpha] retains more of the previous output (smoother, slower response).
 * A lower [alpha] tracks the input more closely (faster response, noisier).
 *
 * Typical value for smooth dot positioning: alpha ≈ 0.80f
 *
 * IMPORTANT: [apply] performs ZERO heap allocations. The caller must supply the
 * [output] array, which is updated in-place and returned for convenience.
 */
class LowPassFilter(private val alpha: Float) {

    init {
        require(alpha in 0f..1f) { "alpha must be in the range [0.0, 1.0], got $alpha" }
    }

    /**
     * Apply the filter to [input], writing the smoothed result into [output].
     *
     * @param input  Raw sensor reading (e.g. accelerometer x/y/z).
     * @param output Previous filtered value; updated in-place and returned.
     * @return The same [output] array after applying the filter, for chaining.
     */
    fun apply(input: FloatArray, output: FloatArray): FloatArray {
        val len = minOf(input.size, output.size)
        for (i in 0 until len) {
            output[i] = alpha * output[i] + (1f - alpha) * input[i]
        }
        return output
    }

    /** Reset all axes of [output] to zero so calibration can restart from a clean state. */
    fun reset(output: FloatArray) {
        output.fill(0f)
    }
}
