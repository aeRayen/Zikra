package com.zikra.tooling.content

import java.io.File

/**
 * Review sheet for the religious reviewer (ADR-P07): every dhikr with its text and the references that
 * will be shown, plus anything the automatic checks could not confirm. Markdown, one section per dhikr.
 */
object HisnReview {
    fun write(adhkar: List<HisnDhikr>, references: List<DhikrReference>, out: File) {
        val byId = references.associateBy { it.id }
        val problems = references.filter { it.status != ReferenceStatus.VERIFIED.name }
        out.parentFile?.mkdirs()
        out.writeText(
            buildString {
                appendLine("# مراجعة مراجع «حصن المسلم» — للمراجع الشرعي")
                appendLine()
                val verified = references.size - problems.size
                appendLine("- عدد الأذكار: ${adhkar.size}. تحقق آلي ناجح: $verified. تحتاج نظرًا: ${problems.size}.")
                appendLine(
                    "- المرجع منقول حرفيًا من حواشي المؤلف (المكتبة الشاملة، الكتاب 31307، ترقيمه موافق للمطبوع)."
                )
                appendLine("- المطلوب: التأكد أن كل مرجع يخص ذكره، ووضع علامة ✅ أو ملاحظة.")
                appendLine()
                adhkar.forEach { d ->
                    val r = byId.getValue(d.id)
                    appendLine("## ${d.id} — ${d.chapterTitle}")
                    appendLine()
                    appendLine("> ${d.text}")
                    appendLine()
                    appendLine(
                        "الحالة: `${r.status}` — تطابق النص: ${"%.2f".format(
                            r.agreement
                        )} — صفحات الشاملة: ${r.shamelaPages.joinToString()}"
                    )
                    appendLine()
                    r.footnotes.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("المراجعة: ☐")
                    appendLine()
                }
            },
            Charsets.UTF_8,
        )
    }
}
