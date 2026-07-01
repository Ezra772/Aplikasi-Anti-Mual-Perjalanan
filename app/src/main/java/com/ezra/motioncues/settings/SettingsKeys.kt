package com.ezra.motioncues.settings

/**
 * Single source of truth for all SharedPreferences key constants.
 *
 * Centralising keys here prevents typos spread across multiple files and
 * makes it trivial to rename a key (change it here only).
 */
object SettingsKeys {
    /** Sensitivity multiplier [Float]. Range 0.1–5.0, default 1.0f. */
    const val KEY_SENSITIVITY = "sensitivity"

    /** Dot radius in dp [Float]. Range 4–20, default 8f. */
    const val KEY_DOT_SIZE = "dot_size"

    /** Dot opacity [Int]. Range 0–255, default 180. */
    const val KEY_DOT_ALPHA = "dot_alpha"

    /** Dot fill color [Int] as ARGB. Default Color.WHITE. */
    const val KEY_DOT_COLOR = "dot_color"

    /** Whether the overlay service should be running [Boolean]. Default false. */
    const val KEY_OVERLAY_ENABLED = "overlay_enabled"

    // ── Defaults ─────────────────────────────────────────────────────────────
    const val DEFAULT_SENSITIVITY = 1.2f
    const val DEFAULT_DOT_SIZE = 8f
    const val DEFAULT_DOT_ALPHA = 180
    const val DEFAULT_DOT_COLOR = 0xFFFFFFFF.toInt() // Opaque white
    const val DEFAULT_OVERLAY_ENABLED = false
}
