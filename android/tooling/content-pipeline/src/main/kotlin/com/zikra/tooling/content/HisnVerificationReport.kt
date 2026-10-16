package com.zikra.tooling.content

import java.io.File

/** docs/review/HISN_VERIFICATION_AR.md: the result of the independent check, for anyone to re-read (ADR-042). */
object HisnVerificationReport {
    private val bookNames = mapOf(
        HadithBook.BUKHARI to "البخاري",
        HadithBook.MUSLIM to "مسلم",
        HadithBook.ABU_DAWUD to "أبو داود",
        HadithBook.TIRMIDHI to "الترمذي",
        HadithBook.NASAI to "النسائي",
        HadithBook.IBN_MAJAH to "ابن ماجه",
    )
    private val verdictNames = mapOf(
        ManualVerdict.EVIDENCE to "الحديث دليل الذكر أو فضله، لا لفظه",
        ManualVerdict.EDITION to "الحديث نفسه برقم آخر في هذه النسخة",
        ManualVerdict.WRONG_NUMBER to "رقم خاطئ في الكتاب",
        ManualVerdict.SECONDARY to "ملاحظة جانبية في الحاشية، لا مصدر الذكر",
        ManualVerdict.COPY_GAP to "لا نص لهذا الرقم في النسخة المستقلة",
    )
    private const val SNIPPET = 220
    private const val PERCENT = 100

    /** The final word on a dhikr, after the manual review. */
    private enum class Final(val label: String) {
        AUTOMATIC("مؤكَّد آليًا"),
        REVIEWED("مؤكَّد بالمراجعة"),
        CORRECTED("مؤكَّد مع تصحيح رقم"),
        OUTSIDE("خارج الكتب الستة"),
        OPEN("لم يُراجع بعد"),
    }

    private fun ReviewedVerification.final(r: DhikrVerification): Final {
        val verdicts = r.citations.mapNotNull { review.verdict(r.dhikr.id, it.citation) }
        return when {
            open.any { it.first == r.dhikr.id } -> Final.OPEN
            verdicts.any { it.verdict == ManualVerdict.WRONG_NUMBER } -> Final.CORRECTED
            r.result == DhikrCheck.CONFIRMED -> Final.AUTOMATIC
            r.result == DhikrCheck.OUTSIDE_SIX_BOOKS -> Final.OUTSIDE
            else -> Final.REVIEWED
        }
    }

    fun write(verified: ReviewedVerification, out: File) {
        val results = verified.results
        val finals = results.associate { it.dhikr.id to verified.final(it) }
        out.parentFile?.mkdirs()
        out.writeText(
            buildString {
                appendLine("# التحقق المستقل من مراجع «حصن المسلم»")
                appendLine()
                appendLine(
                    "يُولَّد هذا الملف بالأمر `hisn-verify` (ADR-042). لا يُحرَّر يدويًا؛ " +
                        "الأحكام اليدوية في `content/${ManualReview.FILE}`.",
                )
                appendLine()
                appendMethod()
                appendLine("## النتيجة النهائية")
                appendLine()
                Final.entries.forEach { f -> appendLine("- ${f.label}: ${finals.values.count { it == f }}") }
                val all = results.flatMap { it.citations }
                val matched = all.count { it.result == CitationCheck.MATCH }
                val near = all.count { it.result == CitationCheck.MATCH_NEAR }
                appendLine()
                appendLine(
                    "- أرقام الأحاديث المفحوصة: ${all.size}. مطابقة آليًا: $matched. " +
                        "مطابقة برقم مجاور: $near. رُوجعت يدويًا: ${verified.review.items.size}.",
                )
                val verses = results.sumOf { it.verses }
                val cited = results.sumOf { it.versesCited }
                appendLine("- الآيات المعروضة: $verses، منها $cited تذكر الحاشية سورتها ورقمها.")
                appendLine()
                appendCorrections(verified)
                appendReviewed(verified)
                appendLine("## كل الأذكار")
                appendLine()
                appendLine("| الذكر | الأحاديث المطابقة آليًا | الآيات | النتيجة |")
                appendLine("|---|---|---|---|")
                results.forEach { r ->
                    val matched = r.citations
                        .filter { it.result == CitationCheck.MATCH || it.result == CitationCheck.MATCH_NEAR }
                        .joinToString("، ") { "${bookNames.getValue(it.citation.book)} ${it.citation.number}" }
                    val verses = if (r.verses == 0) "—" else "${r.versesCited}/${r.verses}"
                    val label = finals.getValue(r.dhikr.id).label
                    appendLine("| ${r.dhikr.id} | ${matched.ifEmpty { "—" }} | $verses | $label |")
                }
            },
            Charsets.UTF_8,
        )
    }

