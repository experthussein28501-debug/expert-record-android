package com.khabir.app.data.export

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class LegalReportDocxBuilderTest {

    @Test
    fun `ministry of justice experts report keeps official cover and rtl structure`() {
        val bytes = LegalReportDocxBuilder().build(
            reportTitle = "تقرير",
            coverFields = listOf(
                "الوزارة" to "وزارة العدل",
                "القطاع" to "قطاع الخبراء",
                "الإدارة" to "إدارة خبراء أسوان",
                "الخبير" to "خبير اختبار",
                "رقم الدعوى" to "272 لسنة 2025",
                "المحكمة" to "مدني كلي كوم أمبو",
                "المرفوعة من" to "المدعي الأول وآخرين",
                "ضد" to "المدعى عليه وآخرين",
                "الوارد" to "رقم 5373 لسنة 2025"
            ),
            sections = listOf(
                "الموضوع :" to "أقام المدعيان دعواهما بطلباتهما المبينة بالأوراق.",
                "المأمورية:" to "الانتقال لعين التداعي وبيان حدودها ومعالمها.",
                "مباشرة المأمورية:" to "قمنا بمباشرة المأمورية على النحو المبين بمحاضر الأعمال.",
                "الاقوال  :-" to "أقوال المدعي ثم أقوال المدعى عليهم.",
                "المعاينه على الطبيعه:-" to "قمنا بإجراء المعاينة على الطبيعة.",
                "بحث المستندات" to "تم الاطلاع على المستندات المقدمة بملف الدعوى."
            )
        )

        val sampleDir = java.io.File("build/report-fixtures").apply { mkdirs() }
        java.io.File(sampleDir, "civil-report-rtl.docx").writeBytes(bytes)

        val documentXml = unzipEntry(bytes, "word/document.xml")
        val stylesXml = unzipEntry(bytes, "word/styles.xml")

        assertTrue(documentXml.contains("وزارة العدل"))
        assertTrue(documentXml.contains("قطاع الخبراء"))
        assertTrue(documentXml.contains("إدارة خبراء أسوان"))
        assertTrue(documentXml.contains("خبير وزارة العدل"))
        assertTrue(documentXml.contains("فى الدعوى رقم 272 لسنة 2025 مدني كلي كوم أمبو"))
        assertTrue(documentXml.contains("المرفوعــة من"))
        assertTrue(documentXml.contains("ضـــــد"))
        assertTrue(documentXml.contains("وارد رقم 5373"))
        assertTrue(documentXml.contains("لسنة 2025"))

        assertTrue(documentXml.contains("الموضوع :"))
        assertTrue(documentXml.contains("المأمورية:"))
        assertTrue(documentXml.contains("مباشرة المأمورية:"))
        assertTrue(documentXml.contains("الاقوال  :-"))
        assertTrue(documentXml.contains("المعاينه على الطبيعه:-"))
        assertTrue(documentXml.contains("بحث المستندات"))

        assertTrue(documentXml.contains("<w:bidi/>"))
        assertTrue(documentXml.contains("<w:rtl/>"))
        assertTrue(documentXml.contains("w:val=\"both\""))
        assertTrue(stylesXml.contains("w:bidi=\"ar-EG\""))
        assertTrue(stylesXml.contains("Traditional Arabic"))

        assertFalse(documentXml.contains("الخبير القضائي"))
        assertFalse(documentXml.contains("تقرير خبير قضائي"))
        assertFalse(documentXml.contains("خبرة قانونية"))
        assertFalse(documentXml.contains("تقرير خبرة قانونية"))
    }

    @Test
    fun `all four expert report families emit reviewable word fixtures`() {
        val catalog = listOf(
            "civil-template-review" to com.khabir.app.domain.model.ReportTemplateCatalog.civil,
            "family-template-review" to com.khabir.app.domain.model.ReportTemplateCatalog.family,
            "misdemeanor-template-review" to com.khabir.app.domain.model.ReportTemplateCatalog.misdemeanor,
            "appeal-template-review" to com.khabir.app.domain.model.ReportTemplateCatalog.appeal,
            "high-appeal-template-review" to com.khabir.app.domain.model.ReportTemplateCatalog.highAppeal
        )
        val sampleDir = java.io.File("build/report-fixtures").apply { mkdirs() }

        catalog.forEach { (fileName, template) ->
            val sections = template.orderedSections()
                .filter { it.enabled }
                .map { section ->
                    section.title to (section.headingLines + "نص تجريبي لمراجعة اتجاه الكتابة وترتيب هذا البند.").joinToString("\n")
                }
            val bytes = LegalReportDocxBuilder().build(
                reportTitle = "تقرير",
                coverFields = listOf(
                    "الوزارة" to "وزارة العدل",
                    "القطاع" to "قطاع الخبراء",
                    "الإدارة" to "إدارة خبراء أسوان",
                    "الخبير" to "خبير اختبار",
                    "رقم الدعوى" to "100 لسنة 2026",
                    "المحكمة" to template.name,
                    "المرفوعة من" to "الطرف الأول",
                    "ضد" to "الطرف الثاني",
                    "الوارد" to "رقم 1 لسنة 2026"
                ),
                sections = sections
            )
            java.io.File(sampleDir, "$fileName.docx").writeBytes(bytes)
        }
    }

    private fun unzipEntry(bytes: ByteArray, path: String): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == path) return zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        error("Missing DOCX entry: $path")
    }
}

