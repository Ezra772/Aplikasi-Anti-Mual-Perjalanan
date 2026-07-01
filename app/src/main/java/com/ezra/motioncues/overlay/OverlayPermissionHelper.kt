package com.ezra.motioncues.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Utility for checking and requesting the SYSTEM_ALERT_WINDOW (overlay) permission.
 *
 * On Android 6.0+ this permission is not granted at install time — the user must
 * explicitly allow the app in system settings.
 */
object OverlayPermissionHelper {

    private const val REQUEST_CODE_OVERLAY = 1001

    /**
     * Returns true if the app is currently allowed to draw over other apps.
     */
    fun hasOverlayPermission(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /**
     * Opens the system settings screen where the user can grant the overlay permission.
     * The result (whether or not the user granted it) will arrive in
     * [Activity.onActivityResult] with [REQUEST_CODE_OVERLAY].
     */
    fun requestOverlayPermission(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        @Suppress("DEPRECATION")
        activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY)
    }

    /** The request code passed to [Activity.startActivityForResult]. */
    fun overlayRequestCode(): Int = REQUEST_CODE_OVERLAY
}
