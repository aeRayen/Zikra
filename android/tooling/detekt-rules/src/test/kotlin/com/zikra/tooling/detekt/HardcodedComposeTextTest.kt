package com.zikra.tooling.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Test

class HardcodedComposeTextTest {

    private fun findings(code: String) = HardcodedComposeText().lint(code)

    @Test
    fun flagsLiteralAsFirstArgumentOfText() {
        assertEquals(1, findings("""fun Screen() { Text("Hello") }""").size)
        assertEquals(1, findings("""fun Screen() { Text("مرحبا") }""").size)
    }

    @Test
    fun flagsNamedTextLikeParametersOnAnyCall() {
        val code = """
            fun Screen() {
                Text(text = "Hello")
                Icon(icon, contentDescription = "Back")
                OutlinedTextField(value = v, onValueChange = {}, label = "Name")
            }
        """.trimIndent()
        assertEquals(3, findings(code).size)
    }

    @Test
    fun allowsStringResourcesAndVariables() {
        val code = """
            fun Screen(name: String) {
                Text(stringResource(R.string.title))
                Text(text = name)
                Icon(icon, contentDescription = null)
            }
        """.trimIndent()
        assertEquals(0, findings(code).size)
    }

    @Test
    fun allowsLiteralsWithoutLetters() {
        assertEquals(0, findings("""fun Screen() { Text("•"); Text(""); Text("12:30") }""").size)
    }

    @Test
    fun ignoresPreviewFunctions() {
        val code = """
            @Preview
            @Composable
            fun ScreenPreview() { Text("Sample") }
        """.trimIndent()
        assertEquals(0, findings(code).size)
    }

    @Test
    fun ignoresDebugLabelsOfAnimationApis() {
        val code = """
            fun Screen() {
                val t = rememberInfiniteTransition(label = "skeleton")
                val a by t.animateFloat(0f, 1f, spec, label = "alpha")
                val c by animateColorAsState(target, label = "tint")
            }
        """.trimIndent()
        assertEquals(0, findings(code).size)
    }

    @Test
    fun stillFlagsLabelOnNonAnimationCalls() {
        assertEquals(1, findings("""fun Screen() { Chip(label = "Hello") }""").size)
    }

    @Test
    fun ignoresNonTextFirstArgumentsOfOtherCalls() {
        assertEquals(0, findings("""fun f() { log("Hello"); require(x) { "message" } }""").size)
    }
}
