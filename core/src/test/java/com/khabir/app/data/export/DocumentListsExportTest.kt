package com.khabir.app.data.export

import com.khabir.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

class DocumentListsExportTest {
    private fun xml(bytes:ByteArray):String = ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
        var e=z.nextEntry
        while(e!=null) { if(e.name=="word/document.xml") return String(z.readBytes(),Charsets.UTF_8);e=z.nextEntry }
        error("missing document")
    }
    private fun build(style:ReportListStyle):ByteArray {
        val d=DocumentExamination.parse("نوع المستند: عقد قسمة\nصفة النسخة: أصل\nتاريخ المستند: 01/01/2020\nأطراف القسمة: أحمد علي، حسن علي\nاختصاصات القسمة: أحمد علي اختص بمساحة ١٩ قيراط\nحسن علي اختص بمساحة ٧ سهم").copy(side=DocumentSide.CLAIMANT)
        val doc=DocumentExamination.renderAll(listOf(d),"استئناف عالي",style)
        val bytes=LegalReportDocxBuilder().build("تقرير",emptyList(),listOf("بحث المستندات" to doc,"البحث" to ReportLists.apply("ثبت كذا\nيلزم كذا",style),"النتيجة النهائية" to ReportLists.apply("أول نتيجة\nثاني نتيجة",style)))
        File("build/report-fixtures/document-list-${style.name.lowercase()}.docx").apply { parentFile.mkdirs();writeBytes(bytes) }
        return bytes
    }
    @Test fun westernMarkersRemainWesternAndBodiesRetainExistingArabicDigits() {
        val x=xml(build(ReportListStyle.WESTERN))
        assertTrue(x.contains(">1. </w:t>"));assertTrue(x.contains(">2. </w:t>"));assertFalse(x.contains(">١. </w:t>"))
        assertTrue(x.contains("١٩ قيراط"));assertTrue(x.contains("مستندات المستأنف:"));assertTrue(x.contains("w:hanging=\"480\""))
    }
    @Test fun latinAndArabicLettersRemainInAllThreeSections() {
        for(style in listOf(ReportListStyle.LATIN_UPPER,ReportListStyle.ARABIC_LETTERS)) {
            val x=xml(build(style));val marker=ReportLists.marker(style,1)
            assertTrue(x.split(">$marker</w:t>").size>=4)
            assertTrue(x.contains("أحمد علي، حسن علي"));assertTrue(x.contains("حسن علي اختص بمساحة ٧ سهم"))
        }
    }
    @Test fun bigBulletHasSeparateLargerRunAndPlainHasNoMarker() {
        val x=xml(build(ReportListStyle.LARGE_BULLET))
        assertTrue(x.contains(">● </w:t>"));assertTrue(x.contains("<w:sz w:val=\"36\"/>"));assertTrue(x.contains("<w:bidi/>"))
        val plain=xml(build(ReportListStyle.PLAIN));assertFalse(plain.contains(">● </w:t>"));assertFalse(plain.contains(">1. </w:t>"))
    }
}
