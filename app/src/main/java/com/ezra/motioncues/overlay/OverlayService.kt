package com.ezra.motioncues.overlay

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ezra.motioncues.MotionCuesApplication
import com.ezra.motioncues.R
import com.ezra.motioncues.motion.MotionEngine
import com.ezra.motioncues.sensor.MotionSensorManager
import com.ezra.motioncues.sensor.SensorSamplingRate
import com.ezra.motioncues.settings.OverlayPreferences
import com.ezra.motioncues.ui.MainActivity
import com.ezra.motioncues.utils.BatteryAwareScheduler
import com.ezra.motioncues.utils.FrameRateUtil
import com.ezra.motioncues.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Foreground [LifecycleService] that owns and coordinates:
 *   - [MotionSensorManager] — sensor registration
 *   - [MotionEngine] — signal processing
 *   - [OverlayManager] — drawing dots on screen
 *   - [BatteryAwareScheduler] — power-saving rate adjustment
 *
 * The service is intentionally thin: it wires the components together and delegates
 * all domain logic to them. Lifecycle-aware coroutines (lifecycleScope) guarantee
 * that collection stops automatically when the service is destroyed.
 */
class OverlayService : LifecycleService() {

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var sensorManager: MotionSensorManager
    private lateinit var motionEngine: MotionEngine
    private lateinit var overlayManager: OverlayManager
    private lateinit var batteryScheduler: BatteryAwareScheduler
    private lateinit var preferences: OverlayPreferences

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Logger.d(TAG, "Screen off — pausing overlay rendering")
                    // The MotionSensorManager handles its own screen-off unregistration.
                    // We just need to stop the Choreographer by hiding the overlay temporarily.
                    // The sensor manager's internal receiver already handles unregistering sensors.
                }
                Intent.ACTION_SCREEN_ON -> {
                    Logger.d(TAG, "Screen on — overlay rendering will resume via sensor")
                    // Sensor manager's internal receiver re-registers sensors automatically.
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Logger.d(TAG, "OverlayService created")

        preferences = OverlayPreferences(this)

        val refreshRate = FrameRateUtil.getDeviceRefreshRate(this)
        val samplingRate = SensorSamplingRate.forRefreshRate(refreshRate)

        sensorManager = MotionSensorManager(this, samplingRate)
        motionEngine = MotionEngine(
            sensitivityMultiplier = preferences.getSensitivity()
        )
        overlayManager = OverlayManager(preferences)
        batteryScheduler = BatteryAwareScheduler(sensorManager)

        startForegroundWithNotification()
        showOverlay()
        startCollectingMotionState()
        registerScreenStateReceiver()
        batteryScheduler.register(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Logger.d(TAG, "onStartCommand received")
        return START_STICKY
    }

    override fun onDestroy() {
        Logger.d(TAG, "OverlayService destroying")
        batteryScheduler.unregister(this)
        unregisterScreenStateReceiver()
        overlayManager.hide()
        sensorManager.destroy()

        // Null out heavy objects to help GC
        super.onDestroy()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun startForegroundWithNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(
            this,
            MotionCuesApplication.OVERLAY_NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Logger.d(TAG, "Foreground notification shown")
    }

    private fun showOverlay() {
        overlayManager.show(this)
    }

    private fun startCollectingMotionState() {
        val sensorFlow = sensorManager.sensorDataFlow()
        val motionFlow = motionEngine.motionStateFlow(sensorFlow)

        // Collect on Main so we can update the view directly
        lifecycleScope.launch(Dispatchers.Main) {
            motionFlow.collect { state ->
                overlayManager.updateDots(state)
            }
        }

        // Also collect preference changes at runtime and apply them live
        lifecycleScope.launch(Dispatchers.Main) {
            preferences.settingsUpdateFlow().collect {
                overlayManager.refreshPreferences()
                motionEngine.updateSensitivity(preferences.getSensitivity())
            }
        }
    }

    private fun registerScreenStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    private fun unregisterScreenStateReceiver() {
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: IllegalArgumentException) {
            Logger.w(TAG, "Screen receiver already unregistered")
        }
    }
}
