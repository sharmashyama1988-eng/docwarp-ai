package com.docwarp.scanner.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Sensor-backed horizontal leveler that computes device tilt and roll angles.
 * Used in Screen 1 viewfinder to indicate if the camera is held flat/parallel to the paper.
 */
class DeviceLeveler(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _rollDegrees = MutableStateFlow(0f)
    val rollDegrees: StateFlow<Float> = _rollDegrees.asStateFlow()

    private val _pitchDegrees = MutableStateFlow(0f)
    val pitchDegrees: StateFlow<Float> = _pitchDegrees.asStateFlow()

    private val _isLevel = MutableStateFlow(false)
    val isLevel: StateFlow<Boolean> = _isLevel.asStateFlow()

    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        gravitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isRunning) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        // Pitch & roll calculation relative to phone facing downward onto a flat book
        val roll = Math.toDegrees(atan2(ax.toDouble(), sqrt((ay * ay + az * az).toDouble()))).toFloat()
        val pitch = Math.toDegrees(atan2(ay.toDouble(), sqrt((ax * ax + az * az).toDouble()))).toFloat()

        _rollDegrees.value = roll
        _pitchDegrees.value = pitch

        // Device is considered level when roll and pitch are within ±2.0 degrees
        _isLevel.value = abs(roll) < 2.0f && abs(pitch) < 2.0f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
