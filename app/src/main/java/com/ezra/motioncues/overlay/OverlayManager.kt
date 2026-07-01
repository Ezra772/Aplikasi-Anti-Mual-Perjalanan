package com.ezra.motioncues.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Choreographer
import android.view.WindowManager
import com.ezra.motioncues.motion.MotionState
import com.ezra.motioncues.settings.OverlayPreferences
import com.ezra.motioncues.utils.Logger
import kotlin.math.abs

/**
 * Manages the lifecycle of the [OverlayDotsView] inside the system [WindowManager].
 *
 * Responsibilities:
 * - [show]: inflate the view and add it to the window.
 * - [hide]: remove the view from the window.
 * - [updateDots]: buffer the latest [MotionState] so it is picked up on the next vsync.
 * - Uses [Choreographer] to synchronise rendering with the display refresh cycle,
 *   preventing redundant [WindowManager.updateViewLayout] calls between frames.
 * - Applies a 0.5 px threshold: layout is only updated when the offset actually changed.
 */
class OverlayManager(private val preferences: OverlayPreferences) {

    private companion object {
        const val TAG = "OverlayManager"
        const val CHANGE_THRESHOLD_PX = 0.5f
    }

    private var windowManager: WindowManager? = null
    private var dotsView: OverlayDotsView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isShowing = false

    // Latest motion state buffered between Choreographer frames
    @Volatile
    private var pendingState: MotionState = MotionState.IDLE
    private var lastAppliedState: MotionState = MotionState.IDLE

    // Declared as lateinit var because the lambda body references 'choreographerCallback'
    // itself — a val cannot be referenced during its own initializer.
    private lateinit var choreographerCallback: Choreographer.FrameCallback

    init {
        choreographerCallback = Choreographer.FrameCallback { _ ->
            applyPendingState()
            // Re-schedule only while the overlay is visible
            if (isShowing) {
                Choreographer.getInstance().postFrameCallback(choreographerCallback)
            }
        }
    }

    /**
     * Inflates [OverlayDotsView], adds it to the [WindowManager] with the correct flags,
     * and starts the Choreographer render loop.
     */
    fun show(context: Context) {
        if (isShowing) {
            Logger.w(TAG, "show() called while already showing — ignoring")
            return
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        layoutParams = params

        val view = OverlayDotsView(context).also {
            // Apply current user preferences
            it.dotColor = preferences.getDotColor()
            it.dotAlpha = preferences.getDotAlpha()
            it.dotRadiusDp = preferences.getDotSize()
        }
        dotsView = view

        wm.addView(view, params)
        isShowing = true

        Choreographer.getInstance().postFrameCallback(choreographerCallback)
        Logger.d(TAG, "Overlay shown")
    }

    /**
     * Removes the overlay from the window and stops the Choreographer loop.
     */
    fun hide() {
        if (!isShowing) return
        Choreographer.getInstance().removeFrameCallback(choreographerCallback)

        dotsView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: IllegalArgumentException) {
                Logger.w(TAG, "View was already removed: ${e.message}")
            }
        }
        dotsView = null
        windowManager = null
        layoutParams = null
        isShowing = false
        Logger.d(TAG, "Overlay hidden")
    }

    /**
     * Called from the motion engine collector (Main dispatcher).
     * Buffers the state; actual view update happens on the next vsync via Choreographer.
     */
    fun updateDots(state: MotionState) {
        pendingState = state
    }

    /** Update dot appearance from preferences without hiding/reshowing. */
    fun refreshPreferences() {
        val view = dotsView ?: return
        view.dotColor = preferences.getDotColor()
        view.dotAlpha = preferences.getDotAlpha()
        view.dotRadiusDp = preferences.getDotSize()
    }

    val isActive: Boolean get() = isShowing

    // ── Private ──────────────────────────────────────────────────────────────

    private fun applyPendingState() {
        val state = pendingState
        val view = dotsView ?: return

        // Skip layout update if the change is below threshold (saves GPU / CPU)
        if (!hasSignificantChange(lastAppliedState, state)) return

        view.offsetX = state.dotsOffsetX
        view.offsetY = state.dotsOffsetY
        lastAppliedState = state
    }

    private fun hasSignificantChange(old: MotionState, new: MotionState): Boolean =
        abs(new.dotsOffsetX - old.dotsOffsetX) > CHANGE_THRESHOLD_PX ||
                abs(new.dotsOffsetY - old.dotsOffsetY) > CHANGE_THRESHOLD_PX
}
