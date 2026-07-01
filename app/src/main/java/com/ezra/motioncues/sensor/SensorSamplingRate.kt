package com.ezra.motioncues.sensor

import android.hardware.SensorManager

/**
 * Maps a desired polling cadence to an Android SensorManager delay constant.
 * The [microseconds] value is what would be passed to [SensorManager.registerListener].
 */
enum class SensorSamplingRate(val microseconds: Int) {
    /** ~10 ms — best for fast interactive applications such as games. */
    GAME(SensorManager.SENSOR_DELAY_GAME),

    /** ~66 ms — suitable for standard 60 Hz UI refresh. */
    UI(SensorManager.SENSOR_DELAY_UI),

    /** ~200 ms — power-saving mode when battery is critically low. */
    NORMAL(SensorManager.SENSOR_DELAY_NORMAL);

    companion object {
        /**
         * Selects the most appropriate sensor rate for the given display refresh rate.
         * Devices with 90 Hz or higher screens benefit from the faster GAME rate
         * to keep motion cues in sync with their smoother display.
         */
        fun forRefreshRate(refreshRateHz: Float): SensorSamplingRate = when {
            refreshRateHz >= 90f -> GAME
            refreshRateHz >= 60f -> UI
            else -> NORMAL
        }
    }
}
