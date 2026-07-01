package com.ezra.motioncues.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.ezra.motioncues.sensor.MotionSensorManager
import com.ezra.motioncues.sensor.SensorSamplingRate

/**
 * Monitors battery level and charging state, automatically reducing sensor
 * polling when the battery is critically low to conserve power.
 *
 * Behaviour:
 *   - Battery < 15 % AND not charging → switch to [SensorSamplingRate.NORMAL] (~200 ms)
 *   - Otherwise                       → restore [SensorSamplingRate.GAME] (~10 ms)
 *
 * Must call [register] after the service is created and [unregister] in onDestroy.
 */
class BatteryAwareScheduler(private val sensorManager: MotionSensorManager) {

    companion object {
        private const val TAG = "BatteryAwareScheduler"
        private const val CRITICAL_BATTERY_THRESHOLD = 15
    }

    private val normalRate = SensorSamplingRate.NORMAL
    private val fullRate = SensorSamplingRate.GAME

    private var isRegistered = false
    private var currentRate = fullRate

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val batteryPct = if (scale > 0) (level * 100) / scale else 100

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val targetRate = if (batteryPct < CRITICAL_BATTERY_THRESHOLD && !isCharging) {
                Logger.w(TAG, "Battery critically low ($batteryPct%) — reducing sensor rate")
                normalRate
            } else {
                fullRate
            }

            if (targetRate != currentRate) {
                currentRate = targetRate
                sensorManager.updateSamplingRate(targetRate)
                Logger.d(TAG, "Sensor rate changed to $targetRate (battery=$batteryPct%, charging=$isCharging)")
            }
        }
    }

    /**
     * Registers the battery receiver. Safe to call multiple times (idempotent).
     */
    fun register(context: Context) {
        if (isRegistered) return
        context.registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        isRegistered = true
        Logger.d(TAG, "Battery receiver registered")
    }

    /**
     * Unregisters the battery receiver. Safe to call when not registered.
     */
    fun unregister(context: Context) {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            Logger.w(TAG, "Battery receiver already unregistered")
        }
        isRegistered = false
        Logger.d(TAG, "Battery receiver unregistered")
    }
}