    private fun StringBuilder.appendMethod() {
        val share = (HisnVerification.MIN_CONTAINMENT * PERCENT).toInt()
        appendLine("## الطريقة")
        appendLine()
        appendLine(
            "1. كل رقم حديث يذكره المؤلف في الكتب الستة يُبحث عنه في نسخة مستقلة من هذه الكتب " +
                "(fawazahmed0/hadith-api، ملكية عامة).",
        )
        appendLine(
            "2. يُعدّ المرجع **مطابقًا آليًا** إذا وُجد في نص الحديث $share٪ على الأقل من كلمات الذكر، " +
                "أو ${HisnVerification.MIN_RUN} كلمات منه متتالية بالترتيب نفسه.",
        )
        appendLine(
            "3. إن لم يطابق الرقم نفسه، يُبحث في الأرقام المجاورة (±${HisnVerification.NEAR})، " +
                "لأن الطبعات تختلف في الترقيم (أبو داود هنا أقل برقمين غالبًا، والنسائي أكثر بواحد)، " +
                "بشرط تطابق أقوى.",
        )
        appendLine(
            "4. كل ما بقي قُرئ نص حديثه يدويًا، وسُجّل الحكم فيه. " +
                "وإن كان الرقم خاطئًا بُحث عن الرقم الصحيح في الكتاب كاملًا.",
        )
        appendLine(
            "5. كل آية تظهر في ذكر نصّها من Tanzil حرفيًا (ADR-041)، " +
                "ويُفحص هنا هل تذكر الحاشية سورتها ورقمها.",
        )
        appendLine()
        appendLine(
            "**حدود هذا التحقق:** يثبت أن كل مرجع يدل على الحديث الذي يذكره، " +
                "ولا يحكم على صحة الحديث أو ضعفه (ذلك من كلام المؤلف ومن نقله عن العلماء)، " +
                "ولا يتحقق من المصادر خارج الكتب الستة (أحمد، الحاكم، ابن السني...).",
        )
        appendLine()
    }

    private fun StringBuilder.appendCorrections(verified: ReviewedVerification) {
        appendLine("## تصحيحات أرقام وُجدت في الكتاب")
        appendLine()
        appendLine("تظهر في التطبيق ملاحظةً تحت المرجع؛ نص المؤلف نفسه لا يُغيَّر.")
        appendLine()
        verified.review.items.filter { it.verdict == ManualVerdict.WRONG_NUMBER }.forEach {
            val book = HadithBook.entries.first { b -> b.key == it.book }
            appendLine("- الذكر ${it.dhikr}: ${bookNames.getValue(book)} ${it.number} ← **${it.correct}**. ${it.note}")
        }
        appendLine()
    }

    private fun StringBuilder.appendReviewed(verified: ReviewedVerification) {
        appendLine("## المراجعة اليدوية")
        appendLine()
        verified.results.filter { r -> r.citations.any { verified.review.verdict(r.dhikr.id, it.citation) != null } }
            .forEach { r ->
                appendLine("### ${r.dhikr.id} — ${r.dhikr.chapterTitle}")
                appendLine()
                appendLine("> ${r.dhikr.text.take(SNIPPET)}")
                appendLine()
                r.citations.forEach { c -> appendLine(citationLine(verified, r.dhikr.id, c)) }
                appendLine()
            }
        verified.open.forEach { (d, c) ->
            appendLine("- ⚠️ لم يُراجع: الذكر $d، ${bookNames.getValue(c.book)} ${c.number}")
        }
        appendLine()
    }

    private fun citationLine(verified: ReviewedVerification, dhikr: Int, c: CheckedCitation): String {
        val name = "${bookNames.getValue(c.citation.book)} ${c.citation.number}"
        val item = verified.review.verdict(dhikr, c.citation)
        return when {
            item != null -> {
                val found = item.found?.let { " ($it)" }.orEmpty()
                "- $name: ${verdictNames.getValue(item.verdict)}$found. ${item.note}"
            }
            c.result == CitationCheck.MATCH_NEAR -> "- $name: مطابق آليًا للحديث ${c.foundAt} في هذه النسخة"
            else -> "- $name: مطابق آليًا"
        }
    }
}
