package com.khabir.app.data.backup

import android.content.Context
import android.net.Uri
import com.khabir.app.data.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase
) {
    sealed class RestoreResult {
        data object Success : RestoreResult()
        data object WrongPasswordOrDamagedFile : RestoreResult()
    }

    fun createEncryptedBackup(destination: Uri, password: CharArray) {
        require(password.size >= MIN_PASSWORD_LENGTH) { "كلمة مرور النسخة الاحتياطية يجب ألا تقل عن ٦ أحرف" }
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
            while (cursor.moveToNext()) Unit
        }
        val databaseFile = context.getDatabasePath(AppDatabase.DB_NAME)
        require(databaseFile.isFile) { "قاعدة البيانات غير موجودة" }

        val salt = ByteArray(SALT_LENGTH).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_LENGTH).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val output = requireNotNull(context.contentResolver.openOutputStream(destination, "wt")) { "تعذر فتح ملف النسخة الاحتياطية" }
        BufferedOutputStream(output).use { raw ->
            raw.write(MAGIC)
            raw.write(salt)
            raw.write(iv)
            CipherOutputStream(raw, cipher).use { encrypted -> databaseFile.inputStream().use { it.copyTo(encrypted) } }
        }
        password.fill('\u0000')
    }

    fun restoreEncryptedBackup(source: Uri, password: CharArray): RestoreResult {
        require(password.size >= MIN_PASSWORD_LENGTH) { "أدخل كلمة مرور النسخة الاحتياطية" }
        val temporary = File(context.cacheDir, "restore_${System.currentTimeMillis()}.db")
        return runCatching {
            val input = requireNotNull(context.contentResolver.openInputStream(source)) { "تعذر فتح النسخة الاحتياطية" }
            BufferedInputStream(input).use { raw ->
                val magic = raw.readExact(MAGIC.size)
                require(magic.contentEquals(MAGIC)) { "صيغة النسخة الاحتياطية غير صحيحة" }
                val salt = raw.readExact(SALT_LENGTH)
                val iv = raw.readExact(IV_LENGTH)
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
                    init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
                }
                CipherInputStream(raw, cipher).use { decrypted -> temporary.outputStream().use { decrypted.copyTo(it) } }
            }
            require(temporary.inputStream().buffered().use { it.readExact(SQLITE_HEADER.size).contentEquals(SQLITE_HEADER) }) { "الملف المستعاد ليس قاعدة بيانات سليمة" }

            database.close()
            val target = context.getDatabasePath(AppDatabase.DB_NAME)
            target.parentFile?.mkdirs()
            temporary.copyTo(target, overwrite = true)
            File("${target.path}-wal").delete()
            File("${target.path}-shm").delete()
            RestoreResult.Success
        }.getOrElse { RestoreResult.WrongPasswordOrDamagedFile }
            .also {
                password.fill('\u0000')
                temporary.delete()
            }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun BufferedInputStream.readExact(count: Int): ByteArray {
        val bytes = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = read(bytes, offset, count - offset)
            require(read > 0) { "ملف نسخة احتياطية غير مكتمل" }
            offset += read
        }
        return bytes
    }

    companion object {
        private val MAGIC = "KHABIRB1".toByteArray(Charsets.US_ASCII)
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        private val secureRandom = SecureRandom()
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val PBKDF2_ITERATIONS = 150_000
        private const val MIN_PASSWORD_LENGTH = 6
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
