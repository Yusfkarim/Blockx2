package com.agon.app

import com.agon.app.settingsprotection.data.ProtectionCommitmentStore
import com.agon.app.settingsprotection.domain.PinPolicy
import com.agon.app.settingsprotection.domain.UsernamePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionCommitmentStoreTest {

    @Test
    fun testDayOptionsRange() {
        val normalOptions = ProtectionCommitmentStore.NORMAL_DAY_OPTIONS
        val strongOptions = ProtectionCommitmentStore.STRONG_DAY_OPTIONS

        assertEquals(
            listOf(
                ProtectionCommitmentStore.TEST_5_MINUTES,
                30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330, 360,
            ),
            normalOptions,
        )
        assertEquals(normalOptions, strongOptions)
        assertEquals(ProtectionCommitmentStore.TEST_5_MINUTES, strongOptions.first())
        assertEquals(360, strongOptions.last())
    }

    @Test
    fun testDurationMillis() {
        val test5min = ProtectionCommitmentStore.durationMillisForOption(ProtectionCommitmentStore.TEST_5_MINUTES)
        assertEquals(5L * 60L * 1000L, test5min)

        val thirtyDays = ProtectionCommitmentStore.durationMillisForOption(30)
        assertEquals(30L * 24L * 60L * 60L * 1000L, thirtyDays)
    }

    @Test
    fun testFormatOptionLabel() {
        assertEquals("5 mins (Test)", ProtectionCommitmentStore.formatOptionLabel(ProtectionCommitmentStore.TEST_5_MINUTES, "en"))
        assertEquals("5 دقائق (تجربة)", ProtectionCommitmentStore.formatOptionLabel(ProtectionCommitmentStore.TEST_5_MINUTES, "ar"))
        assertEquals("٥ خولەک (تێست)", ProtectionCommitmentStore.formatOptionLabel(ProtectionCommitmentStore.TEST_5_MINUTES, "ku"))

        assertEquals("30 days", ProtectionCommitmentStore.formatOptionLabel(30, "en"))
        assertEquals("30 يوم", ProtectionCommitmentStore.formatOptionLabel(30, "ar"))
        assertEquals("30 ڕۆژ", ProtectionCommitmentStore.formatOptionLabel(30, "ku"))
    }

    @Test
    fun testFormatStrongRemaining() {
        val oneDayMillis = 86_400_000L * 2 + 3_600_000L * 3 + 60_000L * 15
        val ar = ProtectionCommitmentStore.formatStrongRemaining(oneDayMillis, "ar")
        assertTrue(ar.contains("2 يوم") || ar.contains("يوم"))

        val en = ProtectionCommitmentStore.formatStrongRemaining(oneDayMillis, "en")
        assertEquals("2 d 3 h 15 m", en)

        val ku = ProtectionCommitmentStore.formatStrongRemaining(oneDayMillis, "ku")
        assertTrue(ku.contains("2 ڕۆژ") || ku.contains("ڕۆژ"))

        val underHourMillis = 3_600_000L * 0 + 60_000L * 4 + 1000L * 25
        val clockEn = ProtectionCommitmentStore.formatStrongRemaining(underHourMillis, "en")
        assertEquals("04:25", clockEn)
    }

    @Test
    fun testPinAndUsernamePolicy() {
        assertTrue(PinPolicy.isValid("1234"))
        assertTrue(PinPolicy.isValid("123456"))
        assertFalse(PinPolicy.isValid("123")) // below min length
        assertFalse(PinPolicy.isValid("123456789012345678901")) // over max length

        assertTrue(UsernamePolicy.isValid("user1"))
        assertFalse(UsernamePolicy.isValid("ab")) // below min length
    }
}
