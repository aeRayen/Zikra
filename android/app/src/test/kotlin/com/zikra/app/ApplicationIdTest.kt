package com.zikra.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The applicationId can never change after the first Google Play upload (ADR-022).
 * This test makes any accidental change fail CI.
 */
class ApplicationIdTest {
    @Test
    fun applicationIdIsPermanentValue() {
        assertEquals("com.zikra.app", BuildConfig.APPLICATION_ID)
    }
}
