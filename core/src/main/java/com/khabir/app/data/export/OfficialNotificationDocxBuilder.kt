package com.khabir.app.data.export

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class OfficialNotificationCard(
    val ministryOrSector: String,
    val department: String,
    val expertJobTitle: String,
    val expertName: String,
    val officeAddress: String,
    val attendancePhrase: String,
    val attendanceLocation: String,
    val recipientName: String,
    val recipientAddress: String,
    val dayName: String,
    val appointmentDate: String,
    val appointmentTime: String,
    val issueDate: String,
    val caseReference: String,
    val plaintiffs: String,
    val defendants: String,
    val subjectOfCase: String = "",
    val preliminaryJudgmentDate: String = "",
    val isStateLawsuitsAuthorityNotice: Boolean = false,
    val incomingNo: String = "",
    val requestedDocuments: String = ""
)

class OfficialNotificationDocxBuilder {
    fun buildToFile(outputFile: File, cards: List<OfficialNotificationCard>): File {
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(build(cards))
        return outputFile
    }

    fun build(cards: List<OfficialNotificationCard>): ByteArray {
        val body = StringBuilder()
        // Keep every original notice (minister/director/administrative authority) intact.
        // State Lawsuits Authority is an additional notice, never a replacement.
        val ordinary = cards.filterNot { it.isStateLawsuitsAuthorityNotice }
        ordinary.chunked(4).forEachIndexed { pageIndex, pageCards ->
            val padded = pageCards + List(4 - pageCards.size) { null }
            body.append(sheet(padded, pageIndex * 4))
            if (pageIndex < (ordinary.size - 1) / 4 || cards.any { it.isStateLawsuitsAuthorityNotice }) body.append(pageBreak())
        }
        val authorityCards = cards.filter { it.isStateLawsuitsAuthorityNotice }
        authorityCards.forEachIndexed { index, card ->
            body.append(stateLawsuitsAuthoritySheet(card))
            if (index < authorityCards.lastIndex) body.append(pageBreak())
        }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="16838" w:h="11906" w:orient="landscape"/><w:pgMar w:top="240" w:right="240" w:bottom="240" w:left="240"/><w:bidi/></w:sectPr></w:body></w:document>"""
        return zip(document)
    }

    private fun sheet(cards: List<OfficialNotificationCard?>, startIndex: Int): String =
        "<w:tbl><w:tblPr><w:tblW w:w=\"16320\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/><w:bidiVisual/><w:jc w:val=\"right\"/><w:tblBorders><w:top w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:left w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:bottom w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:right w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:insideH w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:insideV w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/></w:tblBorders></w:tblPr><w:tblGrid><w:gridCol w:w=\"8160\"/><w:gridCol w:w=\"8160\"/></w:tblGrid>${row(cards[0], cards[1], startIndex + 1, startIndex + 2)}${row(cards[2], cards[3], startIndex + 3, startIndex + 4)}</w:tbl>"

    private fun row(left: OfficialNotificationCard?, right: OfficialNotificationCard?, leftNumber: Int, rightNumber: Int): String =
        "<w:tr><w:trPr><w:trHeight w:val=\"5600\" w:hRule=\"atLeast\"/><w:cantSplit/></w:trPr>${cell(left, leftNumber)}${cell(right, rightNumber)}</w:tr>"

    private fun cell(card: OfficialNotificationCard?, number: Int): String {
        if (card == null) return "<w:tc><w:tcPr><w:tcW w:w=\"8160\" w:type=\"dxa\"/><w:vAlign w:val=\"top\"/></w:tcPr><w:p><w:pPr><w:bidi/><w:jc w:val=\"right\"/></w:pPr></w:p></w:tc>"
        val body = buildString {
            append(cardHeader(card))
            append(line("السيد/ ${card.recipientName}", bold = true, size = 22))
            append(line("العنوان/ ${card.recipientAddress}", size = 21))
            append(line(card.attendancePhrase, size = 21))
            append(line(card.attendanceLocation, size = 20))
            append(line("يوم ${card.dayName} الموافق ${card.appointmentDate} الساعة ${card.appointmentTime}", size = 21))
            append(line("وذلك لأجل المناقشة في ${card.caseReference}", bold = true, size = 21))
            if (card.plaintiffs.isNotBlank()) append(line("المرفوعة من/ ${card.plaintiffs}", size = 20))
            if (card.defendants.isNotBlank()) append(line("ضد/ ${card.defendants}", size = 20))
            if (card.requestedDocuments.isNotBlank()) append(line("وتقديم المستندات: ${card.requestedDocuments}", size = 19))
            append(line("علماً بأنه في حالة عدم حضوركم سيتم مباشرة المأمورية غيابياً طبقاً للقانون.", size = 19))
            append(twoColumnFooter(card.issueDate))
            append(line(number.toString(), size = 14))
        }
        return "<w:tc><w:tcPr><w:tcW w:w=\"8160\" w:type=\"dxa\"/><w:vAlign w:val=\"top\"/><w:tcMar><w:top w:w=\"70\" w:type=\"dxa\"/><w:left w:w=\"100\" w:type=\"dxa\"/><w:bottom w:w=\"70\" w:type=\"dxa\"/><w:right w:w=\"100\" w:type=\"dxa\"/></w:tcMar></w:tcPr>$body</w:tc>"
    }

    /** Government parties receive a formal notice occupying two normal card heights. */
    private fun stateLawsuitsAuthoritySheet(card: OfficialNotificationCard): String {
        val subject = card.subjectOfCase.trim().ifBlank { "موضوع الدعوى يُراجع من بيانات القضية" }
        val content = buildString {
            append(cardHeader(card))
            val metadata = buildString {
                if (card.incomingNo.isNotBlank()) append(line("رقم الوارد/ " + card.incomingNo, size = 21))
                if (card.preliminaryJudgmentDate.isNotBlank()) append(line("تاريخ الحكم التمهيدي/ " + card.preliminaryJudgmentDate, size = 21))
            }
            if (metadata.isNotBlank()) { append(nestedTwoColumns(metadata, "<w:p/>")); append("<w:p/>") }
            append(line("السادة/ هيئة قضايا الدولة", bold = true, size = 27))
            append(line("العنوان/ " + card.recipientAddress.ifBlank { "يُحدد العنوان قبل التصدير" }, bold = true, size = 23))
            append(line("تحية طيبة وبعد،", size = 22))
            append(line("نحيط سيادتكم علماً بأن " + card.caseReference + "، وموضوعها " + subject + ".", size = 22))
            if (card.plaintiffs.isNotBlank()) append(line("وهي مرفوعة من/ " + card.plaintiffs, size = 21))
            if (card.defendants.isNotBlank()) append(line("ضد/ " + card.defendants, size = 21))
            append(line("وقد حُدد يوم " + card.dayName + " الموافق " + card.appointmentDate + " الساعة " + card.appointmentTime + " لمباشرة المأمورية.", bold = true, size = 22))
            append(line("وتم إخطاركم لاتخاذ اللازم.", size = 22))
            if (card.attendanceLocation.isNotBlank()) append(line("مكان مباشرة المأمورية/ " + card.attendanceLocation, size = 21))
            append(twoColumnFooter(card.issueDate))
            append("<w:p/>")
        }
        return "<w:tbl><w:tblPr><w:tblW w:w=\"16320\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/><w:bidiVisual/><w:tblBorders><w:top w:val=\"single\" w:sz=\"8\"/><w:left w:val=\"single\" w:sz=\"8\"/><w:bottom w:val=\"single\" w:sz=\"8\"/><w:right w:val=\"single\" w:sz=\"8\"/></w:tblBorders></w:tblPr><w:tblGrid><w:gridCol w:w=\"8160\"/><w:gridCol w:w=\"8160\"/></w:tblGrid><w:tr><w:trPr><w:trHeight w:val=\"11200\" w:hRule=\"atLeast\"/></w:trPr><w:tc><w:tcPr><w:tcW w:w=\"8160\" w:type=\"dxa\"/></w:tcPr>" + content + "</w:tc><w:tc><w:tcPr><w:tcW w:w=\"8160\" w:type=\"dxa\"/></w:tcPr><w:p/></w:tc></w:tr></w:tbl>"
    }

    private fun cardHeader(card: OfficialNotificationCard): String {
        val authority = buildString {
            append(center(card.ministryOrSector.ifBlank { "وزارة العدل" }, bold = true, size = 22))
            append(center("قطاع الخبراء", bold = true, size = 21))
            append(center(card.department.ifBlank { "إدارة خبراء أسوان" }, bold = true, size = 21, underline = true))
        }
        val expert = buildString {
            append(center("الخبير المحال إليه المأمورية", bold = true, size = 19))
            append(center("الخبير/ ${card.expertName}", bold = true, size = 20))
        }
        return nestedTwoColumns(authority, expert)
    }

    private fun twoColumnFooter(issueDate: String): String = nestedTwoColumns(
        line("تحريراً في ${arabicDigits(issueDate)}", size = 19),
        center("توقيع الخبير\n......................", bold = true, size = 19)
    )

    // <w:bidiVisual/> is required here: without it, Word lays table columns out
    // left-to-right regardless of the document's bidi setting, so the first <w:tc>
    // in the markup always lands in the physical left column. That silently swapped
    // this header/footer split (authority text ended up on the visual left instead
    // of the right, expert info on the right instead of the left) even though the
    // "right"/"left" parameter names below were already correct. Every other table
    // in this project (Sirkis/report/mail-cover) sets bidiVisual; this one didn't.
    private fun nestedTwoColumns(right: String, left: String): String =
        "<w:tbl><w:tblPr><w:tblW w:w=\"7850\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/><w:bidiVisual/><w:jc w:val=\"right\"/><w:tblBorders><w:top w:val=\"nil\"/><w:left w:val=\"nil\"/><w:bottom w:val=\"nil\"/><w:right w:val=\"nil\"/><w:insideH w:val=\"nil\"/><w:insideV w:val=\"nil\"/></w:tblBorders></w:tblPr><w:tblGrid><w:gridCol w:w=\"3925\"/><w:gridCol w:w=\"3925\"/></w:tblGrid><w:tr><w:trPr><w:cantSplit/></w:trPr>${nestedCell(right)}${nestedCell(left)}</w:tr></w:tbl>"

    private fun nestedCell(content: String): String =
        "<w:tc><w:tcPr><w:tcW w:w=\"3925\" w:type=\"dxa\"/><w:vAlign w:val=\"top\"/><w:tcMar><w:top w:w=\"0\" w:type=\"dxa\"/><w:left w:w=\"20\" w:type=\"dxa\"/><w:bottom w:w=\"0\" w:type=\"dxa\"/><w:right w:w=\"20\" w:type=\"dxa\"/></w:tcMar></w:tcPr>$content</w:tc>"

    private fun center(text: String, bold: Boolean = false, size: Int = 18, underline: Boolean = false): String = paragraph(text, "center", bold, size, underline)
    // Use direction-relative start: legacy right is mirrored by some RTL readers.
    private fun line(text: String, bold: Boolean = false, size: Int = 18, underline: Boolean = false): String = paragraph(text, "start", bold, size, underline)

    private fun paragraph(text: String, align: String, bold: Boolean, size: Int, underline: Boolean): String {
        val displaySize = size + 4
        val b = if (bold) "<w:b/><w:bCs/>" else ""
        val u = if (underline) "<w:u w:val=\"single\"/>" else ""
        return "<w:p><w:pPr><w:bidi/><w:jc w:val=\"$align\"/><w:spacing w:before=\"0\" w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/></w:pPr><w:r><w:rPr><w:rtl/><w:lang w:val=\"ar-EG\" w:bidi=\"ar-EG\"/><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:cs=\"Arial\"/>$b$u<w:sz w:val=\"$displaySize\"/><w:szCs w:val=\"$displaySize\"/></w:rPr><w:t xml:space=\"preserve\">${escape(arabicDigits(text))}</w:t></w:r></w:p>"
    }

    private fun arabicDigits(text: String): String = buildString(text.length) {
        text.forEach { append(if (it in '0'..'9') "٠١٢٣٤٥٦٧٨٩"[it - '0'] else it) }
    }

    private fun pageBreak() = "<w:p><w:pPr><w:bidi/><w:spacing w:before=\"0\" w:after=\"0\"/></w:pPr><w:r><w:rPr><w:rtl/></w:rPr><w:br w:type=\"page\"/></w:r></w:p>"

    private fun escape(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun zip(documentXml: String): ByteArray {
        val parts = listOf(
            "[Content_Types].xml" to CONTENT_TYPES,
            "_rels/.rels" to RELS,
            "word/_rels/document.xml.rels" to DOC_RELS,
            "word/styles.xml" to STYLES,
            "word/document.xml" to NotificationNumberDirection.apply(documentXml)
        )
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            parts.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    companion object {
        private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>"""
        private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        private const val DOC_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
        private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:cs="Arial"/><w:lang w:val="ar-EG" w:bidi="ar-EG"/><w:sz w:val="22"/><w:szCs w:val="22"/><w:rtl/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:bidi/><w:jc w:val="start"/><w:spacing w:before="0" w:after="0"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:pPr><w:bidi/><w:jc w:val="start"/><w:spacing w:before="0" w:after="0"/></w:pPr><w:rPr><w:rtl/><w:lang w:val="ar-EG" w:bidi="ar-EG"/></w:rPr></w:style></w:styles>"""
    }
}
