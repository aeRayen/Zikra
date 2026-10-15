package com.zikra.app.qibla

import android.hardware.GeomagneticField
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure compass maths (WP 2.6), separated from sensors so it can be tested exactly. */
object CompassMath {

    /** Earth's field is roughly 25-65 µT; far outside that, a magnet or metal is disturbing the compass. */
    val NORMAL_FIELD_MICROTESLA = 20.0..70.0

    /** Within this many degrees the phone counts as pointing at the Qibla. */
    const val ALIGNED_DEGREES = 3.0

    private const val FULL_TURN = 360.0
    private const val HALF_TURN = 180.0

    /** Any angle into [0, 360). */
    fun normalize(degrees: Double): Double = ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN

    /** Signed difference target - current in (-180, 180]: positive = turn clockwise. */
    fun signedDelta(target: Double, current: Double): Double {
        val d = normalize(target - current)
        return if (d > HALF_TURN) d - FULL_TURN else d
    }

    fun isAligned(qiblaTrue: Double, headingTrue: Double): Boolean =
        abs(signedDelta(qiblaTrue, headingTrue)) <= ALIGNED_DEGREES

    /**
     * Magnetic declination (degrees, east positive) from the World Magnetic Model built into Android,
     * so it works offline. True heading = magnetic heading + declination.
     */
    fun declination(latitude: Double, longitude: Double, epochMs: Long): Double =
        GeomagneticField(latitude.toFloat(), longitude.toFloat(), 0f, epochMs).declination.toDouble()

    /**
     * Magnetic azimuth (degrees from magnetic north) of the top of the screen, from a rotation-vector
     * sample, corrected for the display rotation so the arrow is right in landscape too.
     */
    fun azimuthFromRotationVector(rotationVector: FloatArray, displayRotation: Int): Double {
        val rotation = FloatArray(MATRIX_SIZE)
        SensorManager.getRotationMatrixFromVector(rotation, rotationVector)
        return azimuthFromRotationMatrix(rotation, displayRotation)
    }

    fun azimuthFromRotationMatrix(rotation: FloatArray, displayRotation: Int): Double {
        val (axisX, axisY) = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        val remapped = FloatArray(MATRIX_SIZE)
        SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped)
        val orientation = FloatArray(ORIENTATION_SIZE)
        SensorManager.getOrientation(remapped, orientation)
        return normalize(Math.toDegrees(orientation[0].toDouble()))
    }

    fun fieldStrength(values: FloatArray): Double =
        sqrt(values.take(AXES).sumOf { (it * it).toDouble() })

    /**
     * Low-pass filter on a circle (so 359° -> 1° does not swing through 180°). [alpha] in (0, 1]:
     * smaller = smoother but slower.
     */
    fun smooth(previous: Double?, sample: Double, alpha: Double = DEFAULT_ALPHA): Double {
        if (previous == null) return normalize(sample)
        val p = Math.toRadians(previous)
        val s = Math.toRadians(sample)
        val x = (1 - alpha) * cos(p) + alpha * cos(s)
        val y = (1 - alpha) * sin(p) + alpha * sin(s)
        return normalize(Math.toDegrees(atan2(y, x)))
    }

    private const val MATRIX_SIZE = 9
    private const val ORIENTATION_SIZE = 3
    private const val AXES = 3
    private const val DEFAULT_ALPHA = 0.15
}
