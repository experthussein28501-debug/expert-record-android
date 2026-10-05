package com.khabir.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class ReportListsTest {
    @Test fun allChoicesHaveStableMarkersAndExtendedLetters() {
        assertEquals("1. ",ReportLists.marker(ReportListStyle.WESTERN,1));assertEquals("١. ",ReportLists.marker(ReportListStyle.ARABIC_INDIC,1))
        assertEquals("أ. ",ReportLists.marker(ReportListStyle.ARABIC_LETTERS,1));assertEquals("هـ. ",ReportLists.marker(ReportListStyle.ARABIC_LETTERS,5))
        assertEquals("AA. ",ReportLists.marker(ReportListStyle.LATIN_UPPER,27));assertEquals("aa. ",ReportLists.marker(ReportListStyle.LATIN_LOWER,27))
        assertEquals("● ",ReportLists.marker(ReportListStyle.LARGE_BULLET,1));assertEquals("■ ",ReportLists.marker(ReportListStyle.SQUARE,1))
    }
    @Test fun switchStyleAndRemoveListsPreserveValuesAndHeadings() {
        val text="مستندات المدعي:\n١. قطعة ١٩ ومساحة ٧ سهم\n٢. حكم في 2020-02-01"
        val latin=ReportLists.apply(text,ReportListStyle.LATIN_UPPER)
        assertEquals("مستندات المدعي:\nA. قطعة ١٩ ومساحة ٧ سهم\nB. حكم في 2020-02-01",latin)
        assertEquals("مستندات المدعي:\nقطعة ١٩ ومساحة ٧ سهم\nحكم في 2020-02-01",ReportLists.apply(latin,ReportListStyle.PLAIN))
    }
    @Test fun styleApplicationIsIdempotentAndHandlesArabicHa() {
        val text="أول\nثاني\nثالث\nرابع\nخامس"
        val once=ReportLists.apply(text,ReportListStyle.ARABIC_LETTERS)
        assertEquals(once,ReportLists.apply(once,ReportListStyle.ARABIC_LETTERS));assertTrue(once.endsWith("هـ. خامس"))
    }
    @Test fun enterContinuesAndEmptyItemEndsListAndPasteIsUntouched() {
        assertEquals("A. ",ReportLists.apply("",ReportListStyle.LATIN_UPPER))
        assertEquals("A. مستند\nنص عادي\n",ReportLists.continueOnEnter("A. مستند\nنص عادي","A. مستند\nنص عادي\n",ReportListStyle.LATIN_UPPER))
        assertEquals("A. مستند\nB. ",ReportLists.continueOnEnter("A. مستند","A. مستند\n",ReportListStyle.LATIN_UPPER))
        assertEquals("A. مستند\n",ReportLists.continueOnEnter("A. مستند\nB. ","A. مستند\nB. \n",ReportListStyle.LATIN_UPPER))
        assertEquals("محتوى منسوخ\nثانٍ",ReportLists.continueOnEnter("قديم","محتوى منسوخ\nثانٍ",ReportListStyle.WESTERN))
    }
    @Test fun styleMetadataSurvivesReportCodecIndependentlyPerSection() {
        val m=mapOf(ReportListStyle.key("documents") to "LARGE_BULLET",ReportListStyle.key("conclusion") to "LATIN_UPPER")
        val restored=ReportCustomSectionCodec.decode(ReportCustomSectionCodec.encode(m))
        assertEquals(ReportListStyle.LARGE_BULLET,ReportListStyle.decode(restored[ReportListStyle.key("documents")]))
        assertEquals(ReportListStyle.LATIN_UPPER,ReportListStyle.decode(restored[ReportListStyle.key("conclusion")]))
        assertEquals(ReportListStyle.PLAIN,ReportListStyle.decode(restored[ReportListStyle.key("research")]))
    }
}
