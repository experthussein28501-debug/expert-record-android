package com.khabir.app.data.export

import com.khabir.app.domain.model.WorkMinutesEntry
import com.khabir.app.domain.model.toArabicIndicDigits
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Word builder لمجموعة محاضر الأعمال — بنفس الهيكل الرسمي المتبع فعليًا
 * (شعار الوزارة، قطاع الخبراء، إدارة الخبراء، رقم الوارد، بيانات الدعوى،
 * ثم محضر أعمال مرقّم لكل حدث، كل محضر يبدأ في صفحة جديدة).
 */
class WorkMinutesDocxBuilder {
    private val dateFormat = DateTimeFormatter.ofPattern("d/M/yyyy")

    fun buildToFile(
        outputFile: File,
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
        entries: List<WorkMinutesEntry>,
        logoBytes: ByteArray?
    ): File {
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(
            build(ministry, sector, department, incomingNo, caseIntro, caseNo, caseYear, court, plaintiffs, defendants, entries, logoBytes)
        )
        return outputFile
    }

    fun build(
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
        entries: List<WorkMinutesEntry>,
        logoBytes: ByteArray?
    ): ByteArray {
        val body = StringBuilder()
        body.append(headerBlock(ministry, sector, department, incomingNo, logoBytes != null))
        body.append(compactSpacer())
        body.append(centered("مجموعة محاضر اعمال", bold = true, size = 26))
        body.append(centered("$caseIntro $caseNo لسنة $caseYear $court", bold = true, size = 24))
        if (plaintiffs.isNotBlank()) body.append(centered("المرفوعة من / $plaintiffs", bold = true, size = 22))
        if (defendants.isNotBlank()) body.append(centered("ضـــد/ $defendants", bold = true, size = 22))

        entries.forEachIndexed { index, entry ->
            if (index > 0) body.append(pageBreak())
            body.append(compactSpacer())
            body.append(underlinedCentered("محضر اعمال رقم (${entry.number})"))
            val openingDateText = entry.openingDate?.format(dateFormat).orEmpty()
            val openingLine = buildString {
                append("فتح هذا المحضر اليوم")
                if (openingDateText.isNotBlank()) append(" $openingDateText")
                if (entry.openingTime.isNotBlank()) append(" الساعة ${entry.openingTime}")
                append(" بالمكتب")
            }
            body.append(normal(openingLine))
            if (entry.bodyText.isNotBlank()) {
                entry.bodyText.replace("\r\n", "\n").lines().forEach { line ->
                    if (line.isBlank()) {
                        body.append(compactSpacer())
                    } else {
                        val qa = parseQuestionAnswerLine(line)
                        if (qa != null) body.append(questionAnswerParagraph(qa.first, qa.second)) else body.append(normal(line))
                    }
                }
            }
            entry.scheduledFollowUpDate?.let { followUp ->
                val followUpLine = buildString {
                    append("وحددنا يوم ${followUp.format(dateFormat)}")
                    if (entry.scheduledFollowUpTime.isNotBlank()) append(" الساعة ${entry.scheduledFollowUpTime}")
                    if (entry.scheduledFollowUpLocation.isNotBlank()) append(" ${entry.scheduledFollowUpLocation}")
                    append(" موعدًا لمتابعة مباشرة المأمورية.")
                }
                body.append(normal(followUpLine))
            }
            if (entry.closingTime.isNotBlank()) {
                body.append(normal("واقفل المحضر على ذلك فى تاريخه الساعة ${entry.closingTime} بالمكتب"))
            }
            if (entry.expertName.isNotBlank()) {
                body.append(compactSpacer())
                body.append(endAligned("الخبير/ ${entry.expertName}", bold = true))
            }
        }

        val imageRelId = "rId2"
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="850" w:right="1550" w:bottom="850" w:left="1550"/><w:pgBorders w:offsetFrom="text" w:display="allPages"><w:top w:val="nil"/><w:left w:val="single" w:sz="10" w:space="12" w:color="000000"/><w:bottom w:val="nil"/><w:right w:val="single" w:sz="10" w:space="12" w:color="000000"/></w:pgBorders><w:bidi/></w:sectPr></w:body></w:document>"""

        val parts = mutableListOf(
            "[Content_Types].xml" to contentTypes(hasLogo = logoBytes != null).toByteArray(),
            "_rels/.rels" to RELS.toByteArray(),
            "word/_rels/document.xml.rels" to documentRelationships(hasLogo = logoBytes != null).toByteArray(),
            "word/styles.xml" to STYLES.toByteArray(),
            "word/document.xml" to document.toByteArray()
        )
        if (logoBytes != null) parts += "word/media/logo.png" to logoBytes
        return zip(parts)
    }

    private fun headerBlock(ministry: String, sector: String, department: String, incomingNo: String, hasLogo: Boolean): String {
        val sb = StringBuilder()
        if (hasLogo) sb.append(logoParagraph("rId2", 480_000, 447_000))
        sb.append(startAligned(ministry, bold = true, size = 26))
        if (sector.isNotBlank()) sb.append(startAligned(sector, bold = true, size = 24))
        if (department.isNotBlank()) sb.append(startAligned(department, bold = true, size = 24))
        if (incomingNo.isNotBlank()) sb.append(startAligned("وارد رقم $incomingNo", bold = true, size = 22))
        return sb.toString()
    }

    private fun logoParagraph(relId: String, cx: Long, cy: Long): String =
        """<w:p><w:pPr><w:bidi/><w:jc w:val="end"/></w:pPr><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="1" name="شعار وزارة العدل"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="0" name="شعار وزارة العدل"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="$relId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"""

    private fun centered(text: String, bold: Boolean, size: Int): String = paragraph(text, "center", bold, size, after = 20, line = 300, keepNext = true)

    private fun startAligned(text: String, bold: Boolean, size: Int = 24): String = paragraph(text, "start", bold, size, after = 10, line = 280, keepNext = true)

    private fun endAligned(text: String, bold: Boolean, size: Int = 24): String = paragraph(text, "end", bold, size, after = 10, line = 280, keepNext = true)

    private fun underlinedCentered(text: String): String {
        return "<w:p><w:pPr><w:bidi/><w:keepNext/><w:jc w:val=\"center\"/><w:spacing w:before=\"0\" w:after=\"15\" w:line=\"320\" w:lineRule=\"auto\"/></w:pPr><w:r><w:rPr><w:rtl/><w:b/><w:u w:val=\"single\"/><w:lang w:val=\"ar-EG\" w:bidi=\"ar-EG\"/><w:rFonts w:ascii=\"Traditional Arabic\" w:hAnsi=\"Traditional Arabic\" w:cs=\"Traditional Arabic\"/><w:sz w:val=\"26\"/><w:szCs w:val=\"26\"/></w:rPr><w:t xml:space=\"preserve\">${escape(text.toArabicIndicDigits())}</w:t></w:r></w:p>"
    }

    private fun normal(text: String): String = paragraph(text, "both", bold = true, size = 24, after = 8, line = 360, keepNext = false)

    private fun parseQuestionAnswerLine(text: String): Pair<String, String>? {
        val trimmed = text.trimStart()
        val punctuated = Regex("""^([سج])\s*([/:.\-–—])\s*(.*)$""").matchEntire(trimmed)
        if (punctuated != null) {
            val label = punctuated.groupValues[1]
            val body = punctuated.groupValues[3]
            return label to body
        }
        val spaced = Regex("""^([سج])\s+(.+)$""").matchEntire(trimmed)
        return spaced?.let { it.groupValues[1] to it.groupValues[2] }
    }

    private fun questionAnswerParagraph(label: String, text: String): String {
        val safeLabel = escape(label)
        val safeText = escape(text.toArabicIndicDigits())
        return "<w:p><w:pPr><w:bidi/><w:widowControl/><w:jc w:val=\"both\"/><w:ind w:right=\"0\" w:hanging=\"800\"/><w:tabs><w:tab w:val=\"right\" w:pos=\"0\"/></w:tabs><w:spacing w:before=\"0\" w:after=\"8\" w:line=\"360\" w:lineRule=\"auto\"/></w:pPr>" +
            "<w:r><w:rPr><w:rtl/><w:b/><w:lang w:val=\"ar-EG\" w:bidi=\"ar-EG\"/><w:rFonts w:ascii=\"Traditional Arabic\" w:hAnsi=\"Traditional Arabic\" w:cs=\"Traditional Arabic\"/><w:sz w:val=\"24\"/><w:szCs w:val=\"24\"/></w:rPr><w:t xml:space=\"preserve\">$safeLabel/ </w:t><w:tab/></w:r>" +
            "<w:r><w:rPr><w:rtl/><w:b/><w:lang w:val=\"ar-EG\" w:bidi=\"ar-EG\"/><w:rFonts w:ascii=\"Traditional Arabic\" w:hAnsi=\"Traditional Arabic\" w:cs=\"Traditional Arabic\"/><w:sz w:val=\"24\"/><w:szCs w:val=\"24\"/></w:rPr><w:t xml:space=\"preserve\">$safeText</w:t></w:r></w:p>"
    }

    private fun compactSpacer(): String = "<w:p><w:pPr><w:bidi/><w:spacing w:before=\"0\" w:after=\"20\"/></w:pPr></w:p>"

    private fun pageBreak(): String = "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>"

    private fun paragraph(text: String, align: String, bold: Boolean, size: Int, after: Int, line: Int, keepNext: Boolean = false): String {
        val boldXml = if (bold) "<w:b/>" else ""
        val keepNextXml = if (keepNext) "<w:keepNext/>" else ""
        return "<w:p><w:pPr><w:bidi/><w:widowControl/>$keepNextXml<w:jc w:val=\"$align\"/><w:spacing w:before=\"0\" w:after=\"$after\" w:line=\"$line\" w:lineRule=\"auto\"/></w:pPr><w:r><w:rPr><w:rtl/><w:lang w:val=\"ar-EG\" w:bidi=\"ar-EG\"/><w:rFonts w:ascii=\"Traditional Arabic\" w:hAnsi=\"Traditional Arabic\" w:cs=\"Traditional Arabic\"/>$boldXml<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/></w:rPr><w:t xml:space=\"preserve\">${escape(text.toArabicIndicDigits())}</w:t></w:r></w:p>"
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun zip(parts: List<Pair<String, ByteArray>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            parts.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun contentTypes(hasLogo: Boolean): String {
        val logoDefault = if (hasLogo) "<Default Extension=\"png\" ContentType=\"image/png\"/>" else ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>$logoDefault<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>"""
    }

    private fun documentRelationships(hasLogo: Boolean): String {
        val logoRel = if (hasLogo) "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/logo.png\"/>" else ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>$logoRel</Relationships>"""
    }

    companion object {
        private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Traditional Arabic" w:hAnsi="Traditional Arabic" w:cs="Traditional Arabic"/><w:lang w:val="ar-EG" w:bidi="ar-EG"/><w:sz w:val="24"/><w:szCs w:val="24"/><w:rtl/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:bidi/><w:jc w:val="start"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:pPr><w:bidi/><w:jc w:val="start"/></w:pPr><w:rPr><w:rtl/><w:lang w:val="ar-EG" w:bidi="ar-EG"/></w:rPr></w:style></w:styles>"""
    }
}
