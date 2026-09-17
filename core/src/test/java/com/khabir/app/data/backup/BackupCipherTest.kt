package com.khabir.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCipherTest {
    @Test
    fun `encrypted backup decrypts with same password`() {
        val source = "قضية 105 لسنة 2025 — بيانات سرية".toByteArray(Charsets.UTF_8)
        val encrypted = BackupCipher.encryptBytes(source, "strong-pass-123")
        val restored = BackupCipher.decryptBytes(encrypted, "strong-pass-123")

        assertArrayEquals(source, restored)
        assertFalse(encrypted.toList().windowed(source.size).any { it.toByteArray().contentEquals(source) })
    }

    @Test
    fun `wrong password cannot decrypt backup`() {
        val encrypted = BackupCipher.encryptBytes("بيانات".toByteArray(Charsets.UTF_8), "correct-password")
        assertThrows(Throwable::class.java) {
            BackupCipher.decryptBytes(encrypted, "wrong-password")
        }
    }

    private fun List<Byte>.toByteArray(): ByteArray = ByteArray(size) { this[it] }
}
