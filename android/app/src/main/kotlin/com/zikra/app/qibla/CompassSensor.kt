package com.zikra.app.qibla

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** How far the magnetometer can be trusted, from the platform's accuracy reports. */
enum class CompassAccuracy { HIGH, MEDIUM, LOW, UNRELIABLE }

data class CompassReading(
    /** Degrees from MAGNETIC north of the top of the screen. */
    val magneticAzimuth: Double,
    val accuracy: CompassAccuracy,
    /** Field strength in µT, or null before the first magnetometer sample. */
    val fieldMicroTesla: Double?,
)

/** Source of compass readings; [available] is false on phones without a magnetometer. */
interface Compass {
    val available: Boolean

    fun readings(displayRotation: () -> Int): Flow<CompassReading>
}

/**
 * Platform sensors only (WP 2.6). Prefers the fused rotation vector (steadier); falls back to
 * accelerometer + magnetometer. Listens only while collected, i.e. while the Qibla screen is visible.
 */
class AndroidCompass(context: Context) : Compass {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val magnetometer: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rotationVector: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    override val available: Boolean = magnetometer != null && (rotationVector != null || accelerometer != null)

    override fun readings(displayRotation: () -> Int): Flow<CompassReading> = callbackFlow {
        val manager = sensors
        if (!available || manager == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            var accuracy = CompassAccuracy.MEDIUM
            var field: Double? = null
            val gravity = FloatArray(AXES)
            val geomagnetic = FloatArray(AXES)
            var haveGravity = false
            var haveGeomagnetic = false

            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> emit(
                        CompassMath.azimuthFromRotationVector(event.values, displayRotation())
                    )
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        field = CompassMath.fieldStrength(event.values)
                        event.values.copyInto(geomagnetic, endIndex = AXES)
                        haveGeomagnetic = true
                        if (rotationVector == null) fromAccelerometerAndMagnetometer()
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        event.values.copyInto(gravity, endIndex = AXES)
                        haveGravity = true
                    }
                }
            }

            private fun fromAccelerometerAndMagnetometer() {
                if (!haveGravity || !haveGeomagnetic) return
                val rotation = FloatArray(MATRIX_SIZE)
                if (SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)) {
                    emit(CompassMath.azimuthFromRotationMatrix(rotation, displayRotation()))
                }
            }

            private fun emit(azimuth: Double) {
                trySend(CompassReading(azimuth, accuracy, field))
            }

            override fun onAccuracyChanged(sensor: Sensor, value: Int) {
                if (sensor.type != Sensor.TYPE_MAGNETIC_FIELD && sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                accuracy = when (value) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassAccuracy.LOW
                    else -> CompassAccuracy.UNRELIABLE
                }
            }
        }
        val rate = SensorManager.SENSOR_DELAY_UI
        manager.registerListener(listener, magnetometer, rate)
        if (rotationVector != null) {
            manager.registerListener(listener, rotationVector, rate)
        } else {
            manager.registerListener(listener, accelerometer, rate)
        }
        awaitClose { manager.unregisterListener(listener) }
    }

    private companion object {
        const val AXES = 3
        const val MATRIX_SIZE = 9
    }
}

/** Display rotation constant used when no window is known (tests). */
const val PORTRAIT = Surface.ROTATION_0
