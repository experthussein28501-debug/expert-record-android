package com.khabir.app.presentation.cases

import com.khabir.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class LawyerNotificationTest {
    @Test fun explicitCityBuildsCourtAndBarAddressForEachLawyer() {
        listOf("كوم أمبو", "أسوان", "إدفو").forEach { city ->
            val parsed = PetitionIntakeParser.parse("المحامي: أحمد محمد علي | المدينة: $city")
            val lawyer = parsed.parties.single { it.role == PartyRole.LAWYER }
            assertEquals("أحمد محمد علي", lawyer.name)
            assertEquals("محكمة $city — نقابة المحامين ب$city", lawyer.address)
            assertEquals(lawyer.address, LawyerNotification.address(lawyer.address))
        }
    }
    @Test fun officeAddressIsNeverUsedAndCaseCourtDoesNotSupplyMissingCity() {
        val parsed = PetitionIntakeParser.parse("المحكمة: أسوان\nومحله المختار مكتب الأستاذ أحمد محمد المحامي\nالعنوان: شارع النيل رقم 12")
        assertEquals("", parsed.parties.single { it.role == PartyRole.LAWYER }.address)
        assertEquals("", LawyerNotification.address("شارع النيل 12"))
    }
    @Test fun rawPetitionExtractsNameAndCityWithoutTheOfficeStreet() {
        val lawyer = LawyerIntakeParser.parse("ومحله المختار مكتب الأستاذ أحمد محمد المحامي بكوم أمبو، شارع النيل").single()
        assertEquals("أحمد محمد", lawyer.name)
        assertEquals("محكمة كوم أمبو — نقابة المحامين بكوم أمبو", lawyer.address)
    }
    @Test fun splitLawyerTitleAndCityRemainLinkedToTheName() {
        val lawyer = LawyerIntakeParser.parse("ومحله المختار مكتب الأستاذ أحمد محمد\nالمحامي بأسوان، شارع تجريبي").single()
        assertEquals("أحمد محمد", lawyer.name)
        assertEquals("محكمة أسوان — نقابة المحامين بأسوان", lawyer.address)
    }
    @Test fun roleSurvivesDatabaseStringRoundTripWithoutBecomingALitigant() {
        val lawyer = Party(firstName = "أحمد", restName = "محمد", role = PartyRole.LAWYER, address = LawyerNotification.address("إدفو"), orderIndex = 0)
        assertEquals(PartyRole.LAWYER, PartyRole.parseStored(lawyer.storedRoleValue).first)
        assertFalse(lawyer.role.isPlaintiff)
        assertFalse(lawyer.role.isDefendant)
        val reviewed = PetitionIntakeParser.parse("الخصم: أحمد محمد | العنوان: ${lawyer.address} | الصفة: محامي | الدعوى: أصلية")
        assertEquals(lawyer.address, reviewed.parties.single().address)
    }
}
