package com.ezra.motioncues.settings

import android.content.Context
import android.content.SharedPreferences
import com.ezra.motioncues.utils.Logger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Marker class representing which setting changed.
 * Consumers can use [key] to decide how to react.
 */
data class SettingsUpdate(val key: String)

/**
 * Typed wrapper around [SharedPreferences] for MotionCues settings.
 *
 * Provides:
 * - Strongly-typed getters and setters for every setting key.
 * - A [settingsUpdateFlow] that emits a [SettingsUpdate] whenever any preference changes.
 *
 * Does NOT hold a static Context reference — it uses the application context
 * obtained once in the constructor.
 */
class OverlayPreferences(context: Context) {

    private companion object {
        const val TAG = "OverlayPreferences"
        const val PREFS_FILE = "motion_cues_prefs"
    }

    // Use application context to prevent activity leaks
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // ── Getters ───────────────────────────────────────────────────────────────

    fun getSensitivity(): Float =
        prefs.getFloat(SettingsKeys.KEY_SENSITIVITY, SettingsKeys.DEFAULT_SENSITIVITY)

    fun getDotSize(): Float =
        prefs.getFloat(SettingsKeys.KEY_DOT_SIZE, SettingsKeys.DEFAULT_DOT_SIZE)

    fun getDotAlpha(): Int =
        prefs.getInt(SettingsKeys.KEY_DOT_ALPHA, SettingsKeys.DEFAULT_DOT_ALPHA)

    fun getDotColor(): Int =
        prefs.getInt(SettingsKeys.KEY_DOT_COLOR, SettingsKeys.DEFAULT_DOT_COLOR)

    fun isOverlayEnabled(): Boolean =
        prefs.getBoolean(SettingsKeys.KEY_OVERLAY_ENABLED, SettingsKeys.DEFAULT_OVERLAY_ENABLED)

    // ── Setters ───────────────────────────────────────────────────────────────

    fun setSensitivity(value: Float) {
        prefs.edit().putFloat(SettingsKeys.KEY_SENSITIVITY, value.coerceIn(0.1f, 5.0f)).apply()
        Logger.d(TAG, "Sensitivity set to $value")
    }

    fun setDotSize(valueDp: Float) {
        prefs.edit().putFloat(SettingsKeys.KEY_DOT_SIZE, valueDp.coerceIn(4f, 20f)).apply()
    }

    fun setDotAlpha(value: Int) {
        prefs.edit().putInt(SettingsKeys.KEY_DOT_ALPHA, value.coerceIn(0, 255)).apply()
    }

    fun setDotColor(argbColor: Int) {
        prefs.edit().putInt(SettingsKeys.KEY_DOT_COLOR, argbColor).apply()
    }

    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(SettingsKeys.KEY_OVERLAY_ENABLED, enabled).apply()
    }

    // ── Reactive flow ─────────────────────────────────────────────────────────

    /**
     * Returns a cold [Flow] that emits a [SettingsUpdate] whenever any preference changes.
     * The flow is active while collected; the listener is unregistered on cancellation.
     */
    fun settingsUpdateFlow(): Flow<SettingsUpdate> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null) {
                trySend(SettingsUpdate(key))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        Logger.d(TAG, "Settings change listener registered")

        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            Logger.d(TAG, "Settings change listener unregistered")
        }
    }
}
