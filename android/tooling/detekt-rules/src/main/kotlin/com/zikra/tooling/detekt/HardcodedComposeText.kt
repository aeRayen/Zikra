package com.zikra.tooling.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Flags user-visible text written as a string literal in Compose code (I18N_SPEC_AR.md §3).
 * Android Lint's HardcodedText only covers XML layouts, so Compose needs this rule.
 *
 * Reported: a literal containing a letter passed as
 *  - the first positional argument of Text/BasicText, or
 *  - a named argument `text`, `label`, `placeholder`, `title` or `contentDescription` of any call.
 * Ignored: @Preview functions, and literals with no letters ("•", "", "12").
 * Use stringResource(R.string.…) instead.
 */
class HardcodedComposeText(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "User-visible text must come from string resources (stringResource), not a literal.",
        debt = Debt.FIVE_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (isInsidePreview(expression)) return
        val callee = expression.calleeExpression?.text ?: return

        expression.valueArguments.forEachIndexed { index, argument ->
            val name = argument.getArgumentName()?.asName?.asString()
            // `label` on animation APIs is a debug name for tooling, not user-visible text.
            val isAnimationLabel = name == "label" && ANIMATION_CALL.matches(callee)
            val isText = !isAnimationLabel &&
                if (name != null) name in TEXT_PARAMETERS else index == 0 && callee in TEXT_COMPOSABLES
            val literal = argument.getArgumentExpression() as? KtStringTemplateExpression
            if (isText && literal != null && literal.hasLetters()) {
                report(
                    CodeSmell(
                        issue,
                        Entity.from(literal),
                        "Hardcoded UI text ${literal.text} in $callee(). Use stringResource(R.string.<name>).",
                    ),
                )
            }
        }
    }

    private fun KtStringTemplateExpression.hasLetters(): Boolean = entries
        .filterIsInstance<KtLiteralStringTemplateEntry>()
        .any { entry -> entry.text.any { it.isLetter() } }

    private fun isInsidePreview(expression: KtCallExpression): Boolean = generateSequence(
        expression.getStrictParentOfType<KtNamedFunction>(),
    ) { it.getStrictParentOfType<KtNamedFunction>() }
        .any { fn -> fn.annotationEntries.any { it.shortName?.asString()?.endsWith("Preview") == true } }

    private companion object {
        val TEXT_COMPOSABLES = setOf("Text", "BasicText")
        val TEXT_PARAMETERS = setOf("text", "label", "placeholder", "title", "contentDescription")
        val ANIMATION_CALL = Regex(
            "animate\\w*|remember\\w*Transition|updateTransition|Crossfade|AnimatedContent|AnimatedVisibility",
        )
    }
}
