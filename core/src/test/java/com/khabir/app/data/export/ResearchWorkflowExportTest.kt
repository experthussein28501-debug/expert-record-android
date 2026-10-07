package com.khabir.app.data.export

import com.khabir.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ResearchWorkflowExportTest {
    @Test fun inheritanceResearchApprovedConclusionAndArabicListReachRealWord() {
        val doc=DocumentExamination.parse("نوع المستند: إعلام وراثة\nصفة النسخة: صورة ضوئية\nالمورث: علي حسين\nتاريخ الوفاة: 01/01/2020\nتفاصيل الورثة: ابنه أحمد وابنته منى كما ورد بالإعلام")
        val source=doc.render()
        val r=Report(caseId=8,assignment="١- بيان ورثة المالك.",documentsSubmitted=source,witnessStatements="قال الشاهد لا أعلم النصيب.")
        val c=ReportResearch.parseContext(ReportResearch.context(r));val e=c.evidence.first { it.kind=="مستند" }
        val answer="[[ANSWER ${c.tasks.single().id}]]\nالحالة: INSUFFICIENT\nأدلة مؤيدة: ${e.id}\nالرد: ورد بالإعلام وفاة علي حسين في 01/01/2020 وانحصار الإرث في أحمد ومنى؛ لم يثبت نصيب كل منهما بالأدلة المتاحة.\nالنواقص: أصل بيان الأنصبة\n[[END ANSWER]]"
        val review=ReportResearch.review(answer,c);assertTrue(review.errors.toString(),review.valid)
        val proposal=ReportAiProposal(ReportAiKind.RESEARCH,review.reportText(c.tasks),review.render(c.tasks,c.evidence),ReportAiWorkflow.fingerprint(r,ReportAiKind.RESEARCH),review.answers.associate { it.taskId to it.status.name })
        val approved=ReportAiWorkflow.apply(r,proposal,proposal.text,true)
        val cc=ReportResearch.parseContext(ReportAiWorkflow.context(approved,ReportAiKind.CONCLUSION));val ce=cc.evidence.first();val quote=review.answers.single().response
        val raw="[[RESULT ${c.tasks.single().id}]]\nالحالة: INSUFFICIENT\nالمصدر: ${ce.id}\nالاقتباس: $quote\nالنتيجة: لم يثبت نصيب كل من أحمد ومنى؛ يلزم أصل بيان الأنصبة.\n[[END RESULT]]"
        val summary=ReportAiWorkflow.reviewConclusion(raw,approved,cc)
        val finished=ReportAiWorkflow.apply(approved,summary,summary.text,true)
        assertEquals(r.witnessStatements,finished.witnessStatements)
        val bytes=LegalReportDocxBuilder().build("تقرير",emptyList(),listOf("بحث المستندات" to source,"البحث" to finished.research,"النتيجة النهائية" to ReportLists.apply(finished.conclusion,ReportListStyle.LARGE_BULLET)))
        File("build/report-fixtures/research-inheritance-reviewed.docx").apply {parentFile.mkdirs();writeBytes(bytes)}
        val xml=ZipInputStream(ByteArrayInputStream(bytes)).use { z -> var entry=z.nextEntry;var text="";while(entry!=null) {if(entry.name=="word/document.xml") text=String(z.readBytes(),Charsets.UTF_8);entry=z.nextEntry};text }
        assertTrue(xml.contains("إعلام وراثة"));assertTrue(xml.contains("لم يثبت نصيب"));assertTrue(xml.contains("● "));assertTrue(xml.contains("<w:bidi/>"));assertFalse(xml.contains("[[ANSWER"));assertFalse(xml.contains("[[RESULT"))
    }
}
