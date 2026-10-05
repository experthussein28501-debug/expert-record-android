package com.khabir.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class PetitionSubjectExtractionTest {
    private val petition = """
        صحيفة دعوى
        بناءً على طلب أحمد علي المقيم بأسوان
        ضد حسن محمود المقيم بدراو
        الموضوع
        الطالب يمتلك أرضًا مساحتها ١٩ قيراطًا و٧ أسهم بناحية أرمنا.
        الحد البحري طريق والقبلي ترعة والشرقي ملكه والغربي جار.
        وقد امتنع المدعى عليه عن التسليم مما حدا به إلى إقامة الدعوى.
        بناءً عليه
        أنا المحضر انتقلت إلى حيث إقامة المعلن إليهم وأعلنتهم وكلفتهم بالحضور إلى سرايا المحكمة يوم ١/١٠/٢٠٢٦ وذلك لسماع الحكم عليهم
        ١- بتسليم العين.
        ٢- واحتياطيًا بالتعويض بمبلغ ٥٠٠٠٠ جنيه.
        ٣- وإلزامهم بالمصاريف ومقابل أتعاب المحاماة.
        ولأجل العلم
        توقيع المحضر
    """.trimIndent()

    @Test fun skipsServiceAndKeepsAllRequests() {
        val parts = PetitionSubjectExtraction.extract(petition)
        assertTrue(parts.requests.startsWith("١- بتسليم العين"))
        assertTrue(parts.requests.contains("٥٠٠٠٠"))
        assertTrue(parts.requests.contains("أتعاب المحاماة"))
        assertFalse(parts.requests.contains("المحضر"))
        assertFalse(parts.requests.contains("ولأجل العلم"))
        assertFalse(parts.explanation.contains("بناءً عليه"))
        assertTrue(parts.explanation.contains("١٩ قيراطًا و٧ أسهم"))
        assertTrue(parts.warnings.isEmpty())
    }

    @Test fun hearingMarkerCanCrossPages() {
        val raw = petition.replace("لسماع الحكم عليهم", "لسماع\nالحكم\nعليهم")
        assertEquals(PetitionSubjectExtraction.extract(petition).requests, PetitionSubjectExtraction.extract(raw).requests)
    }

    @Test fun findsFactsWithoutHeadingAndDoesNotIncludePartyAddresses() {
        val parts = PetitionSubjectExtraction.extract(petition.replace("الموضوع\n", ""))
        assertTrue(parts.explanation.startsWith("الطالب يمتلك"))
        assertFalse(parts.explanation.contains("المقيم"))
    }

    @Test fun missingHearingAndRequestsLabelDoesNotPromoteServiceToRelief() {
        val parts = PetitionSubjectExtraction.extract(petition.substringBefore("وذلك لسماع"))
        assertEquals("", parts.requests)
        assertTrue(parts.warnings.any { it.contains("الطلبات") })
    }

    @Test fun explicitRequestsLabelIsAValidFallback() {
        val parts = PetitionSubjectExtraction.extract("الموضوع:\nيمتلك المدعي العين.\nالطلبات الختامية:\nالحكم بالتسليم والمصاريف.\nولأجل العلم")
        assertEquals("الحكم بالتسليم والمصاريف.", parts.requests)
    }

    @Test fun convertsEachTableRowWithoutMixingBoundsOrChangingUnits() {
        val parts = PetitionSubjectExtraction.extract("""
            الموضوع:
            الطالب يمتلك الأعيان الآتية:
            | رقم القطعة | المساحة | الناحية | البحري | القبلي | الشرقي | الغربي |
            | --- | --- | --- | --- | --- | --- | --- |
            | ٧٦٩٥ | ١٧ قيراطًا و٧ أسهم | أرمنا | طريق | ترعة | قطعة ٧٦٩٦ | قطعة ٧٦٩٧ |
            | ٧٧٠١ | ٢ قيراط | أرمنا | جار | مصرف | شارع | أرض |
            وقد امتنع الخصم عن التسليم.
            الطلبات الختامية: الحكم بالتسليم.
        """.trimIndent())
        val rows = parts.explanation.lines().filter { it.startsWith("رقم القطعة:") }
        assertEquals(2, rows.size)
        assertTrue(rows[0].contains("١٧ قيراطًا و٧ أسهم"))
        assertTrue(rows[0].contains("الغربي: قطعة ٧٦٩٧"))
        assertTrue(rows[1].contains("٢ قيراط"))
        assertTrue(rows[1].contains("الغربي: أرض"))
        assertFalse(rows[1].contains("٧٦٩٧"))
    }

    @Test fun composingTwiceDoesNotDuplicateOpeningRequestsOrClosing() {
        val parts = PetitionSubjectExtraction.extract(petition)
        val subject = UnifiedCaseSubject.compose(parts.explanation, parts.requests)
        assertTrue(subject.indexOf("بتسليم العين") < subject.indexOf("وحيث قال شارحًا"))
        assertEquals(subject, UnifiedCaseSubject.compose(subject, parts.requests))
        assertEquals(subject, UnifiedCaseSubject.compose(subject, ""))
        assertFalse(subject.contains("أودعت"))
        assertEquals(1, Regex("مما حدا به").findAll(subject).count())
    }

    @Test fun cleansServiceEvenInsideLabelledApiRequests() {
        val contaminated = petition.substringAfter("بناءً عليه\n")
        assertEquals(PetitionSubjectExtraction.extract(petition).requests, PetitionSubjectExtraction.cleanRequests(contaminated))
    }

    @Test fun instructionsCoverOriginalPetitionOnlyAndAllRequiredDetails() {
        val prompt = PetitionSubjectRules.PROMPT
        assertTrue(prompt.contains("لسماع الحكم"))
        assertTrue(prompt.contains("لا تخلط صفوف"))
        assertTrue(prompt.contains("صحيفة الدعوى وعريضة الدعوى"))
        assertTrue(prompt.contains("المأمورية"))
    }

    @Test fun apiOutputCanPutRequestsBeforeExplanation() {
        val parts = PetitionSubjectExtraction.extract("الطلبات الختامية:\nالحكم بتسليم العين.\nشرح الدعوى:\nالطالب يمتلك مساحة ٢ قيراط وحدها البحري الطريق.")
        assertEquals("الحكم بتسليم العين.", parts.requests)
        assertEquals("الطالب يمتلك مساحة ٢ قيراط وحدها البحري الطريق.", parts.explanation)
    }

    @Test fun malformedTablePreservesHeaderAndRowsForReview() {
        val table = "| رقم القطعة | المساحة | البحري |\n| ٧ | ٢ قيراط |"
        assertEquals(table, PetitionSubjectExtraction.tableToProse(table))
    }

    @Test fun labelledApiFieldsWorkInEitherOrder() {
        for (raw in listOf(
            "الطلبات الختامية: الحكم بتسليم الأرض.\nموضوع الدعوى: الطالب يمتلك ١٩ قيراطًا.\nمأمورية الحكم التمهيدي: المعاينة",
            "موضوع الدعوى: الطالب يمتلك ١٩ قيراطًا.\nالطلبات الختامية: الحكم بتسليم الأرض.\nمأمورية الحكم التمهيدي: المعاينة"
        )) {
            val parts = PetitionSubjectExtraction.extract(raw)
            assertEquals("الحكم بتسليم الأرض.", parts.requests)
            assertEquals("الطالب يمتلك ١٩ قيراطًا.", parts.explanation)
        }
    }
    @Test fun preservesIncompleteTableInsteadOfGuessingBounds() {
        val text = "| المساحة | البحري | القبلي |\n| ٢ قيراط | طريق |"
        assertTrue(PetitionSubjectExtraction.tableToProse(text).contains("| ٢ قيراط | طريق |"))
    }

    @Test fun doesNotMistakeSubstantiveMentionOfBailiffOrDefendantsForService() {
        val request = "الحكم بالتعويض عن خطأ المحضر وإلزام المعلن إليهم بالمصاريف."
        assertEquals(request, PetitionSubjectExtraction.cleanRequests(request))
        assertEquals(request, PetitionSubjectExtraction.extract("الطلبات الختامية:\n$request").requests)
    }
    @Test fun missingRequestsAreFlaggedWhenExplanationIsPresent() {
        assertTrue(PetitionSubjectExtraction.extract("الموضوع: الطالب يمتلك الأرض.").warnings.any { it.contains("الطلبات") })
    }
}
