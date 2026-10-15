package com.zikra.app.qibla

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompassMathTest {

    @Test
    fun anglesWrapAroundTheCircle() {
        assertEquals(350.0, CompassMath.normalize(-10.0), 1e-9)
        assertEquals(10.0, CompassMath.normalize(370.0), 1e-9)
        assertEquals(20.0, CompassMath.signedDelta(10.0, 350.0), 1e-9) // 350 -> 10 is 20° clockwise
        assertEquals(-20.0, CompassMath.signedDelta(350.0, 10.0), 1e-9)
        assertEquals(180.0, CompassMath.signedDelta(180.0, 0.0), 1e-9)
    }

    @Test
    fun alignedWithinThreeDegreesAcrossNorth() {
        assertTrue(CompassMath.isAligned(1.0, 359.0))
        assertFalse(CompassMath.isAligned(10.0, 0.0))
    }

    @Test
    fun smoothingNeverSwingsThroughSouthAtNorth() {
        val s = CompassMath.smooth(359.0, 1.0, alpha = 0.5)
        assertTrue("got $s", s > 359.5 || s < 0.5)
        assertEquals(42.0, CompassMath.smooth(null, 42.0), 1e-9)
    }

    @Test
    fun declinationComesFromTheBuiltInWorldMagneticModel() {
        val t = ZonedDateTime.parse("2026-06-01T00:00:00Z").toInstant().toEpochMilli()
        // Published WMM values: New York about 13° west, Sydney about 13° east, Makkah about 3-4° east.
        assertTrue(CompassMath.declination(40.71, -74.01, t) in -14.5..-11.5)
        assertTrue(CompassMath.declination(-33.87, 151.21, t) in 11.5..14.5)
        assertTrue(CompassMath.declination(21.42, 39.83, t) in 2.0..5.0)
    }

    private fun aboutZ(degrees: Double): FloatArray {
        val half = Math.toRadians(degrees) / 2
        return floatArrayOf(0f, 0f, sin(half).toFloat(), cos(half).toFloat())
    }

    @Test
    fun flatPhoneAzimuthFromRotationVector() {
        assertEquals(0.0, CompassMath.azimuthFromRotationVector(aboutZ(0.0), Surface.ROTATION_0), 0.5)
        // Turning the phone anticlockwise (seen from above) by 90° points its top to the west.
        assertEquals(270.0, CompassMath.azimuthFromRotationVector(aboutZ(90.0), Surface.ROTATION_0), 0.5)
        assertEquals(90.0, CompassMath.azimuthFromRotationVector(aboutZ(-90.0), Surface.ROTATION_0), 0.5)
    }

    @Test
    fun landscapeShiftsTheScreenTopByAQuarterTurn() {
        val portrait = CompassMath.azimuthFromRotationVector(aboutZ(30.0), Surface.ROTATION_0)
        val landscape = CompassMath.azimuthFromRotationVector(aboutZ(30.0), Surface.ROTATION_90)
        assertEquals(90.0, kotlin.math.abs(CompassMath.signedDelta(landscape, portrait)), 0.5)
    }

    @Test
    fun fieldStrength() {
        assertEquals(50.0, CompassMath.fieldStrength(floatArrayOf(30f, 40f, 0f)), 1e-6)
        assertTrue(50.0 in CompassMath.NORMAL_FIELD_MICROTESLA)
        assertFalse(150.0 in CompassMath.NORMAL_FIELD_MICROTESLA)
    }
}
