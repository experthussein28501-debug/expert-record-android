package com.khabir.app.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.khabir.app.data.ai.PersonalAiKeyStore
import com.khabir.app.data.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface BackupOperationResult {
    data class Success(val message: String, val restartRequired: Boolean = false) : BackupOperationResult
    data class Failure(val message: String) : BackupOperationResult
}

/**
 * Creates a password-encrypted backup that can be written through Android's Storage Access
 * Framework. That means Google Drive, OneDrive and Dropbox work without giving Khabir central
 * access to the user's cloud account; the user chooses the destination provider themselves.
 *
 * The API key is deliberately excluded: Android Keystore credentials are device-bound and must
 * be entered again on a replacement phone. Case data, reports, work minutes, notifications,
 * expert profile, sketches and portable AI instructions/style rules are included.
 */
@Singleton
class EncryptedBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val personalAiKeyStore: PersonalAiKeyStore
) {
    suspend fun exportTo(uri: Uri, password: String): BackupOperationResult = withContext(Dispatchers.IO) {
        if (password.length < MIN_PASSWORD_LENGTH) {
            return@withContext BackupOperationResult.Failure("اجعل كلمة مرور النسخة الاحتياطية 6 أحرف على الأقل")
        }
        val zip = File.createTempFile("khabir_backup_", ".zip", context.cacheDir)
        try {
            checkpointDatabase()
            buildPayload(zip)
            val output = context.contentResolver.openOutputStream(uri, "w")
                ?: return@withContext BackupOperationResult.Failure("تعذر فتح مكان حفظ النسخة الاحتياطية")
            output.use { raw ->
                BackupCipher.encryptedOutput(BufferedOutputStream(raw), password).use { encrypted ->
                    zip.inputStream().buffered().use { input -> input.copyTo(encrypted) }
                }
            }
            BackupOperationResult.Success("تم حفظ نسخة احتياطية مشفرة. احتفظ بكلمة المرور في مكان آمن.")
        } catch (error: Throwable) {
            BackupOperationResult.Failure(error.message ?: "تعذر إنشاء النسخة الاحتياطية")
        } finally {
            zip.delete()
        }
    }

    suspend fun stageRestore(uri: Uri, password: String): BackupOperationResult = withContext(Dispatchers.IO) {
        if (password.isBlank()) return@withContext BackupOperationResult.Failure("أدخل كلمة مرور النسخة الاحتياطية")
        val zip = File.createTempFile("khabir_restore_", ".zip", context.cacheDir)
        val staging = PendingBackupRestore.stagingDir(context)
        try {
            staging.deleteRecursively()
            staging.mkdirs()
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext BackupOperationResult.Failure("تعذر فتح ملف النسخة الاحتياطية")
            input.use { raw ->
                BackupCipher.decryptedInput(BufferedInputStream(raw), password).use { decrypted ->
                    zip.outputStream().buffered().use { output -> copyBounded(decrypted, output, 512L * 1024 * 1024) }
                }
            }
            extractPayload(zip, staging)
            validateStaging(staging)
            File(staging, READY_MARKER).writeText("ready")
            BackupOperationResult.Success(
                "تم تجهيز الاستعادة بأمان. أغلق التطبيق وافتحه من جديد لتطبيق البيانات.",
                restartRequired = true
            )
        } catch (error: Throwable) {
            staging.deleteRecursively()
            val detail = error.message.orEmpty()
            val message = if (
                detail.contains("tag", ignoreCase = true) ||
                detail.contains("AEAD", ignoreCase = true) ||
                detail.contains("mac", ignoreCase = true)
            ) "كلمة المرور غير صحيحة أو ملف النسخة تالف" else (error.message ?: "تعذر استعادة النسخة الاحتياطية")
            BackupOperationResult.Failure(message)
        } finally {
            zip.delete()
        }
    }

    private fun checkpointDatabase() {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
            while (cursor.moveToNext()) Unit
        }
    }

    private fun buildPayload(outputFile: File) {
        val databaseFile = context.getDatabasePath(AppDatabase.DB_NAME)
        require(databaseFile.isFile) { "قاعدة بيانات سجل الخبير غير موجودة" }
        ZipOutputStream(BufferedOutputStream(outputFile.outputStream())).use { zip ->
            val manifest = buildString {
                appendLine("format=2")
                appendLine("schema=${AppDatabase.SCHEMA_VERSION}")
                appendLine("createdAt=${Instant.now()}")
                appendLine("apiKeyIncluded=false")
            }
            zip.putText("manifest.txt", manifest)
            zip.putFile("database/${AppDatabase.DB_NAME}", databaseFile)
            zip.putDirectory("files/case-source-pages", File(context.filesDir,"case-source-pages"))
            zip.putDirectory("files/report-source-pages", File(context.filesDir,"report-source-pages"))
            zip.putDirectory("files/report_sketches", File(context.filesDir, "report_sketches"))
            zip.putDirectory("files/report_sketch_drafts", File(context.filesDir, "report_sketch_drafts"))
            zip.putText("settings/portable.txt", portableSettings())
            zip.putText("settings/agenda.json", com.khabir.app.data.security.AgendaVault(context).exportPortable())
            zip.putDirectory("files/agenda-media", File(context.filesDir, "agenda-media"))
        }
    }

    private fun portableSettings(): String {
        fun packed(value: String): String = Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        return buildString {
            appendLine("provider=${personalAiKeyStore.readProvider().name}")
            appendLine("instructions=${packed(personalAiKeyStore.readInstructions())}")
            appendLine("style=${packed(personalAiKeyStore.readReportStyleMemory())}")
            appendLine("styleHistory=${packed(com.khabir.app.domain.model.ApprovedStyleRules.encode(personalAiKeyStore.readReportStyleHistory()))}")
        }
    }

    private fun extractPayload(zipFile: File, staging: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zip ->
            var extractedBytes = 0L
            var entries = 0
            val seen = mutableSetOf<String>()
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= 10_000) { "عدد ملفات النسخة أكبر من الحد المسموح" }
                val safeName = entry.name.replace('\\', '/')
                val allowed = safeName == "manifest.txt" || safeName == "settings/portable.txt" || safeName == "settings/agenda.json" || safeName.startsWith("files/agenda-media/") ||
                    safeName == "database/${AppDatabase.DB_NAME}" ||
                    safeName.startsWith("files/report-source-pages/") || safeName.startsWith("files/case-source-pages/") || safeName.startsWith("files/report_sketches/") || safeName.startsWith("files/report_sketch_drafts/")
                if (!allowed || safeName.contains("../")) {
                    throw IllegalArgumentException("ملف أو مسار غير مسموح داخل النسخة")
                }
                require(seen.add(safeName)) { "ملف مكرر داخل النسخة" }
                val target = File(staging, safeName)
                val canonicalRoot = staging.canonicalFile
                val canonicalTarget = target.canonicalFile
                require(canonicalTarget.path.startsWith(canonicalRoot.path + File.separator)) { "مسار غير صالح داخل النسخة" }
                if (entry.isDirectory) target.mkdirs() else {
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { output -> extractedBytes += copyBounded(zip, output, minOf(256L * 1024 * 1024, 1024L * 1024 * 1024 - extractedBytes)) }
                }
                zip.closeEntry()
            }
        }
    }

    private fun validateStaging(staging: File) {
        val manifest = File(staging, "manifest.txt")
        val db = File(staging, "database/${AppDatabase.DB_NAME}")
        require(manifest.isFile && db.isFile) { "ملف النسخة الاحتياطية غير مكتمل" }
        val schema = manifest.readLines().firstOrNull { it.startsWith("schema=") }?.substringAfter('=')?.toIntOrNull()
            ?: throw IllegalArgumentException("تعذر قراءة إصدار قاعدة البيانات من النسخة")
        require(schema <= AppDatabase.SCHEMA_VERSION) { "هذه النسخة الاحتياطية صادرة من إصدار أحدث من التطبيق" }
        val header = ByteArray(SQLITE_HEADER.size)
        db.inputStream().use { input -> require(input.read(header) == header.size) { "قاعدة البيانات داخل النسخة غير صالحة" } }
        require(header.contentEquals(SQLITE_HEADER)) { "قاعدة البيانات داخل النسخة غير صالحة" }
    }

    private fun ZipOutputStream.putText(name: String, value: String) {
        putNextEntry(ZipEntry(name))
        write(value.toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.putFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().buffered().use { it.copyTo(this) }
        closeEntry()
    }

    private fun ZipOutputStream.putDirectory(prefix: String, directory: File) {
        if (!directory.isDirectory) return
        directory.walkTopDown().filter(File::isFile).forEach { file ->
            val relative = file.relativeTo(directory).invariantSeparatorsPath
            putFile("$prefix/$relative", file)
        }
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 6
        const val READY_MARKER = ".ready"
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(StandardCharsets.US_ASCII)
    }
}

