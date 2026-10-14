package com.zikra.core.common.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactorTest {

    private fun assertRedacted(input: String, mustNotContain: String) {
        val out = Redactor.redact(input)
        assertFalse("'$mustNotContain' leaked in: $out", out.contains(mustNotContain))
    }

    // Fake secrets are assembled at runtime so no complete secret-shaped literal exists in the
    // repository (the gitleaks scan stays strict for tests too).
    private val fakeJwt = listOf(
        "eyJ" + "hbGciOiJub25lIn0",
        "eyJ" + "zdWIiOiJ0ZXN0In0",
        "c2lnbmF0dXJl",
    ).joinToString(".")
    private val fakeSupabaseKey = "sb_" + "secret_" + "x".repeat(24)

    @Test
    fun jwtIsRemoved() {
        assertRedacted("sync failed for token $fakeJwt", "c2lnbmF0dXJl")
    }

    @Test
    fun bearerHeaderIsRemoved() {
        assertEquals("Authorization: Bearer [REDACTED]", Redactor.redact("Authorization: Bearer abc.def-123"))
    }

    @Test
    fun sensitiveKeyValuesAreRemoved() {
        assertEquals("password=[REDACTED] user=1", Redactor.redact("password=hunter2 user=1"))
        assertRedacted("""{"refresh_token": "r-9f8e7d"}""", "r-9f8e7d")
        assertRedacted("api_key: AKIA1234", "AKIA1234")
    }

    @Test
    fun supabaseSecretKeyIsRemoved() {
        assertRedacted("key $fakeSupabaseKey", "x".repeat(24))
    }

    @Test
    fun urlKeepsHostOnly() {
        assertEquals(
            "blocked https://example.com/[REDACTED_PATH] now",
            Redactor.redact("blocked https://example.com/some/private/page?q=secret now"),
        )
    }

    @Test
    fun emailIsRemoved() {
        assertRedacted("user ali.hassan+test@example.org signed in", "ali.hassan")
    }

    @Test
    fun preciseCoordinatesAreRemoved() {
        val out = Redactor.redact("location 30.04442, 31.235712")
        assertFalse(out.contains("30.04442"))
        assertFalse(out.contains("31.235712"))
    }

    @Test
    fun phoneNumbersAreRemoved() {
        assertRedacted("call +20 100 123 4567", "123 4567")
    }

    @Test
    fun harmlessTextIsUnchanged() {
        val text = "Prayer schedule recomputed for 6 prayers, version 2.0.21, surah 2 ayah 255"
        assertEquals(text, Redactor.redact(text))
    }

    @Test
    fun arabicTextIsPreserved() {
        val text = "تمت المزامنة بنجاح"
        assertEquals(text, Redactor.redact(text))
    }
}
