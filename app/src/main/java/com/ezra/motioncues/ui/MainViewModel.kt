package com.ezra.motioncues.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ezra.motioncues.motion.MotionEngine
import com.ezra.motioncues.overlay.OverlayPermissionHelper
import com.ezra.motioncues.overlay.OverlayService
import com.ezra.motioncues.sensor.SensorAvailability
import com.ezra.motioncues.settings.OverlayPreferences
import com.ezra.motioncues.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Represents the current lifecycle state of the overlay feature. */
sealed class OverlayStatus {
    /** The overlay is not running. */
    object Inactive : OverlayStatus()

    /** The overlay is running and displaying dots. */
    object Active : OverlayStatus()

    /** The SYSTEM_ALERT_WINDOW permission has not been granted. */
    object PermissionRequired : OverlayStatus()
}

/**
 * ViewModel for [MainActivity].
 *
 * Holds UI state, coordinates start/stop of [OverlayService],
 * and persists settings through [OverlayPreferences].
 *
 * Uses [AndroidViewModel] so we can access application context without leaking an Activity.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "MainViewModel"
    }

    private val preferences = OverlayPreferences(application)
    private val motionEngine = MotionEngine()

    // ── Exposed state ─────────────────────────────────────────────────────────

    private val _overlayStatus = MutableStateFlow<OverlayStatus>(OverlayStatus.Inactive)
    val overlayStatus: StateFlow<OverlayStatus> = _overlayStatus.asStateFlow()

    private val _sensorAvailability = MutableStateFlow(SensorAvailability.check(application))
    val sensorAvailability: StateFlow<SensorAvailability> = _sensorAvailability.asStateFlow()

    private val _sensitivity = MutableStateFlow(preferences.getSensitivity())
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    private val _dotSize = MutableStateFlow(preferences.getDotSize())
    val dotSize: StateFlow<Float> = _dotSize.asStateFlow()

    private val _dotAlpha = MutableStateFlow(preferences.getDotAlpha())
    val dotAlpha: StateFlow<Int> = _dotAlpha.asStateFlow()

    private val _dotColor = MutableStateFlow(preferences.getDotColor())
    val dotColor: StateFlow<Int> = _dotColor.asStateFlow()

    // ── Actions ───────────────────────────────────────────────────────────────

    /** Attempt to start the overlay. Sets status to [OverlayStatus.PermissionRequired] if permission is missing. */
    fun startOverlay() {
        val app = getApplication<Application>()
        if (!OverlayPermissionHelper.hasOverlayPermission(app)) {
            Logger.d(TAG, "Overlay permission not granted")
            _overlayStatus.value = OverlayStatus.PermissionRequired
            return
        }
        val intent = Intent(app, OverlayService::class.java)
        app.startForegroundService(intent)
        preferences.setOverlayEnabled(true)
        _overlayStatus.value = OverlayStatus.Active
        Logger.d(TAG, "OverlayService started")
    }

    /** Stop the overlay service. */
    fun stopOverlay() {
        val app = getApplication<Application>()
        val intent = Intent(app, OverlayService::class.java)
        app.stopService(intent)
        preferences.setOverlayEnabled(false)
        _overlayStatus.value = OverlayStatus.Inactive
        Logger.d(TAG, "OverlayService stopped")
    }

    /**
     * Re-check permission status (call after returning from the system settings screen).
     * If permission was just granted and the user had tried to start, start the service.
     */
    fun recheckPermissionAndStart() {
        val app = getApplication<Application>()
        if (OverlayPermissionHelper.hasOverlayPermission(app)) {
            startOverlay()
        } else {
            _overlayStatus.value = OverlayStatus.PermissionRequired
        }
    }

    /** Reset the motion filters to treat current orientation as the baseline zero. */
    fun calibrateSensor() {
        viewModelScope.launch {
            motionEngine.calibrate()
            Logger.d(TAG, "Sensor calibrated")
        }
    }

    fun updateSensitivity(value: Float) {
        preferences.setSensitivity(value)
        _sensitivity.value = value
    }

    fun updateDotSize(valueDp: Float) {
        preferences.setDotSize(valueDp)
        _dotSize.value = valueDp
    }

    fun updateDotAlpha(value: Int) {
        preferences.setDotAlpha(value)
        _dotAlpha.value = value
    }

    fun updateDotColor(argbColor: Int) {
        preferences.setDotColor(argbColor)
        _dotColor.value = argbColor
    }

    override fun onCleared() {
        super.onCleared()
        Logger.d(TAG, "MainViewModel cleared")
    }
}
