package com.khabir.app.domain.repository

import android.net.Uri
import com.khabir.app.domain.model.NotificationBatch
import com.khabir.app.domain.model.WorkMinutesEntry
import java.io.File

interface DocumentExportRepository {
    suspend fun exportTitledTableAsWord(
        fileNamePrefix: String,
        title: String,
        subtitle: String?,
        columns: List<String>,
        rows: List<List<String>>
    ): Uri

    suspend fun exportReportAsWord(
        fileNamePrefix: String,
        reportTitle: String,
        coverFields: List<Pair<String, String>>,
        sections: List<Pair<String, String>>,
        siteSketchFile: File? = null
    ): Uri

    suspend fun exportReportAsPdf(
        fileNamePrefix: String,
        reportTitle: String,
        coverFields: List<Pair<String, String>>,
        sections: List<Pair<String, String>>,
        siteSketchFile: File? = null
    ): Uri

    suspend fun exportNotificationBatchAsWord(batch: NotificationBatch): Uri
    suspend fun exportNotificationSirkisAsWord(batch: NotificationBatch): Uri
    suspend fun exportNotificationEnvelopesAsWord(batch: NotificationBatch): Uri
    suspend fun exportNotificationMailCoverAsWord(batch: NotificationBatch): Uri
    suspend fun exportNotificationBatchesAsWord(batches: List<NotificationBatch>): Uri
    suspend fun exportNotificationSirkisBatchesAsWord(batches: List<NotificationBatch>): Uri
    suspend fun exportNotificationEnvelopeBatchesAsWord(batches: List<NotificationBatch>): Uri
    suspend fun exportNotificationMailCoverBatchesAsWord(batches: List<NotificationBatch>): Uri

    suspend fun exportWorkMinutesAsWord(
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
    ): Uri
}
