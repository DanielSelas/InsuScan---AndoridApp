package com.example.insuscan.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class OrientationHelper(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null

    // Callback: (pitch, roll, isLevel)
    var onOrientationChanged: ((Float, Float, Boolean) -> Unit)? = null

    companion object {
        private const val TAG = "OrientationHelper"
        private const val MAX_TILT_DEGREES = 15.0 // Allow small tilt
    }

    fun start() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val gx = event.values[0]
            val gy = event.values[1]
            val gz = event.values[2]

            // Calculate tilt angle from vertical (Z-axis)
            // Ideally, gz should be ~9.8 (or -9.8) and gx, gy ~0 when flat.
            // Tilt angle = acos(|gz| / |g|) * (180 / PI)
            
            val norm = Math.sqrt((gx * gx + gy * gy + gz * gz).toDouble())
            val zNorm = Math.abs(gz) / norm
            
            // Allow for some floating point noise, clamp to [-1, 1]
            val zClamped = zNorm.coerceIn(-1.0, 1.0)
            
            // Angle from horizontal (flat)
            // If zClamped is 1.0 -> Angle is 0.
            val tiltAngle = Math.toDegrees(Math.acos(zClamped)).toFloat()

            // Threshold: 15 degrees
            val isLevel = tiltAngle < MAX_TILT_DEGREES
            
            // X/Y tilt components (Pitch/Roll estimate) for debugging
            val pitch = Math.toDegrees(Math.atan2(gx.toDouble(), Math.sqrt((gy * gy + gz * gz).toDouble()))).toFloat()
            val roll = Math.toDegrees(Math.atan2(gy.toDouble(), Math.sqrt((gx * gx + gz * gz).toDouble()))).toFloat()

            onOrientationChanged?.invoke(pitch, roll, isLevel)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
