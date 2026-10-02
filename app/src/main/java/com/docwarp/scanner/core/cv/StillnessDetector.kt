package com.docwarp.scanner.core.cv

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Dual-modal stillness detector: Combines hardware IMU sensors (Gyroscope / Acceleration)
 * with OpenCV optical frame differencing. Emits a continuous stability progress [0.0f..1.0f]
 * across a 500ms stabilization window for the circular shutter sweep ring.
 */
class StillnessDetector(
    context: Context,
    private val onStillnessStabilized: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private var previousPreviewFrame: Bitmap? = null

    // Thresholds
    private val gyroThreshold = 0.08f         // rad/s
    private val accelThreshold = 0.25f        // m/s^2
    private val visualMotionThreshold = 0.035f // 3.5% pixel change
    private val requiredStableDurationMs = 500L
    private val minCaptureIntervalMs = 2500L

    // Live metrics
    private var lastGyroMagnitude = 0.0f
    private var lastAccelMagnitude = 0.0f
    private var lastVisualMotion = 0.0f

    private var stableStartTime: Long = 0L
    private var lastCaptureTime: Long = 0L
    private var isMonitoring = false

    // State flow for Compose UI: 0.0f (moving) to 1.0f (fully stabilized)
    private val _stabilityProgress = MutableStateFlow(0.0f)
    val stabilityProgress: StateFlow<Float> = _stabilityProgress.asStateFlow()

    private val _isLockedAndStill = MutableStateFlow(false)
    val isLockedAndStill: StateFlow<Boolean> = _isLockedAndStill.asStateFlow()

    fun start() {
        if (isMonitoring) return
        isMonitoring = true
        stableStartTime = 0L
        _stabilityProgress.value = 0.0f
        _isLockedAndStill.value = false

        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        if (!isMonitoring) return
        isMonitoring = false
        sensorManager.unregisterListener(this)
        previousPreviewFrame?.recycle()
        previousPreviewFrame = null
        _stabilityProgress.value = 0.0f
        _isLockedAndStill.value = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isMonitoring) return

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                lastGyroMagnitude = sqrt(x * x + y * y + z * z)
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                lastAccelMagnitude = sqrt(x * x + y * y + z * z)
            }
        }

        evaluateStillness()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun processPreviewFrame(downsampledFrame: Bitmap) {
        if (!isMonitoring) return

        val prev = previousPreviewFrame
        if (prev != null && prev.width == downsampledFrame.width && prev.height == downsampledFrame.height) {
            try {
                lastVisualMotion = NativeCvEngine.computeFrameMotion(downsampledFrame, prev, 18)
            } catch (e: Exception) {
                Log.e(TAG, "Error computing frame motion: ${e.message}")
            }
        }

        previousPreviewFrame?.recycle()
        previousPreviewFrame = downsampledFrame.copy(downsampledFrame.config ?: Bitmap.Config.ARGB_8888, false)

        evaluateStillness()
    }

    private fun evaluateStillness() {
        val now = SystemClock.uptimeMillis()

        if (now - lastCaptureTime < minCaptureIntervalMs) {
            stableStartTime = 0L
            _stabilityProgress.value = 0.0f
            _isLockedAndStill.value = false
            return
        }

        val isSensorStill = (lastGyroMagnitude < gyroThreshold) && (lastAccelMagnitude < accelThreshold)
        val isVisualStill = (lastVisualMotion < visualMotionThreshold)

        if (isSensorStill && isVisualStill) {
            if (stableStartTime == 0L) {
                stableStartTime = now
                _stabilityProgress.value = 0.0f
                _isLockedAndStill.value = false
            } else {
                val elapsed = now - stableStartTime
                val progress = (elapsed.toFloat() / requiredStableDurationMs).coerceIn(0.0f, 1.0f)
                _stabilityProgress.value = progress

                if (progress >= 1.0f) {
                    _isLockedAndStill.value = true
                    lastCaptureTime = now
                    stableStartTime = 0L
                    onStillnessStabilized()
                }
            }
        } else {
            stableStartTime = 0L
            _stabilityProgress.value = 0.0f
            _isLockedAndStill.value = false
        }
    }

    companion object {
        private const val TAG = "StillnessDetector"
    }
}
