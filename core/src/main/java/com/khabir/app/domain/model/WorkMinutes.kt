package com.khabir.app.domain.model

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Base64

/** محضر أعمال واحد ضمن مجموعة محاضر الأعمال الخاصة بقضية. */
data class WorkMinutesEntry(
    val number: Int,
    val openingDate: LocalDate? = null,
    val openingTime: String = "",
    val bodyText: String = "",
    val closingTime: String = "",
    val expertName: String = "",
    /** لو هذا المحضر حدد موعد جلسة/مباشرة جاية، تاريخها هنا — يُستخدم تلقائيًا كتاريخ افتتاح المحضر التالي. */
    val scheduledFollowUpDate: LocalDate? = null,
    /** وقت الموعد القادم بصيغة حرة مثل 9 صباحًا أو 09:00. */
    val scheduledFollowUpTime: String = "",
    /** مكان الموعد القادم: المكتب أو المحكمة أو أي مكان آخر يكتبه المستخدم. */
    val scheduledFollowUpLocation: String = ""
)

/**
 * مجموعة محاضر الأعمال — إما مرتبطة بقضية مسجلة (caseId) أو مستقلة ببيانات
 * يدوية (caseNo/caseYear/court/الأطراف)، بنفس فكرة استقلالية [Report].
 */
data class WorkMinutesRecord(
    val id: Long = 0L,
    val caseId: Long? = null,
    val caseNo: String = "",
    val caseYear: String = "",
    val court: String = "",
    val plaintiffsSummary: String = "",
    val defendantsSummary: String = "",
    val entries: List<WorkMinutesEntry> = emptyList(),
    val copiesCount: Int = 1,
    val updatedAt: Long = 0L
) {
    val isIndependent: Boolean get() = caseId == null
}

/**
 * ترميز/فك ترميز قائمة محاضر الأعمال إلى نص واحد يُخزَّن في عمود واحد —
 * بنفس أسلوب [ReportTemplateCodec]: كل حقل نصي base64، الحقول بينها tab،
 * والمحاضر بينها سطر جديد.
 *
 * أُضيف الوقت والمكان في نهاية السطر فقط للحفاظ على التوافق مع البيانات القديمة:
 * السجلات القديمة التي تحتوي 7 أعمدة تظل قابلة للقراءة كما هي.
 */
object WorkMinutesCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(entries: List<WorkMinutesEntry>): String = entries.joinToString("\n") { entry ->
        listOf(
            entry.number.toString(),
            entry.openingDate?.toEpochDay()?.toString().orEmpty(),
            encodeText(entry.openingTime),
            encodeText(entry.bodyText),
            encodeText(entry.closingTime),
            encodeText(entry.expertName),
            entry.scheduledFollowUpDate?.toEpochDay()?.toString().orEmpty(),
            encodeText(entry.scheduledFollowUpTime),
            encodeText(entry.scheduledFollowUpLocation)
        ).joinToString("\t")
    }

    fun decode(spec: String): List<WorkMinutesEntry> = runCatching {
        spec.split("\n").filter(String::isNotBlank).map { line ->
            val parts = line.split("\t")
            WorkMinutesEntry(
                number = parts.getOrNull(0)?.toIntOrNull() ?: 0,
                openingDate = parts.getOrNull(1)?.takeIf(String::isNotBlank)?.toLongOrNull()?.let(LocalDate::ofEpochDay),
                openingTime = parts.getOrNull(2)?.let(::decodeText).orEmpty(),
                bodyText = parts.getOrNull(3)?.let(::decodeText).orEmpty(),
                closingTime = parts.getOrNull(4)?.let(::decodeText).orEmpty(),
                expertName = parts.getOrNull(5)?.let(::decodeText).orEmpty(),
                scheduledFollowUpDate = parts.getOrNull(6)?.takeIf(String::isNotBlank)?.toLongOrNull()?.let(LocalDate::ofEpochDay),
                scheduledFollowUpTime = parts.getOrNull(7)?.let(::decodeText).orEmpty(),
                scheduledFollowUpLocation = parts.getOrNull(8)?.let(::decodeText).orEmpty()
            )
        }
    }.getOrElse { emptyList() }

    private fun encodeText(value: String): String = encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun decodeText(value: String): String = if (value.isBlank()) "" else String(decoder.decode(value), StandardCharsets.UTF_8)
}

/** نصوص جاهزة شائعة لمتن محاضر الأعمال — لتسريع الكتابة، قابلة للتعديل دائمًا. */
object WorkMinutesPhrases {
    const val RECEIVED_FILE_DEFERRED =
        "لإثبات استلامنا ملف الدعوى من سكرتارية المكتب وقد قمنا بالاطلاع عليه وما به من اوراق وعليه رأينا ارجاء مباشرة المأمورية لحين ورود دورها بالسجلات"
    const val RETURNED_FOR_REASSIGNMENT =
        "لإثبات اعادة ملف الدعوى الى سكرتارية المكتب لاعادة توزيعه مرة اخرى بمعرفة الادارة على احد السادة الخبراء لتسوية الارصدة"
    const val RECEIVED_AFTER_REASSIGNMENT_DEFERRED =
        "لإثبات استلامنا ملف الدعوى من سكرتارية المكتب بعد اعادة توزيعه علينا بمعرفة الادارة وقد قمنا بالاطلاع عليه وما به من اوراق وعليه رأينا ارجاء مباشرة المأمورية لحين ورود دورها بالسجلات"
    const val SCHEDULING_TEMPLATE =
        "لإثبات تحديد يوم %s الساعة %s بالمكتب لبدء مباشرة المأمورية وأخطرنا طرفي التداعي بذلك الموعد بموجب إخطارات مسجلة"

    val quickPhrases = listOf(
        "استلام الملف (محضر 1)" to RECEIVED_FILE_DEFERRED,
        "إعادة توزيع الملف (محضر 2)" to RETURNED_FOR_REASSIGNMENT,
        "استلام بعد إعادة التوزيع (محضر 3)" to RECEIVED_AFTER_REASSIGNMENT_DEFERRED
    )
}
