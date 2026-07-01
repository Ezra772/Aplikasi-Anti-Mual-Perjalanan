package com.ezra.motioncues

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.ezra.motioncues.utils.Logger

/**
 * Application entry-point.
 * Responsible for one-time setup that must happen before any Activity or Service starts,
 * such as creating the persistent notification channel used by the foreground service.
 */
class MotionCuesApplication : Application() {

    companion object {
        const val OVERLAY_NOTIFICATION_CHANNEL_ID = "motion_cues_overlay"
        private const val TAG = "MotionCuesApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Logger.d(TAG, "Application starting")
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            OVERLAY_NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW // Low priority: no sound, minimal interruption
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        Logger.d(TAG, "Notification channel created: $OVERLAY_NOTIFICATION_CHANNEL_ID")
    }
}
