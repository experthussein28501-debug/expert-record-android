package com.khabir.app.presentation.cases

import com.khabir.app.domain.model.PartyRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PetitionIntakeParserTest {

    @Test
    fun `accepts referral and case receipt date wording`() {
        val result = PetitionIntakeParser.parse(
            """
            رقم الوارد 88
            تاريخ الإحالة 04/09/2026
            تاريخ استلام القضية 05/09/2026
            """.trimIndent()
        )

        assertEquals("88", result.incomingNo)
        assertEquals(LocalDate.of(2026, 9, 4), result.incomingDate)
        assertEquals(LocalDate.of(2026, 9, 5), result.receiptDate)
    }

    @Test
    fun `parses a single OCR line with spelling variants and numbered parties`() {
        val result = PetitionIntakeParser.parse(
            "الدعوي رقم ٤٨ لسنه ٢٠٢٥ مدني كلي كوم أمبو المرفوعه من / ١- أحمد محمد علي ٢- فاطمة حسن محمود ضد / ١- محمود سالم حسن الموضوع"
        )

        assertEquals("٤٨", result.caseNo)
        assertEquals("٢٠٢٥", result.caseYear)
        assertEquals(2, result.parties.count { it.role == PartyRole.PLAINTIFF })
        assertEquals(1, result.parties.count { it.role == PartyRole.DEFENDANT })
    }

    @Test
    fun `parses legal cover wording and capacity`() {
        val result = PetitionIntakeParser.parse(
            """
            رقم الوارد 5373
            تاريخ الوارد 23/08/2026
            فى الدعوى رقم 272 لسنة 2025 مدني كلي كوم أمبو
            المحكمة: كوم أمبو
            المرفوعة من / السيد أحمد محمد علي بصفته
            ضد / محمود حسن سالم
            تاريخ الاستلام 20/08/2026
            تاريخ الحكم التمهيدي 15/07/2026
            """.trimIndent()
        )

        assertEquals("5373", result.incomingNo)
        assertEquals(LocalDate.of(2026, 8, 23), result.incomingDate)
        assertEquals("272", result.caseNo)
        assertEquals("2025", result.caseYear)
        assertEquals("مدني كلي", result.caseType)
        assertEquals("كوم أمبو", result.court)
        assertEquals(LocalDate.of(2026, 8, 20), result.receiptDate)
        assertEquals(LocalDate.of(2026, 7, 15), result.preliminaryJudgmentDate)

        val plaintiff = result.parties.first { it.role == PartyRole.PLAINTIFF }
        val defendant = result.parties.first { it.role == PartyRole.DEFENDANT }
        assertEquals("أحمد محمد علي", plaintiff.name)
        assertTrue(plaintiff.withCapacity)
        assertEquals("محمود حسن سالم", defendant.name)
        assertFalse(defendant.withCapacity)
    }

    @Test
    fun `accepts المقامة من and Arabic numerals`() {
        val result = PetitionIntakeParser.parse(
            """
            الدعوى رقم ٤٨ لسنة ٢٠٢٥
            المقامة من / فاطمة علي حسن
            ضد / محمد أحمد محمود بصفته
            """.trimIndent()
        )

        assertEquals("٤٨", result.caseNo)
        assertEquals("٢٠٢٥", result.caseYear)
        assertEquals(2, result.parties.size)
        assertEquals(PartyRole.PLAINTIFF, result.parties[0].role)
        assertEquals(PartyRole.DEFENDANT, result.parties[1].role)
        assertTrue(result.parties[1].withCapacity)
    }

    @Test
    fun `parses explicit party lines with addresses`() {
        val result = PetitionIntakeParser.parse(
            """
            المدعي: أحمد سالم العنوان: أسوان - كوم أمبو
            المدعى عليه بصفته: حسن محمود العنوان: القاهرة
            """.trimIndent()
        )

        assertEquals(2, result.parties.size)
        assertEquals("أحمد سالم", result.parties[0].name)
        assertEquals("أسوان - كوم أمبو", result.parties[0].address)
        assertEquals("حسن محمود", result.parties[1].name)
        assertEquals("القاهرة", result.parties[1].address)
        assertTrue(result.parties[1].withCapacity)
    }

    @Test
    fun `parses several plaintiffs and defendants from numbered cover blocks`() {
        val result = PetitionIntakeParser.parse(
            """
            الدعوى رقم 123 لسنة 2026 مدني كلي
            المرفوعة من /
            ١- أحمد محمد علي
            العنوان: كوم أمبو - أسوان
            ٢- محمود سالم حسن بصفته
            العنوان: دراو - أسوان
            ضد /
            1- حسن علي محمود
            العنوان: أسوان
            2- فاطمة أحمد حسن بصفتها العنوان: القاهرة
            الموضوع
            طلبات المدعين
            """.trimIndent()
        )

        val plaintiffs = result.parties.filter { it.role == PartyRole.PLAINTIFF }
        val defendants = result.parties.filter { it.role == PartyRole.DEFENDANT }

        assertEquals(2, plaintiffs.size)
        assertEquals("أحمد محمد علي", plaintiffs[0].name)
        assertEquals("كوم أمبو - أسوان", plaintiffs[0].address)
        assertEquals("محمود سالم حسن", plaintiffs[1].name)
        assertTrue(plaintiffs[1].withCapacity)
        assertEquals("دراو - أسوان", plaintiffs[1].address)

        assertEquals(2, defendants.size)
        assertEquals("حسن علي محمود", defendants[0].name)
        assertEquals("أسوان", defendants[0].address)
        assertEquals("فاطمة أحمد حسن", defendants[1].name)
        assertTrue(defendants[1].withCapacity)
        assertEquals("القاهرة", defendants[1].address)
    }

    @Test
    fun `does not treat following report section as a party`() {
        val result = PetitionIntakeParser.parse(
            """
            المرفوعة من / أحمد محمد علي
            ضد / حسن محمود سالم
            المأمورية
            الانتقال والمعاينة وبيان وجه الحق
            """.trimIndent()
        )

        assertEquals(2, result.parties.size)
        assertTrue(result.parties.none { it.name.contains("المأمورية") })
        assertTrue(result.parties.none { it.name.contains("الانتقال") })
    }

    @Test
    fun `does not treat recipients and address heading as a defendant`() {
        val result = PetitionIntakeParser.parse(
            """
            المرفوعة من / أحمد محمد علي
            ضد / حسن محمود سالم
            المخاطبون والعناوين:
            مكتب الأستاذ محمد - أسوان
            """.trimIndent()
        )

        assertEquals(2, result.parties.size)
        assertTrue(result.parties.none { it.name.contains("المخاطبون") || it.name.contains("مكتب الأستاذ") })
    }

    @Test
    fun `extracts case type and court from one legal heading line`() {
        val civil = PetitionIntakeParser.parse("فى الدعوى رقم ٢٧٢ لسنة ٢٠٢٥ مدني جزئي كوم أمبو")
        assertEquals("مدني جزئي", civil.caseType)
        assertEquals("كوم أمبو", civil.court)

        val criminal = PetitionIntakeParser.parse("في الدعوى رقم 1334 لسنة 2014 جنح مركز دراو")
        assertEquals("جنح", criminal.caseType)
        assertEquals("مركز دراو", criminal.court)

        val appeal = PetitionIntakeParser.parse("الدعوى رقم 871 لسنة 34 استئناف عالي قنا مأمورية استئناف أسوان")
        assertEquals("استئناف عالي", appeal.caseType)
        assertEquals("قنا مأمورية استئناف أسوان", appeal.court)
    }

    @Test
    fun `numbered case identity overrides unrelated court preamble`() {
        val result = PetitionIntakeParser.parse(
            """
            الدعوى رقم 48 لسنة 2025 مدني كلي كوم أمبو
            المحكمة: مأمورية نصر النوبة
            """.trimIndent()
        )

        assertEquals("مدني كلي", result.caseType)
        assertEquals("كوم أمبو", result.court)
    }

    @Test
    fun `cleans common hidden OCR and Arabic layout artifacts before parsing`() {
        val result = PetitionIntakeParser.parse(
            "\u202Bالدعــــوى\u200F رقم\u00A0٢٧٢ لسنة ٢٠٢٥ مدني كلي كوم أمبو\u202C\n" +
                "المرفــــوعة من :: أحمد محمد علي بصفته\n" +
                "ضــــد / محمود حسن سالم"
        )

        assertEquals("٢٧٢", result.caseNo)
        assertEquals("٢٠٢٥", result.caseYear)
        assertEquals("مدني كلي", result.caseType)
        assertEquals("كوم أمبو", result.court)
        assertEquals("أحمد محمد علي", result.parties.first { it.role == PartyRole.PLAINTIFF }.name)
        assertTrue(result.parties.first { it.role == PartyRole.PLAINTIFF }.withCapacity)
        assertEquals("محمود حسن سالم", result.parties.first { it.role == PartyRole.DEFENDANT }.name)
    }

    @Test
    fun `parses common legal address phrases on same or following line`() {
        val result = PetitionIntakeParser.parse(
            """
            المرفوعة من /
            ١- أحمد محمد علي المقيم في قرية المنصورية - مركز دراو
            ٢- فاطمة حسن محمود
            ومحلها المختار: مكتب الأستاذ محمد - أسوان
            ضد /
            حسن سالم أحمد
            موطنه: شارع المحكمة - كوم أمبو
            الموضوع
            """.trimIndent()
        )

        val plaintiffs = result.parties.filter { it.role == PartyRole.PLAINTIFF }
        val defendant = result.parties.first { it.role == PartyRole.DEFENDANT }
        assertEquals("قرية المنصورية - مركز دراو", plaintiffs[0].address)
        assertEquals("", plaintiffs[1].address)
        assertTrue(result.lawyerContact.orEmpty().contains("محمد"))
        assertEquals("شارع المحكمة - كوم أمبو", defendant.address)
    }

    @Test
    fun `extracts all parties and addresses from a preliminary judgment heading`() {
        val result = PetitionIntakeParser.parse(
            """
            الحكم التمهيدي في الدعوى رقم ٣١٢ لسنة ٢٠٢٦ مدني كلي أسوان
            المقامة من /
            السيد / أحمد محمود علي المقيم في شارع السوق - أسوان
            ٢- فاطمة حسن سالم
            عنوانها: قرية المنصورية - دراو
            ضد /
            السيد / محمد علي حسن بصفته المقيم في كوم أمبو - أسوان
            الوقائع
            بعد سماع المرافعة والاطلاع على الأوراق
            """.trimIndent()
        )

        assertEquals("٣١٢", result.caseNo)
        assertEquals("٢٠٢٦", result.caseYear)
        assertEquals(2, result.parties.count { it.role == PartyRole.PLAINTIFF })
        assertEquals(1, result.parties.count { it.role == PartyRole.DEFENDANT })
        assertEquals("شارع السوق - أسوان", result.parties[0].address)
        assertEquals("قرية المنصورية - دراو", result.parties[1].address)
        val defendant = result.parties.first { it.role == PartyRole.DEFENDANT }
        assertEquals("محمد علي حسن", defendant.name)
        assertTrue(defendant.withCapacity)
        assertEquals("كوم أمبو - أسوان", defendant.address)
    }

    @Test
    fun `extracts plaintiffs and addressed defendants from service wording`() {
        val result = PetitionIntakeParser.parse(
            "بناء على طلب السادة أحمد محمود علي، فاطمة حسن سالم المقيمين بناحية كوم أمبو " +
                "ومحلهم المختار مكتب الأستاذ محمد عبد الله المحامي بأسوان " +
                "ضد حسن علي محمود، أنا المحضر بمحكمة أسوان انتقلت في تاريخه إلى ناحية دراو " +
                "حيث وجود كل من حسن علي محمود وفاطمة علي حسن"
        )

        val plaintiffs = result.parties.filter { it.role == PartyRole.PLAINTIFF }
        val defendants = result.parties.filter { it.role == PartyRole.DEFENDANT }
        assertTrue(plaintiffs.any { it.name.contains("أحمد") })
        assertTrue(plaintiffs.all { it.address.contains("كوم أمبو") && !it.address.contains("مكتب الأستاذ") })
        assertTrue(defendants.any { it.name.contains("حسن علي محمود") })
    }

    @Test
    fun `extracts petition subject without final requests`() {
        val result = PetitionIntakeParser.parse(
            """
            الدعوى رقم ١٠٥ لسنة ٢٠٢٥ مدني جزئي كوم أمبو
            الموضوع
            يمتلك أطراف التداعي أطيانًا زراعية بطريق الميراث ويرغب المدعون في فرز وتجنيب نصيبهم.
            بناء عليه
            يلتمس المدعون ندب خبير في الدعوى.
            """.trimIndent()
        )

        assertEquals(
            "يمتلك أطراف التداعي أطيانًا زراعية بطريق الميراث ويرغب المدعون في فرز وتجنيب نصيبهم.",
            result.subjectOfCase
        )
        assertTrue(result.subjectOfCase?.contains("يلتمس") == false)
    }

    @Test
    fun `extracts preliminary judgment mission`() {
        val result = PetitionIntakeParser.parse(
            """
            مأمورية الحكم التمهيدي:
            الانتقال إلى أطيان التداعي لمعاينتها وبيان حدودها ومساحتها وسند الملكية.
            وبيان ما إذا كانت تقبل القسمة عينًا من عدمه وتقدير الريع.
            مباشرة المأمورية
            تم إخطار الخصوم.
            """.trimIndent()
        )

        val mission = requireNotNull(result.preliminaryMission)
        assertTrue(mission.contains("بيان حدودها"))
        assertTrue(mission.contains("تقدير الريع"))
        assertTrue(!mission.contains("تم إخطار"))
    }

    @Test
    fun `applies one shared address to all unaddressed parties in same side block`() {
        val result = PetitionIntakeParser.parse(
            """
            المرفوعة من /
            ١- أحمد محمد علي
            ٢- فاطمة حسن محمود
            المقيمون بناحية الكوبانية - مركز أسوان
            ضد /
            محمود سالم حسن
            الموضوع
            """.trimIndent()
        )

        val plaintiffs = result.parties.filter { it.role == PartyRole.PLAINTIFF }
        assertEquals(2, plaintiffs.size)
        assertTrue(plaintiffs.all { it.address == "ناحية الكوبانية - مركز أسوان" })
    }
    @Test
    fun `preserves judicial year suffix through shared petition parser`() {
        val highAppeal = PetitionIntakeParser.parse(
            "محكمة استئناف قنا\nالدعوى رقم 350 لسنة 21 ق استئناف عالي قنا"
        )
        val administrative = PetitionIntakeParser.parse(
            "محكمة القضاء الإداري\nالدعوى رقم 180 لسنة ١٠١ ق قضاء إداري"
        )
        assertEquals("21ق", highAppeal.caseYear)
        assertEquals("استئناف عالي", highAppeal.caseType)
        assertEquals("101ق", administrative.caseYear)
        assertEquals("قضاء إداري", administrative.caseType)
    }

    @Test
    fun `chosen lawyer office is never copied as party address`() {
        val result = PetitionIntakeParser.parse(
            """
            بناء على طلب السادة أحمد محمود علي وفاطمة حسن سالم
            المقيمون بناحية كوم أمبو
            ومحلهم المختار مكتب الأستاذ محمد عبد الله المحامي بأسوان
            ضد حسن علي محمود
            """.trimIndent()
        )
        val plaintiffs = result.parties.filter { it.role == PartyRole.PLAINTIFF }
        assertTrue(plaintiffs.isNotEmpty())
        assertTrue(plaintiffs.all { it.address.contains("كوم أمبو") })
        assertTrue(plaintiffs.none { it.address.contains("مكتب") || it.address.contains("المحامي") })
        val lawyer = result.parties.first { it.role == PartyRole.LAWYER }
        assertEquals("محكمة أسوان — نقابة المحامين بأسوان", lawyer.address)
    }

    @Test
    fun `keeps q suffix when judicial type is only in court heading`() {
        val result = PetitionIntakeParser.parse(
            """
            محكمة القضاء الإداري بأسوان
            الدعوى رقم ١٨٠ لسنة ١٠١ ق
            """.trimIndent()
        )
        assertEquals("١٠١ق", result.caseYear)
        assertEquals("قضاء إداري", result.caseType)

        val highAppeal = PetitionIntakeParser.parse(
            """
            محكمة استئناف قنا
            الدعوى رقم ٣٥٠ لسنة ٢١ ق
            """.trimIndent()
        )
        assertEquals("٢١ق", highAppeal.caseYear)
        assertEquals("استئناف عالي", highAppeal.caseType)
    }

}