/** Applies a staged restore before Room opens on the next cold application start. */
object PendingBackupRestore {
    private const val PREFS = "khabir_backup_restore"
    private const val STATUS = "last_status"

    fun stagingDir(context: Context): File = File(context.filesDir, "pending_backup_restore")

    fun applyIfPresent(context: Context) {
        val staging = stagingDir(context)
        if (!File(staging, EncryptedBackupService.READY_MARKER).isFile) return
        runCatching {
            val sourceDb = File(staging, "database/${AppDatabase.DB_NAME}")
            require(sourceDb.isFile)
            val targetDb = context.getDatabasePath(AppDatabase.DB_NAME)
            targetDb.parentFile?.mkdirs()
            File(targetDb.absolutePath + "-wal").delete()
            File(targetDb.absolutePath + "-shm").delete()
            val temporary = File(targetDb.parentFile, targetDb.name + ".restore")
            sourceDb.copyTo(temporary, overwrite = true)
            java.nio.file.Files.move(temporary.toPath(), targetDb.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            if (File(staging,"files/case-source-pages").exists()) replaceDirectory(File(staging,"files/case-source-pages"),File(context.filesDir,"case-source-pages"))
            else File(context.filesDir,"case-source-pages").deleteRecursively()
            if (File(staging,"files/report-source-pages").exists()) replaceDirectory(File(staging,"files/report-source-pages"),File(context.filesDir,"report-source-pages"))
            else File(context.filesDir,"report-source-pages").deleteRecursively()
            replaceDirectory(File(staging, "files/report_sketches"), File(context.filesDir, "report_sketches"))
            replaceDirectory(File(staging, "files/report_sketch_drafts"), File(context.filesDir, "report_sketch_drafts"))
            restorePortableSettings(context, File(staging, "settings/portable.txt"))
            val agenda = File(staging, "settings/agenda.json")
            if (agenda.isFile) {
                replaceDirectory(File(staging, "files/agenda-media"), File(context.filesDir, "agenda-media"))
                com.khabir.app.data.security.AgendaVault(context).restorePortable(agenda.readText())
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(STATUS, "تم تطبيق النسخة الاحتياطية بنجاح")
                .apply()
            staging.deleteRecursively()
        }.onFailure { error ->
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(STATUS, "تعذر تطبيق النسخة الاحتياطية: ${error.message.orEmpty()}")
                .apply()
        }
    }

    fun consumeLastStatus(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(STATUS, null)
        if (value != null) prefs.edit().remove(STATUS).apply()
        return value
    }

    private fun replaceDirectory(source: File, target: File) {
        if (!source.exists()) return
        target.deleteRecursively(); target.mkdirs()
        source.walkTopDown().filter(File::isFile).forEach { file ->
            val destination = File(target, file.relativeTo(source).invariantSeparatorsPath)
            destination.parentFile?.mkdirs(); file.copyTo(destination, overwrite = true)
        }
    }

    private fun restorePortableSettings(context: Context, file: File) {
        if (!file.isFile) return
        val values = file.readLines().mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
        }.toMap()
        fun unpack(key: String): String = runCatching {
            String(Base64.decode(values[key].orEmpty(), Base64.NO_WRAP), StandardCharsets.UTF_8)
        }.getOrDefault("")
        val prefs = context.getSharedPreferences("secure_ai_preferences", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("personal_gemini_key")
            .putString("personal_ai_provider", values["provider"] ?: "GEMINI")
            .putString("personal_ai_instructions", unpack("instructions"))
            .putString("approved_style_rules_v2", unpack("style"))
            .putString("approved_style_history_v3",unpack("styleHistory"))
            .apply()
    }
}

/** Password-based authenticated encryption used by backup files. */
internal object BackupCipher {
    private val MAGIC = "KHABIRB2".toByteArray(StandardCharsets.US_ASCII)
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val ITERATIONS = 180_000

    fun encryptedOutput(output: OutputStream, password: String): OutputStream {
        val salt = ByteArray(SALT_SIZE).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_SIZE).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        output.write(MAGIC); output.write(salt); output.write(iv)
        cipher.updateAAD(MAGIC)
        return CipherOutputStream(output, cipher)
    }

    fun decryptedInput(input: InputStream, password: String): InputStream {
        val magic = input.readExactly(MAGIC.size)
        require(magic.contentEquals(MAGIC)) { "هذا الملف ليس نسخة احتياطية مشفرة من سجل الخبير" }
        val salt = input.readExactly(SALT_SIZE)
        val iv = input.readExactly(IV_SIZE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return CipherInputStream(input, cipher)
    }

    internal fun encryptBytes(value: ByteArray, password: String): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        encryptedOutput(output, password).use { it.write(value) }
        return output.toByteArray()
    }

    internal fun decryptBytes(value: ByteArray, password: String): ByteArray =
        decryptedInput(value.inputStream(), password).use { it.readBytes() }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun InputStream.readExactly(size: Int): ByteArray {
        val result = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = read(result, offset, size - offset)
            if (read < 0) throw IllegalArgumentException("ملف النسخة الاحتياطية غير مكتمل")
            offset += read
        }
        return result
    }
}
