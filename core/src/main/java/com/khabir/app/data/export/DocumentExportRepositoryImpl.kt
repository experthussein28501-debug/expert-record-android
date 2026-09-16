package com.khabir.app.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.khabir.app.domain.model.NotificationBatch
import com.khabir.app.domain.model.WorkMinutesEntry
import com.khabir.app.domain.repository.DocumentExportRepository
import com.khabir.core.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class DocumentExportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentExportRepository {
    private val docxBuilder = DocxBuilder()
    private val legalReportDocxBuilder = LegalReportDocxBuilder()
    private val legalReportPdfBuilder = LegalReportPdfBuilder()
    private val officialNotificationDocxBuilder = OfficialNotificationDocxBuilder()
    private val officialSirkisDocxBuilder = OfficialSirkisDocxBuilder()
    private val officialEnvelopeDocxBuilder = OfficialEnvelopeDocxBuilder()
    private val officialMailCoverDocxBuilder = OfficialMailCoverDocxBuilder()
    private val workMinutesDocxBuilder = WorkMinutesDocxBuilder()

    /** شعار وزارة العدل — يُقرأ مرة واحدة كبايتات أصلية من res/raw لتضمينه في المستندات. */
    private val ministryLogoBytes: ByteArray? by lazy {
        runCatching { context.resources.openRawResource(R.raw.logo_ministry_of_justice).use { it.readBytes() } }.getOrNull()
    }

    override suspend fun exportTitledTableAsWord(fileNamePrefix: String, title: String, subtitle: String?, columns: List<String>, rows: List<List<String>>): Uri = withContext(Dispatchers.IO) {
        val file = exportFile(fileNamePrefix, "docx")
        docxBuilder.buildTitledTableToFile(file, title, subtitle, columns, rows)
        uriFor(file)
    }

    override suspend fun exportReportAsWord(fileNamePrefix: String, reportTitle: String, coverFields: List<Pair<String, String>>, sections: List<Pair<String, String>>, siteSketchFile: File?): Uri = withContext(Dispatchers.IO) {
        val file = exportFile(fileNamePrefix, "docx")
        legalReportDocxBuilder.buildToFile(file, reportTitle, coverFields, sections, siteSketchFile, ministryLogoBytes)
        uriFor(file)
    }

    override suspend fun exportReportAsPdf(fileNamePrefix: String, reportTitle: String, coverFields: List<Pair<String, String>>, sections: List<Pair<String, String>>, siteSketchFile: File?): Uri = withContext(Dispatchers.IO) {
        val file = exportFile(fileNamePrefix, "pdf")
        legalReportPdfBuilder.buildToFile(file, reportTitle, coverFields, sections, siteSketchFile, ministryLogoBytes)
        uriFor(file)
    }

    override suspend fun exportNotificationBatchAsWord(batch: NotificationBatch): Uri =
        exportNotificationBatchesAsWord(listOf(batch))

    override suspend fun exportNotificationBatchesAsWord(batches: List<NotificationBatch>): Uri = withContext(Dispatchers.IO) {
        val file = exportFile("إخطارات", "docx")
        val dateFormat = DateTimeFormatter.ofPattern("d-M-yyyy")
        val cards = batches.flatMap { batch ->
            val issueDate = batch.createdAt.takeIf { it > 0L }
                ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                ?: java.time.LocalDate.now()
            batch.recipients.map { recipient ->
                OfficialNotificationCard(
                    ministryOrSector = batch.ministryOrSector,
                    department = batch.department,
                    expertJobTitle = batch.expertJobTitle,
                    expertName = batch.expertName,
                    officeAddress = batch.officeAddress,
                    attendancePhrase = batch.attendancePhrase,
                    attendanceLocation = batch.appointmentLocation.ifBlank { batch.officeAddress },
                    recipientName = recipient.fullName,
                    recipientAddress = recipient.partyAddress,
                    dayName = arabicDay(batch.appointmentDate.dayOfWeek),
                    appointmentDate = batch.appointmentDate.format(dateFormat),
                    appointmentTime = batch.appointmentTime,
                    issueDate = issueDate.format(dateFormat),
                    caseReference = "الدعوى رقم ${recipient.caseNo} لسنة ${recipient.caseYear} ${recipient.court}",
                    plaintiffs = recipient.plaintiffsSummary,
                    defendants = recipient.defendantsSummary,
                    subjectOfCase = recipient.subjectOfCase,
                    preliminaryJudgmentDate = recipient.preliminaryJudgmentDate?.format(dateFormat).orEmpty(),
                    isStateLawsuitsAuthorityNotice = recipient.isAuthorityNotice,
                    incomingNo = recipient.incomingNo,
                    requestedDocuments = batch.requestedDocuments
                )
            }
        }
        officialNotificationDocxBuilder.buildToFile(file, cards)
        uriFor(file)
    }

    override suspend fun exportNotificationSirkisAsWord(batch: NotificationBatch): Uri =
        exportNotificationSirkisBatchesAsWord(listOf(batch))

    override suspend fun exportNotificationSirkisBatchesAsWord(batches: List<NotificationBatch>): Uri = withContext(Dispatchers.IO) {
        val file = exportFile("سركي_إخطارات", "docx")
        val dateFormat = DateTimeFormatter.ofPattern("d-M-yyyy")
        val rowsWithoutIndex = batches.flatMap { batch ->
            val issueDate = batch.createdAt.takeIf { it > 0L }
                ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                ?: java.time.LocalDate.now()
            batch.recipients.map { recipient ->
                listOf(
                    issueDate.format(dateFormat),
                    "${recipient.caseNo} لسنة ${recipient.caseYear} ${recipient.court}",
                    batch.appointmentDate.format(dateFormat),
                    recipient.fullName,
                    "",
                    ""
                )
            }
        }
        val rows = rowsWithoutIndex.mapIndexed { index, row -> listOf((index + 1).toString()) + row }
        val first = batches.first()
        officialSirkisDocxBuilder.buildToFile(file, first.expertName, rows, first.ministryOrSector, first.department)
        uriFor(file)
    }

    override suspend fun exportNotificationEnvelopesAsWord(batch: NotificationBatch): Uri =
        exportNotificationEnvelopeBatchesAsWord(listOf(batch))

    override suspend fun exportNotificationEnvelopeBatchesAsWord(batches: List<NotificationBatch>): Uri = withContext(Dispatchers.IO) {
        val file = exportFile("أظرف_الإخطارات", "docx")
        officialEnvelopeDocxBuilder.buildToFile(
            file,
            batches.flatMap { batch -> batch.recipients.map { it.fullName to it.partyAddress } }
        )
        uriFor(file)
    }

    override suspend fun exportNotificationMailCoverAsWord(batch: NotificationBatch): Uri =
        exportNotificationMailCoverBatchesAsWord(listOf(batch))

    override suspend fun exportNotificationMailCoverBatchesAsWord(batches: List<NotificationBatch>): Uri = withContext(Dispatchers.IO) {
        val file = exportFile("حافظة_بريد", "docx")
        val rowsWithoutIndex = batches.flatMap { batch ->
            batch.recipients.map { recipient ->
                listOf(
                    recipient.fullName,
                    recipient.partyAddress,
                    "${recipient.caseNo} لسنة ${recipient.caseYear}",
                    recipient.court
                )
            }
        }
        val rows = rowsWithoutIndex.mapIndexed { index, row -> listOf((index + 1).toString()) + row }
        val first = batches.first()
        officialMailCoverDocxBuilder.buildToFile(
            outputFile = file,
            expertName = first.expertName,
            officeAddress = first.officeAddress,
            rows = rows
        )
        uriFor(file)
    }

    override suspend fun exportWorkMinutesAsWord(
        fileNamePrefix: String,
        ministry: String,
        sector: String,
        department: String,
        incomingNo: String,
        caseIntro: String,
        caseNo: String,
        caseYear: String,
        court: String,
        plaintiffs: String,
        defendants: String,
        entries: List<WorkMinutesEntry>
    ): Uri = withContext(Dispatchers.IO) {
        val file = exportFile(fileNamePrefix, "docx")
        workMinutesDocxBuilder.buildToFile(
            file, ministry, sector, department, incomingNo, caseIntro,
            caseNo, caseYear, court, plaintiffs, defendants, entries, ministryLogoBytes
        )
        uriFor(file)
    }

    private fun arabicDay(day: DayOfWeek): String = when (day) {
        DayOfWeek.SATURDAY -> "السبت"
        DayOfWeek.SUNDAY -> "الأحد"
        DayOfWeek.MONDAY -> "الاثنين"
        DayOfWeek.TUESDAY -> "الثلاثاء"
        DayOfWeek.WEDNESDAY -> "الأربعاء"
        DayOfWeek.THURSDAY -> "الخميس"
        DayOfWeek.FRIDAY -> "الجمعة"
    }

    private fun exportFile(prefix: String, extension: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "${prefix}_$stamp.$extension")
    }

    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
