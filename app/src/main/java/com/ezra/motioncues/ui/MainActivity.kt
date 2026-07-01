package com.ezra.motioncues.ui

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ezra.motioncues.R
import com.ezra.motioncues.databinding.ActivityMainBinding
import com.ezra.motioncues.overlay.OverlayPermissionHelper
import com.ezra.motioncues.utils.Logger
import kotlinx.coroutines.launch

/**
 * Main and only Activity of MotionCues.
 *
 * Responsibilities:
 * - Observe [MainViewModel] state flows and update the UI.
 * - Handle the overlay permission flow (request → return → retry).
 * - Forward user interactions (sliders, buttons) to the ViewModel.
 *
 * Heavy work is delegated to [MainViewModel] and the background service.
 * Nothing on the Main thread should block.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupOverlayControlButtons()
        setupAppearanceControls()
        setupMotionControls()
        observeViewModel()
    }

    @Deprecated("Still required for overlay permission result callback on API < 33")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OverlayPermissionHelper.overlayRequestCode()) {
            Logger.d(TAG, "Returned from overlay permission screen")
            viewModel.recheckPermissionAndStart()
        }
    }

    // ── Button / control setup ────────────────────────────────────────────────

    private fun setupOverlayControlButtons() {
        binding.btnStartOverlay.setOnClickListener {
            if (!OverlayPermissionHelper.hasOverlayPermission(this)) {
                OverlayPermissionHelper.requestOverlayPermission(this)
            } else {
                viewModel.startOverlay()
            }
        }

        binding.btnStopOverlay.setOnClickListener {
            viewModel.stopOverlay()
        }

        binding.btnCalibrate.setOnClickListener {
            viewModel.calibrateSensor()
            showToast(getString(R.string.calibration_done))
        }
    }

    private fun setupAppearanceControls() {
        // Dot size slider
        binding.sliderDotSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.updateDotSize(value)
        }

        // Alpha slider
        binding.sliderAlpha.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.updateDotAlpha(value.toInt())
        }

        // Color picker button — shows a simple predefined color dialog
        binding.btnColorPicker.setOnClickListener {
            showColorPickerDialog()
        }
    }

    private fun setupMotionControls() {
        binding.sliderSensitivity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.updateSensitivity(value)
        }
    }

    // ── ViewModel observation ─────────────────────────────────────────────────

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.overlayStatus.collect { status ->
                updateOverlayStatusUi(status)
            }
        }

        lifecycleScope.launch {
            viewModel.sensorAvailability.collect { availability ->
                val statusText = buildString {
                    append(getString(R.string.sensor_accelerometer))
                    append(": ")
                    append(if (availability.hasAccelerometer) getString(R.string.available) else getString(R.string.unavailable))
                    append("\n")
                    append(getString(R.string.sensor_gyroscope))
                    append(": ")
                    append(if (availability.hasGyroscope) getString(R.string.available) else getString(R.string.unavailable))
                }
                binding.tvSensorStatus.text = statusText
            }
        }

        lifecycleScope.launch {
            viewModel.sensitivity.collect { value ->
                binding.sliderSensitivity.value = value.coerceIn(
                    binding.sliderSensitivity.valueFrom,
                    binding.sliderSensitivity.valueTo
                )
            }
        }

        lifecycleScope.launch {
            viewModel.dotSize.collect { value ->
                binding.sliderDotSize.value = value.coerceIn(
                    binding.sliderDotSize.valueFrom,
                    binding.sliderDotSize.valueTo
                )
            }
        }

        lifecycleScope.launch {
            viewModel.dotAlpha.collect { value ->
                binding.sliderAlpha.value = value.toFloat().coerceIn(
                    binding.sliderAlpha.valueFrom,
                    binding.sliderAlpha.valueTo
                )
            }
        }
    }

    private fun updateOverlayStatusUi(status: OverlayStatus) {
        when (status) {
            is OverlayStatus.Inactive -> {
                binding.tvOverlayStatus.text = getString(R.string.status_inactive)
                binding.tvOverlayStatus.setTextColor(getColor(R.color.status_inactive))
                binding.btnStartOverlay.isEnabled = true
                binding.btnStopOverlay.isEnabled = false
            }
            is OverlayStatus.Active -> {
                binding.tvOverlayStatus.text = getString(R.string.status_active)
                binding.tvOverlayStatus.setTextColor(getColor(R.color.status_active))
                binding.btnStartOverlay.isEnabled = false
                binding.btnStopOverlay.isEnabled = true
            }
            is OverlayStatus.PermissionRequired -> {
                binding.tvOverlayStatus.text = getString(R.string.status_permission_required)
                binding.tvOverlayStatus.setTextColor(getColor(R.color.status_warning))
                binding.btnStartOverlay.isEnabled = true
                binding.btnStopOverlay.isEnabled = false
                showPermissionExplanationDialog()
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private fun showColorPickerDialog() {
        val colors = arrayOf(
            getString(R.string.color_white),
            getString(R.string.color_yellow),
            getString(R.string.color_cyan),
            getString(R.string.color_green),
            getString(R.string.color_orange),
            getString(R.string.color_black)
        )
        val colorValues = intArrayOf(
            Color.WHITE,
            Color.YELLOW,
            Color.CYAN,
            Color.GREEN,
            0xFFFF8C00.toInt(), // Dark Orange
            Color.BLACK
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_pick_color))
            .setItems(colors) { _, which ->
                viewModel.updateDotColor(colorValues[which])
                binding.btnColorPicker.setBackgroundColor(colorValues[which])
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_permission_title))
            .setMessage(getString(R.string.dialog_permission_message))
            .setPositiveButton(getString(R.string.dialog_permission_open_settings)) { _, _ ->
                OverlayPermissionHelper.requestOverlayPermission(this)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
