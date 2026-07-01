package com.ezra.motioncues.utils

import android.content.Context
import android.hardware.SensorManager
import android.view.WindowManager
import com.ezra.motioncues.sensor.SensorSamplingRate

/**
 * Utility for querying the device's display refresh rate and mapping it to
 * an appropriate sensor sampling delay.
 *
 * Refresh rate ≥ 90 Hz → [SensorSamplingRate.GAME] (fastest, ~10 ms)
 * Refresh rate ≥ 60 Hz → [SensorSamplingRate.UI]   (~66 ms)
 * Otherwise            → [SensorSamplingRate.NORMAL] (power-saving)
 */
object FrameRateUtil {

    /**
     * Returns the display's refresh rate in Hz.
     * Reads from [WindowManager.defaultDisplay] which is always available.
     */
    @Suppress("DEPRECATION")
    fun getDeviceRefreshRate(context: Context): Float {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return windowManager.defaultDisplay.refreshRate
    }

    /**
     * Maps a display [refreshRateHz] to the most appropriate Android sensor delay constant.
     */
    fun getSensorDelayForRefreshRate(refreshRateHz: Float): Int =
        when {
            refreshRateHz >= 90f -> SensorManager.SENSOR_DELAY_GAME
            refreshRateHz >= 60f -> SensorManager.SENSOR_DELAY_UI
            else -> SensorManager.SENSOR_DELAY_NORMAL
        }
}
