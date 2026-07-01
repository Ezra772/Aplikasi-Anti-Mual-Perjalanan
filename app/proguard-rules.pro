# ProGuard rules for MotionCues
# ──────────────────────────────────────────────────────────────

# ── Android Sensor API ────────────────────────────────────────
# SensorEventListener methods are called via reflection by the Android runtime
-keep class * implements android.hardware.SensorEventListener {
    public void onSensorChanged(android.hardware.SensorEvent);
    public void onAccuracyChanged(android.hardware.Sensor, int);
}

# ── SharedPreferences ─────────────────────────────────────────
# Keep OnSharedPreferenceChangeListener implementations
-keep class * implements android.content.SharedPreferences$OnSharedPreferenceChangeListener {
    public void onSharedPreferenceChanged(android.content.SharedPreferences, java.lang.String);
}

# ── Services ──────────────────────────────────────────────────
# Keep our foreground service so it can be referenced in the manifest
-keep class com.ezra.motioncues.overlay.OverlayService { *; }

# Keep the Application class
-keep class com.ezra.motioncues.MotionCuesApplication { *; }

# ── BroadcastReceivers ────────────────────────────────────────
-keep class * extends android.content.BroadcastReceiver { *; }

# ── AndroidX Lifecycle ────────────────────────────────────────
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.LifecycleService { *; }

# ── Kotlin coroutines ─────────────────────────────────────────
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── Keep data classes used in flows ──────────────────────────
-keep class com.ezra.motioncues.sensor.SensorData { *; }
-keep class com.ezra.motioncues.motion.MotionState { *; }
-keep class com.ezra.motioncues.sensor.SensorAvailability { *; }

# ── General Android keep rules ────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
