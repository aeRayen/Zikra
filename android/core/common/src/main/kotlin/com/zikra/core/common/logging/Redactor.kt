package com.zikra.core.common.logging

/**
 * Removes personal or sensitive data from any text before it reaches a log or crash report
 * (ADR-010, SECURITY_AND_PRIVACY_REVIEW T8). Rules run in order; earlier rules win.
 *
 * Redaction is a safety net, not a licence to log user content: never pass recovery notes,
 * bookmark notes, or browsing data to the logger at all.
 */
object Redactor {
    private data class Rule(val pattern: Regex, val replacement: String)

    private const val SENSITIVE_KEYS =
        "password|passwd|secret|token|api[_-]?key|authorization|refresh[_-]?token"

    private val rules = listOf(
        // JSON Web Tokens (access / ID tokens).
        Rule(Regex("""eyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]*"""), "[REDACTED_TOKEN]"),
        // Authorization headers.
        Rule(Regex("""(?i)\b(bearer|basic)\s+[A-Za-z0-9._~+/=-]+"""), "$1 [REDACTED]"),
        // key=value or "key": "value" for sensitive keys.
        // Skips values already handled above ("Bearer …" or an existing [REDACTED…] marker).
        Rule(
            Regex("""(?i)("?(?:$SENSITIVE_KEYS)"?\s*[:=]\s*)(?!(?:bearer|basic)\s|\[REDACTED)"?[^\s",}&]+"?"""),
            "$1[REDACTED]",
        ),
        // Supabase secret keys.
        Rule(Regex("""sb_secret_[A-Za-z0-9_-]+"""), "[REDACTED_KEY]"),
        // URLs: keep scheme + host only; paths and queries can reveal browsing (ADR-020).
        Rule(Regex("""(?i)\b(https?://[^\s/?#]+)[^\s]*"""), "$1/[REDACTED_PATH]"),
        // E-mail addresses.
        Rule(Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""), "[REDACTED_EMAIL]"),
        // Decimal coordinates or any decimal with 3+ fraction digits (location precision, ADR-018).
        Rule(Regex("""-?\b\d{1,3}\.\d{3,}\b"""), "[REDACTED_NUMBER]"),
        // Phone numbers and other long digit runs (7+ digits, optional separators).
        Rule(Regex("""\+?\d(?:[\s-]?\d){6,}"""), "[REDACTED_NUMBER]"),
    )

    fun redact(text: String): String = rules.fold(text) { acc, rule -> rule.pattern.replace(acc, rule.replacement) }
}
