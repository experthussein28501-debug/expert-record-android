package com.khabir.app.presentation.cases

import com.khabir.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LegalProcedurePipelineTest {
    private fun doc(id:Int,type:String,extra:String="",request:String="الحكم بالتسليم"):LegalDocumentRecord =
        LegalProcedureParser.parse(id,listOf(id),"""
            نوع المستند: $type
            رقم الدعوى: ١٠٥
            سنة الدعوى: ٢٠٢٦
            المحكمة: أسوان
            موضوع الدعوى: الطالب يمتلك أرضًا مساحتها ١٩ قيراطًا و٧ أسهم.
            الطلبات الختامية: $request
            $extra
        """.trimIndent(),type)
    private fun referral(extra:String=""):LegalDocumentRecord = LegalProcedureParser.parse(9,listOf(1,2),"""
        نوع المستند: حكم إحالة
        رقم الدعوى السابقة: ١٢
        سنة الدعوى السابقة: ٢٠٢٥
        المحكمة السابقة: محكمة دراو الجزئية
        رقم الدعوى الحالية: ١٠٥
        سنة الدعوى الحالية: ٢٠٢٦
        المحكمة الحالية: أسوان
        نوع الدعوى الحالية: مدني كلي
        تاريخ الحكم: ٢٠/١/٢٠٢٦
        دليل الإحالة: حكمت المحكمة بعدم اختصاصها وإحالة الدعوى إلى محكمة أسوان.
        موضوع الدعوى: الطالب يمتلك قطعة أرض ومساحتها ١٩ قيراطًا.
        الطلبات الختامية: الحكم بتسليم العين والمصاريف.
        منطوق الحكم:
        حكمت المحكمة بعدم اختصاصها نوعيًا بنظر الدعوى وإحالتها بحالتها.
        وأبقت الفصل في المصاريف.
        $extra
    """.trimIndent())
    @Test fun chronologicalSubsidiariesFollowOriginalRegardlessOfCameraOrder() {
        val intervention=doc(2,"صحيفة تدخل هجومي","تاريخ تقديم الإجراء: ١٠/٣/٢٠٢٦\nمقدم الإجراء: متدخل أول","طلب المتدخل")
        val incidental=doc(3,"صحيفة طلب عارض","تاريخ تقديم الإجراء: ١/٢/٢٠٢٦\nمقدم الإجراء: مقدم العارض","طلب العارض")
        val counter=doc(4,"صحيفة دعوى فرعية","تاريخ رفع الصحيفة: ٢٠/٤/٢٠٢٦\nمقدم الإجراء: صاحب الفرعية","طلب الفرعية")
        val result=LegalCaseAssembly.assemble(listOf(counter,intervention,doc(1,"صحيفة دعوى"),incidental))
        assertTrue(result.subject.startsWith("أقام المدعي"))
        assertTrue(result.subject.indexOf("طلب العارض") < result.subject.indexOf("طلب المتدخل"))
        assertTrue(result.subject.indexOf("طلب المتدخل") < result.subject.indexOf("طلب الفرعية"))
        assertEquals(1,Regex("طلب المتدخل").findAll(result.subject).count())
        assertTrue(result.subject.contains("تدخل متدخل أول هجوميًا"))
    }
    @Test fun sessionDateIsNotProcedureDate() {
        val r=doc(1,"صحيفة تدخل هجومي","تاريخ الجلسة: ١/١/٢٠٢٦")
        assertNull(r.procedureDate)
        assertTrue(r.warnings.any { it.contains("تاريخ الجلسة") })
    }
    @Test fun conflictingDateIsNotGuessed() {
        val r=doc(1,"صحيفة طلب عارض","تاريخ تقديم الإجراء: ١/١/٢٠٢٦\nتاريخ رفع الصحيفة: ٢/١/٢٠٢٦")
        assertNull(r.procedureDate)
        assertTrue(r.warnings.any { it.contains("متعارض") })
    }
    @Test fun invalidDateIsFlagged() { assertNull(doc(1,"صحيفة طلب عارض","تاريخ تقديم الإجراء: ٣١/٢/٢٠٢٦").procedureDate) }
    @Test fun serviceAndProcedureDatesRemainIndependent() {
        val r=doc(1,"صحيفة طلب عارض","تاريخ تقديم الإجراء: ١/١/٢٠٢٦\nتاريخ الإعلان: ١٠/٢/٢٠٢٦")
        assertEquals(LocalDate.of(2026,1,1),r.procedureDate)
        assertEquals(LocalDate.of(2026,2,10),r.serviceDate)
    }
    @Test fun explicitManualOrderWorksWithoutInventingDates() {
        val a=doc(2,"صحيفة طلب عارض","ترتيب الإجراء: ٢","طلب متأخر")
        val b=doc(3,"صحيفة تدخل هجومي","ترتيب الإجراء: ١","طلب أول")
        val result=LegalCaseAssembly.assemble(listOf(a,b,doc(1,"صحيفة دعوى")))
        assertTrue(result.subject.indexOf("طلب أول") < result.subject.indexOf("طلب متأخر"))
        assertNull(b.procedureDate)
    }
    @Test fun duplicatePhotosDoNotDuplicateProcedureButChangedReliefDoes() {
        val a=doc(1,"صحيفة طلب عارض","تاريخ تقديم الإجراء: ١/١/٢٠٢٦","طلب خاص")
        val duplicate=a.copy(id=5,pages=listOf(8))
        val changed=a.copy(id=6,requests="طلب معدل لاحقًا")
        val result=LegalCaseAssembly.assemble(listOf(a,duplicate,changed))
        assertEquals(2,result.records.size)
        assertEquals(1,Regex("طلب خاص").findAll(result.subject).count())
        assertTrue(result.subject.contains("طلب معدل"))
    }
    @Test fun mereMentionOfInterventionDoesNotBecomeHostileIntervention() {
        assertEquals(LegalProcedureType.ORIGINAL,LegalProcedureType.classify("صحيفة دعوى"))
        assertEquals(LegalProcedureType.UNKNOWN,LegalProcedureType.classify("تدخل"))
    }
    @Test fun referralUsesHistoricalAndCurrentIdentitiesSeparatelyAndExactDispositive() {
        val r=referral();val result=LegalCaseAssembly.assemble(listOf(r))
        assertEquals("١٠٥",result.current?.number)
        assertEquals("١٢",result.previous.single().number)
        assertTrue(result.subject.contains("الدعوى رقم ١٢"))
        assertTrue(result.subject.contains(r.dispositive))
        assertTrue(result.subject.contains("حيثيات الحكم"))
        assertFalse(result.subject.contains("صحيفة معلنة قانونًا"))
        assertEquals(LocalDate.of(2026,1,20),r.judgmentDate)
    }
    @Test fun referralWithoutCurrentNumberNeverPromotesOldNumber() {
        val r=referral().copy(currentIdentity=null)
        val result=LegalCaseAssembly.assemble(listOf(r))
        assertNull(result.current)
        assertTrue(result.warnings.any { it.contains("الحالية") })
    }
    @Test fun twoUnlinkedCasesDoNotChooseFirstIdentity() {
        val result=LegalCaseAssembly.assemble(listOf(doc(1,"صحيفة دعوى"),doc(2,"صحيفة دعوى").copy(identity=LegalCaseIdentity("77","2026","أسوان"))))
        assertNull(result.current)
        assertTrue(result.warnings.any { it.contains("أرقام") })
    }
    @Test fun appealDoesNotGenerateReferralNarrative() {
        val r=referral().copy(type=LegalProcedureType.APPEAL)
        val result=LegalCaseAssembly.assemble(listOf(r))
        assertTrue(result.subject.contains("الاستئناف"))
        assertFalse(result.subject.contains("وأُحيلت"))
    }
    @Test fun negatedOrRequestedReferralIsNotEvidence() {
        assertFalse(referral("دليل الإحالة: لم تحل الدعوى").copy(referralProof="").provenReferral)
        val raw="نوع المستند: حكم إحالة\nدليل الإحالة: طلب المدعي إحالة الدعوى"
        assertFalse(LegalProcedureParser.parse(1,listOf(1),raw).provenReferral)
    }
    @Test fun originalAndSubsidiaryRolesSurviveReview() {
        val docs=DocumentReviewParser.parse("""
            [[DOCUMENT 1]]
            نوع المستند: صحيفة دعوى
            رقم الدعوى: ١٠٥
            سنة الدعوى: ٢٠٢٦
            المحكمة: أسوان
            الخصم: أحمد علي | العنوان: أسوان | الصفة: مدعي | الدعوى: أصلية
            موضوع الدعوى: الطالب يمتلك الأرض.
            الطلبات الختامية: الحكم بالتسليم.
            [[END DOCUMENT]]
            [[DOCUMENT 2]]
            نوع المستند: صحيفة دعوى فرعية
            رقم الدعوى: ١٠٥
            سنة الدعوى: ٢٠٢٦
            المحكمة: أسوان
            الخصم: أحمد علي | العنوان: أسوان | الصفة: مدعى عليه | الدعوى: فرعية
            موضوع الدعوى: الطالب يطلب التعويض.
            الطلبات الختامية: الحكم بالتعويض.
            [[END DOCUMENT]]
        """.trimIndent())
        val parsed=PetitionIntakeParser.parse(DocumentReviewParser.combinedText(docs))
        assertEquals(2,parsed.parties.size)
        assertEquals(setOf(PartyRole.PLAINTIFF,PartyRole.DEFENDANT),parsed.parties.map { it.role }.toSet())
        assertTrue(parsed.sourceAudit.isNotBlank())
        assertTrue(LegalSourceAudit.decode(parsed.sourceAudit).contains("الصفحات: 2"))
    }
    @Test fun assignmentKeepsArabicNumbersAndNewlinesLiterally() {
        val body="١- بيان المساحة ١٩ قيراطًا.\nأ- بيان الحدود.\n٢- وتحقيق كافة عناصر الدعوى"
        assertEquals(body,PetitionIntakeParser.parse("مأمورية الحكم التمهيدي:\n$body\nمرجع المأمورية: صفحة ٢").preliminaryMission)
    }
    @Test fun declaredDocumentIdAndPageOrderArePreserved() {
        val docs=DocumentReviewParser.parse("[[DOCUMENT 9]]\nنوع المستند: صحيفة دعوى\nالصفحات: ٣,١\n[[END DOCUMENT]]")
        assertEquals(9,docs.single().id)
        assertEquals(listOf(3,1),docs.single().pageNumbers)
    }
    @Test fun sourceReferenceRetainsQuoteAndPages() {
        val r=doc(1,"صحيفة دعوى")
        assertTrue(r.sources.any { it.field=="الطلبات الختامية" && it.quote==r.requests && it.pages==listOf(1) })
    }
    @Test fun misdemeanorSubjectDoesNotUseCivilOpening() {
        val result=LegalCaseAssembly.assemble(listOf(doc(1,"صحيفة دعوى")),"جنح",listOf("أحمد علي","حسن محمود"))
        assertTrue(result.subject.startsWith("النيابة العامة ضد أحمد علي وآخرين"))
        assertFalse(result.subject.contains("أقام المدعي"))
    }

    @Test fun courtDegreeInTitleDoesNotHidePetitionOrSubsidiaryKind() {
        assertEquals(LegalProcedureType.INTERVENTION,LegalProcedureType.classify("صحيفة تدخل هجومي أمام محكمة استئناف"))
        assertEquals(LegalProcedureType.ORIGINAL,LegalProcedureType.classify("صحيفة استئناف"))
        assertEquals(LegalProcedureType.PRELIMINARY,LegalProcedureType.classify("حكم تمهيدي استئناف عالي"))
        assertEquals(LegalProcedureType.APPEAL,LegalProcedureType.classify("حكم استئناف"))
    }
}
