package com.ezra.motioncues.ui

import androidx.preference.PreferenceFragmentCompat
import com.ezra.motioncues.R

/**
 * Secondary settings screen using the AndroidX Preference library.
 *
 * Inflates [R.xml.preferences] which contains SeekBarPreferences bound to the same
 * SharedPreferences file used by [com.ezra.motioncues.settings.OverlayPreferences].
 *
 * This fragment can be hosted inside a separate SettingsActivity or added as a
 * fragment transaction from [MainActivity] for a single-activity design.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: android.os.Bundle?, rootKey: String?) {
        // Point the preference framework at the same prefs file used by OverlayPreferences
        preferenceManager.sharedPreferencesName = "motion_cues_prefs"
        preferenceManager.sharedPreferencesMode = android.content.Context.MODE_PRIVATE
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
