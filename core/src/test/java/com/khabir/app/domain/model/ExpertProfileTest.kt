package com.khabir.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpertProfileTest {
    @Test
    fun `official profile requires identity office and address`() {
        assertFalse(ExpertProfile().isComplete)
        assertFalse(
            ExpertProfile(
                expertName = "خبير تجريبي",
                jobTitle = "خبير",
                department = "إدارة خبراء أسوان"
            ).isComplete
        )
        assertTrue(
            ExpertProfile(
                expertName = "خبير تجريبي",
                jobTitle = "خبير",
                department = "إدارة خبراء أسوان",
                officeAddress = "أسوان"
            ).isComplete
        )
    }

    @Test
    fun `attendance phrase has a usable default`() {
        assertTrue(ExpertProfile().attendancePhrase.isNotBlank())
    }
}
