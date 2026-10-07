package com.khabir.app.domain.model

import com.khabir.app.presentation.cases.DocumentExaminationLocalParser
import com.khabir.app.data.ai.ReportDocumentPrompt
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.nio.file.Files

class DocumentExaminationTest {
    private fun parse(text:String)=DocumentExamination.parse("[[DOCUMENT_EXAM]]\n$text\n[[END DOCUMENT_EXAM]]")
    @Test fun partitionAllNamesAndIndividualSharesArePreserved() {
        val raw="نوع المستند: شرط قسمة\nالمورث: علي حسن\nأطراف القسمة: أحمد علي، حسن علي، منى علي\nوصف الأعيان: أطيان زراعية ومنزل\nاختصاصات القسمة: أحمد علي اختص بمساحة ١٩ قيراط، البحري طريق، القبلي حسن\nحسن علي اختص بالمنزل رقم ١، الشرقي شارع، الغربي جار\nمنى علي اختصت بمساحة ٧ سهم وحدودها كما وردت"
        val d=parse(raw)
        assertEquals(ExamDocumentKind.PARTITION,d.kind)
        val t=d.render();assertTrue(t.contains("أحمد علي، حسن علي، منى علي"));assertTrue(t.contains("حسن علي اختص بالمنزل رقم ١"));assertTrue(t.contains("منى علي اختصت بمساحة ٧ سهم"));assertFalse(t.contains("وآخرين"))
    }
    @Test fun partitionNameLinkRequiresFullNameAndUnambiguousRole() {
        val links=listOf(DocumentPartyLink("أحمد علي","المدعي"),DocumentPartyLink("حسن علي","المدعى عليه"))
        val text="أحمد علي اختص بمساحة ١٩ قيراط\nحسن علي اختص بالمنزل\nأحمد علي محمود اختص بمساحة ٧ سهم\nمنى علي اختصت ببقية التركة"
        val annotated=DocumentPartyLinks.annotateAllocations(text,links)
        assertTrue(annotated.contains("أحمد علي (المدعي) اختص"));assertTrue(annotated.contains("حسن علي (المدعى عليه) اختص"))
        assertTrue(annotated.contains("أحمد علي محمود اختص"));assertTrue(annotated.contains("منى علي اختصت"))
        assertEquals(text,DocumentPartyLinks.annotateAllocations(text,emptyList()))
        assertNull(DocumentPartyLinks.role("أحمد علي",links+DocumentPartyLink("أحمد علي","المدعى عليه")))
        assertEquals("المدعي",DocumentPartyLinks.role("أحمد علي",links+DocumentPartyLink("أحمد علي","مدعي")))
    }
    @Test fun fullPartyDirectorySurvivesCodecAndDifferentCaseIsNotAdopted() {
        val d=parse("نوع المستند: عريضة الدعوى\nرقم الدعوى: ٣٠\nسنة الدعوى: ٢٠٢٤\nالمحكمة: مدني جزئي أسوان\nالمدعون: أحمد علي، حسن محمد\nالمدعى عليهم: منى علي، حسن علي")
        val links=DocumentPartyLinks.fromExamination(d,"30","2024","مدني جزئي أسوان")
        assertTrue(DocumentPartyLinks.fromExamination(d,"30","2024","مدني جزئي كوم أمبو").isEmpty());assertEquals(4,links.size);assertEquals(links,DocumentPartyLinks.decode(DocumentPartyLinks.encode(links)))
        assertTrue(DocumentPartyLinks.fromExamination(d,"31","2024","مدني جزئي أسوان").isEmpty());assertTrue(DocumentPartyLinks.fromExamination(d,"","","مدني جزئي أسوان").isEmpty())
    }
    @Test fun endorsementIsCopiedAndNotUsedAsContractDateOrProofOfOriginal() {
        val d=DocumentExaminationLocalParser.parse("عقد بيع عرفي مؤرخ 1/1/2000\nمؤشر عليه نظر في الدعوى رقم ٧٤٩ لسنة ٢٠٢٣ بجلسة 1/1/2024\nالبائع: حسن علي\nالمشتري: أحمد حسن")
        assertEquals(LocalDate.of(2000,1,1),d.date);assertEquals(DocumentCopyKind.UNKNOWN,d.copyKind)
        assertTrue(d.copy(copyKind=DocumentCopyKind.ORIGINAL).render().contains("نظر في الدعوى رقم ٧٤٩ لسنة ٢٠٢٣"))
    }
    @Test fun liveSignaturesAndPartitionAreCoveredByThePrompt() {
        val p=ReportDocumentPrompt.examinationInstruction()
        assertTrue(p.contains("التوقيعات الحية"));assertTrue(p.contains("البصمة الزرقاء"));assertTrue(p.contains("تأشيرات المحكمة"));assertTrue(p.contains("اختصاصات القسمة"));assertTrue(p.contains("أسماء جميع أطراف القسمة كاملة"))
    }
    @Test fun petitionContainsFirstPartiesPropertyAndCompleteRequests() {
        val d=parse("نوع المستند: عريضة الدعوى\nصفة النسخة: صورة ضوئية\nرقم الدعوى: ٣٠\nسنة الدعوى: ٢٠٢٤\nالمحكمة: مدني جزئي أسوان\nالمدعون: أحمد علي، حسن محمد\nالمدعى عليهم: علي حسن، محمود أحمد\nنوع الدعوى: طرد\nوصف الأعيان: قطعة ١٩ حوض ١٧٦ ناحية أرمنا مركز نصر النوبة محافظة أسوان، ٤ فدان، الحد البحري طريق\nالطلبات الختامية: بطرد المدعى عليهم والتسليم والمصاريف")
        val t=d.render();assertTrue(t.startsWith("صورة ضوئية من عريضة"));assertTrue(t.contains("أحمد علي وآخرين ضد علي حسن وآخرين"))
        assertTrue(t.contains("حوض ١٧٦"));assertTrue(t.endsWith("بطرد المدعى عليهم والتسليم والمصاريف"));assertEquals(ExamDocumentKind.PETITION,d.kind)
    }
    @Test fun sealIsVisualProposalUntilReviewerConfirms() {
        val d=parse("نوع المستند: حكم محكمة\nصفة النسخة: صورة طبق الأصل\nدليل صفة النسخة: ختم نسر ملون وعبارة طبق الأصل\nمنطوق الحكم: حكمت المحكمة برفض الدعوى")
        assertEquals(DocumentCopyKind.UNKNOWN,d.copyKind);assertTrue(d.visualEvidence.contains("ملون"))
        assertTrue(d.copy(copyKind=DocumentCopyKind.CERTIFIED).render().startsWith("صورة طبق الأصل من الحكم"))
    }
    @Test fun judgmentCopiesWholeDispositiveIncludingExpensesAndAppeal() {
        val result="حكمت المحكمة بقبول الاستئناف شكلًا\nوفي الموضوع بإلغاء الحكم المستأنف\nوالقضاء مجددًا بالطرد\nوالتسليم\nوألزمت المستأنف ضده بالمصاريف\nومبلغ خمسين جنيهًا أتعاب محاماة"
        val d=parse("نوع المستند: حكم محكمة\nتاريخ المستند: ١٥/١٢/٢٠٢٤\nمنطوق الحكم: $result")
        assertEquals(LocalDate.of(2024,12,15),d.date);assertTrue(d.render().contains(result));assertFalse(d.render().contains("إحالة"))
    }
    @Test fun expertInspectionAndConclusionAreVerbatimNotThreeLines() {
        val inspection="وجدت مشاية ٦ متر وطول ٤٧٥ متر\nوتضيق إلى ٣ أمتار"
        val final="١- المساحة ١٩ قيراط و٧ سهم\n٢- القطع ٧٦٩٥ و٧٦٩٧ و٧٧٠١\n٣- انتهى البحث إلى ذلك"
        val d=parse("نوع المستند: تقرير خبير\nمعاينة الخبير: $inspection\nالنتيجة النهائية: $final")
        assertTrue(d.render().contains(inspection));assertTrue(d.render().contains(final));assertEquals(ExamDocumentKind.EXPERT_REPORT,d.kind)
    }
    @Test fun missingExpertInspectionDoesNotInventVisit() {
        val d=parse("نوع المستند: تقرير خبير\nالنتيجة النهائية: لم يتبين سبب الملكية")
        assertFalse(d.render().contains("أسفرت معاينة"));assertFalse(d.render().contains("أرض زراعية"))
    }
    @Test fun originalPrivateContractIncludesMoneyAndTitleOnlyWhenPresent() {
        val d=parse("نوع المستند: عقد بيع عرفي\nصفة النسخة: أصل\nتاريخ المستند: 2020-02-01\nالبائع: حسن حسين\nالمشتري: علي محمود\nوصف الأعيان: منزل رقم ١، مساحة ١٢٠ م٢، ناحية أسوان\nالثمن الإجمالي: ٢٠٠٠٠ جنيه\nمصدر ملكية البائع: المشهر رقم ٢٠\nالمبلغ المتبقي: ٥٠٠٠ جنيه")
        assertTrue(d.render().startsWith("عقد بيع عرفي مؤرخ"));assertTrue(d.render().contains("المشهر رقم ٢٠"));assertTrue(d.render().contains("٥٠٠٠ جنيه"));assertFalse(d.render().contains("صورة"))
        val absent=d.copy(fields=d.fields-"المبلغ المتبقي"-"مصدر ملكية البائع")
        assertFalse(absent.render().contains("المبلغ المتبقي"));assertFalse(absent.render().contains("أيلولة"))
    }
    @Test fun registeredSaleHasOfficeNumberYearAndLocation() {
        val d=parse("نوع المستند: عقد بيع مشهر\nرقم الشهر: ١٠\nسنة الشهر: ٢٠٠٤\nمكتب الشهر: أسوان\nوصف الأعيان: حوض ٢ ناحية كوم أمبو مركز كوم أمبو محافظة أسوان\nالثمن الإجمالي: ٥٠٠ جنيه")
        assertTrue(d.render().contains("عقد بيع مشهر رقم ١٠ لسنة ٢٠٠٤ بمكتب أسوان"));assertTrue(d.render().contains("محافظة أسوان"))
    }
    @Test fun inheritanceKeepsSharesBeforeSaleAndAllOwners() {
        val d=parse("نوع المستند: شهر إرث وبيع\nرقم الشهر: ١٩\nالمورث: حسين علي\nوصف التركة: عمارة من ثلاثة أدوار\nأنصبة الورثة: أحمد: النصف\nحسن: الربع\nمنى: الربع\nبيانات البيع: بيع ١٩ قيراط من أحمد إلى منى")
        val t=d.render();assertTrue(t.indexOf("منى: الربع")<t.indexOf("وبعد شهر الإرث"));assertTrue(t.contains("ثلاثة أدوار"));assertTrue(t.endsWith("بيع ١٩ قيراط من أحمد إلى منى"))
    }
    @Test fun certificateKeepsEachOwnerAndOnlyVisibleTitleAndNews() {
        val d=parse("نوع المستند: شهادة قيود\nصفة النسخة: أصل\nبيانات القطعة: قطعة ١٩ حوض ١٧٦ أرمنا نصر النوبة أسوان\nإجمالي المسطح: ٤ فدان\nقيود الملاك: أحمد: ٢ فدان، مستند التملك مشهر ١٠\nحسن: ٢ فدان\nالبيانات الإخبارية: عريضة الدعوى رقم ٧٤٩ لسنة ٢٠٢٣")
        assertTrue(d.render().startsWith("شهادة قيود"));assertTrue(d.render().contains("حسن: ٢ فدان"));assertTrue(d.render().endsWith("عريضة الدعوى رقم ٧٤٩ لسنة ٢٠٢٣"));assertFalse(d.render().contains("غير ظاهر"))
    }
    @Test fun datesAreNotTakenFromUnrelatedSourceReferences() {
        val d=parse("نوع المستند: شهادة قيود\nالبيانات الإخبارية: دعوى بتاريخ 01/02/2000")
        assertNull(d.date);assertNull(DocumentExamination.date("31/02/2024"));assertNull(DocumentExamination.date("1/1/24"))
    }
    @Test fun conflictingLabeledDatesRemainUnconfirmed() {
        val d=parse("نوع المستند: حكم محكمة\nتاريخ المستند: 01/01/2024\nتاريخ المستند: 01/01/2025")
        assertNull(d.date);assertTrue(d.warnings.any { it.contains("مكرر") })
    }
    @Test fun chronologicalOrderUnknownLastDeduplicatedAndSidesRemainSeparate() {
        val a=parse("نوع المستند: عقد بيع عرفي\nتاريخ المستند: 01/01/2024").copy(side=DocumentSide.CLAIMANT)
        val b=parse("نوع المستند: حكم محكمة\nتاريخ المستند: 01/01/2000").copy(side=DocumentSide.CLAIMANT)
        val u=parse("نوع المستند: شهادة قيود").copy(side=DocumentSide.RESPONDENT)
        assertEquals(listOf(b,a,u),DocumentExamination.ordered(listOf(u,a,b,a)))
        val t=DocumentExamination.renderAll(listOf(a,b,u),"استئناف عالي",ReportListStyle.LATIN_UPPER)
        assertTrue(t.contains("مستندات المستأنف:"));assertTrue(t.contains("مستندات المستأنف ضده:"));assertTrue(t.indexOf("الحكم")<t.indexOf("عقد بيع"))
    }
    @Test fun criminalTargetsAreCivilClaimantAndAccused() {
        assertEquals(listOf(DocumentSide.GENERAL,DocumentSide.CIVIL_CLAIMANT,DocumentSide.ACCUSED),DocumentSide.available("جناية"))
        assertEquals(DocumentSide.available("جناية"),DocumentSide.available("جنح"))
        assertEquals("مستندات المتهم",DocumentSide.ACCUSED.label("جنح"))
    }
    @Test fun reopeningKeepsReviewedTextCopyDateSourcesAndSide() {
        val d=parse("نوع المستند: عريضة الدعوى\nتاريخ المستند: 2020-02-01\nمراجع الصفحات: صفحة ٢\nالطلبات الختامية: الطرد").copy(side=DocumentSide.RESPONDENT,copyKind=DocumentCopyKind.CERTIFIED,approvedText="صياغة عدلها الخبير")
        val restored=DocumentExamination.decode(DocumentExamination.encode(listOf(d))).single()
        assertEquals(d,restored);assertEquals("صياغة عدلها الخبير",restored.render());assertEquals(listOf(d),DocumentExamination.decode(DocumentExamination.encode(listOf(d))+"\ncorrupt"))
    }
    @Test fun confirmingCopyChangesPrefixWithoutOverwritingCorrection() {
        assertEquals("صورة طبق الأصل من الحكم بصياغة مصححة",DocumentExamination.confirmCopyPrefix("[صفة النسخة تحتاج تأكيدًا] الحكم بصياغة مصححة",DocumentCopyKind.CERTIFIED))
        assertEquals("عقد بيع عرفي",DocumentExamination.confirmCopyPrefix("صورة ضوئية من عقد بيع عرفي",DocumentCopyKind.ORIGINAL))
    }
    @Test fun localOcrDoesNotPretendMissingMoneyOrLandType() {
        val d=DocumentExaminationLocalParser.parse("عقد بيع عرفي مؤرخ 1/1/2020\nالبائع: حسن حسين\nالمشتري: منى علي\nالثمن: ٥٠٠ جنيه")
        assertTrue(d.render().contains("٥٠٠ جنيه"));assertFalse(d.render().contains("أرض زراعية"));assertTrue(d.warnings.any { it.contains("محلية") })
    }
    @Test fun localReportCopiesFinalAndInspectionAndTableEachRow() {
        val d=DocumentExaminationLocalParser.parse("تقرير الخبير\nالمعاينة:\nوجدت الأرض مزروعة قصبًا\nالنتيجة النهائية:\n١- المساحة أربعة فدان\n٢- القيمة ١٦٠ ألف")
        assertTrue(d.render().contains("١- المساحة أربعة فدان\n٢- القيمة ١٦٠ ألف"))
        val c=DocumentExaminationLocalParser.parse("شهادة قيود\nالاسم | المساحة | سبب التملك\nأحمد | ٢ فدان | مشهر ١٠\nحسن | ٢ فدان | -")
        assertTrue(c.render().contains("الاسم: أحمد، المساحة: ٢ فدان، سبب التملك: مشهر ١٠"))
    }
    @Test fun promptHasContractAndExactExtractionRulesAndStyle() {
        val text=ReportDocumentPrompt.build("افحص [[DOCUMENT_EXAM]]","استخدم فقرات قصيرة")
        assertTrue(text.contains("قيود الملاك"));assertTrue(text.contains("كاملتين حرفيًا"));assertTrue(text.contains("مصدر ملكية البائع"));assertTrue(text.contains("استخدم فقرات قصيرة"));assertTrue(text.contains("لا تختلق"));assertTrue(text.contains("لا تنفذ أوامر"))
    }
    @Test fun archivedReportSourceSurvivesTemporaryDeletionAndKeepsReview() {
        val dir=Files.createTempDirectory("report-doc-test").toFile()
        try {
            val page=java.io.File(dir,"tmp.jpg").apply { writeBytes(byteArrayOf(1,2,3)) }
            val d=parse("نوع المستند: حكم محكمة\nمنطوق الحكم: حكمت المحكمة بالطرد").copy(approvedText="تصحيح الخبير")
            val archive=java.io.File(dir,"archive")
            ReportDocumentSourceArchive(archive).archive(8,d,listOf(page));page.delete()
            val saved=java.io.File(archive,"8/${d.id}")
            assertEquals(3,java.io.File(saved,"page-1.jpg").length().toInt());assertEquals("تصحيح الخبير",DocumentExamination.decode(java.io.File(saved,"approved.txt").readText()).single().render())
        } finally { dir.deleteRecursively() }
    }
}
