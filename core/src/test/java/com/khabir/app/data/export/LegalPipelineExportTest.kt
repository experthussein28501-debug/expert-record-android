package com.khabir.app.data.export

import com.khabir.app.domain.model.*
import com.khabir.app.presentation.cases.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class LegalPipelineExportTest {
    @Test fun reviewedCaseResearchAndReopenedMetadataExportOneArabicSubject() {
        val original=LegalProcedureParser.parse(1,listOf(1),"""
            نوع المستند: صحيفة دعوى
            رقم الدعوى: ١٠٥
            سنة الدعوى: ٢٠٢٦
            المحكمة: أسوان
            موضوع الدعوى: الطالب يمتلك ١٩ قيراطًا و٧ أسهم بناحية أرمنا والحد البحري طريق.
            الطلبات الختامية: الحكم بتسليم العين وإلزام المدعى عليه بالمصاريف.
        """.trimIndent())
        val intervention=LegalProcedureParser.parse(2,listOf(2),"""
            نوع المستند: صحيفة تدخل هجومي
            تاريخ تقديم الإجراء: ١/٣/٢٠٢٦
            مقدم الإجراء: متدخل أول، متدخل ثان
            موضوع الدعوى: شرح المتدخل على سند مستنده.
            الطلبات الختامية: الحكم بحق المتدخل.
        """.trimIndent())
        val subject=LegalCaseAssembly.assemble(listOf(intervention,original)).subject
        val report=Report(caseId=5,caseNo="١٠٥",caseYear="٢٠٢٦",court="مدني أسوان",subjectOfCase=subject,
            assignment="١- بيان ملكية الأرض.",documentsSubmitted="عقد بيع مقدم — بيان الحدود والمساحة.",
            customSectionContentsSpec=ReportCustomSectionCodec.encode(mapOf(ReportTextFormat.KEY to ReportTextFormat(size=18,font="Arial").encode())))
        val context=ReportResearch.parseContext(ReportResearch.context(report))
        val response="""
            [[ANSWER ${context.tasks.single().id}]]
            الحالة: SUPPORTED
            أدلة مؤيدة: ${context.evidence.single().id}
            أدلة معارضة:
            الرد: بيان الملكية يستند إلى المستند المقدم، مع مراجعة أصله.
            التعارضات:
            النواقص:
            [[END ANSWER]]
        """.trimIndent()
        val checked=ReportResearch.review(response,context)
        assertTrue(checked.valid)
        val accepted=ReportResearch.appendApproved(report,checked.render(context.tasks),ReportResearch.fingerprint(report))
        val reopened=accepted.copy(customSectionContentsSpec=ReportCustomSectionCodec.encode(ReportCustomSectionCodec.decode(accepted.customSectionContentsSpec)))
        val bytes=LegalReportDocxBuilder().build("تقرير خبرة",listOf("رقم الدعوى" to "١٠٥ لسنة ٢٠٢٦","المحكمة" to "أسوان"),
            listOf("الموضوع" to reopened.subjectOfCase,"المأمورية" to reopened.assignment,"البحث" to reopened.research))
        val files=mutableMapOf<String,String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while(true) { val e=zip.nextEntry ?: break;files[e.name]=zip.readBytes().toString(Charsets.UTF_8) }
        }
        val xml=files["word/document.xml"].orEmpty()
        assertTrue(xml.contains("١٩ قيراطًا و٧ أسهم"))
        assertEquals(1,Regex("الحكم بتسليم العين").findAll(xml).count())
        assertEquals(1,Regex("الحكم بحق المتدخل").findAll(xml).count())
        assertTrue(xml.contains("متدخل أول وآخرين"))
        assertTrue(xml.contains("رد مسند"))
        assertTrue(xml.contains("<w:bidi/>") && xml.contains("<w:rtl/>"))
        assertFalse(ReportResearch.needsReview(reopened))
        java.io.File("build/report-fixtures/full-legal-pipeline.docx").apply { parentFile.mkdirs();writeBytes(bytes) }
    }
}
