package com.khabir.app.data.export

import java.io.ByteArrayOutputStream
import java.io.File
import android.graphics.BitmapFactory
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Word builder dedicated to Arabic expert reports and their real office structure. */
class LegalReportDocxBuilder {
    fun buildToFile(
        outputFile: File,
        reportTitle: String,
        coverFields: List<Pair<String, String>>,
        sections: List<Pair<String, String>>,
        siteSketchFile: File? = null,
        logoBytes: ByteArray? = null
    ): File {
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(build(reportTitle, coverFields, sections, siteSketchFile, logoBytes))
        return outputFile
    }

    fun build(
        reportTitle: String,
        coverFields: List<Pair<String, String>>,
        sections: List<Pair<String, String>>,
        siteSketchFile: File? = null,
        logoBytes: ByteArray? = null
    ): ByteArray {
        val fields = coverFields.toMap()
        val ministry = fields["الوزارة"] ?: "وزارة العدل"
        val sector = fields["القطاع"] ?: fields["جهة الانتداب"].orEmpty()
        val department = fields["الإدارة"] ?: fields["الإدارة/المكتب"].orEmpty()
        val expert = fields["الخبير"] ?: fields["الخبير المنتدب"].orEmpty()
        val caseNumber = fields["رقم الدعوى"].orEmpty()
        val court = fields["المحكمة"].orEmpty()
        // الصيغة الافتراضية المعتمدة في الغلاف هي «المرفوعة من»؛
        // مع استمرار قبول «المقامة من» من البيانات القديمة أو القوالب الأخرى.
        val plaintiffs = fields["المرفوعة من"] ?: fields["المقامة من"].orEmpty()
        val defendants = fields["ضد"].orEmpty()
        val incoming = fields["الوارد"].orEmpty()
        val caseIntro = fields["صيغة رقم القضية"].orEmpty().ifBlank { "فى الدعوى رقم" }
        val firstPartyLabel = fields["تسمية الطرف الأول"].orEmpty().ifBlank { "المرفوعــة من" }
        val secondPartyLabel = fields["تسمية الطرف الثاني"].orEmpty().ifBlank { "ضـــــد" }
        val compactCover = fields["نمط الغلاف"] == "مختصر"
        val runningHeader = fields["رأس التقرير"].orEmpty()

        val coverParagraph: (String, Boolean, Int) -> String = if (compactCover) {
            { text, bold, size -> compactCentered(text, bold, size) }
        } else {
            { text, bold, size -> centered(text, bold, size) }
        }

        val body = StringBuilder()
        var nextRelId = 2
        val headerRelId = if (runningHeader.isNotBlank()) "rId${nextRelId++}" else null
        val logoRelId = if (logoBytes != null) "rId${nextRelId++}" else null
        val sketchRelId = "rId${nextRelId}"

        if (logoRelId != null) {
            body.append(imageParagraph(logoRelId, 480_000, 447_000, "شعار وزارة العدل"))
        }
        body.append(coverParagraph(ministry, true, if (compactCover) 24 else 26))
        if (sector.isNotBlank()) body.append(coverParagraph(sector, true, if (compactCover) 22 else 24))
        if (department.isNotBlank()) body.append(coverParagraph(department, true, if (compactCover) 22 else 24))
        if (!compactCover) body.append(compactSpacer())
        body.append(coverParagraph(reportTitle, true, if (compactCover) 30 else 34))
        if (expert.isNotBlank()) body.append(coverParagraph("مقدم من $expert خبير وزارة العدل", true, if (compactCover) 22 else 24))
        if (caseNumber.isNotBlank()) body.append(coverParagraph("$caseIntro $caseNumber${if (court.isBlank()) "" else " $court"}", true, if (compactCover) 23 else 25))
        if (plaintiffs.isNotBlank()) {
            body.append(coverParagraph(firstPartyLabel, true, if (compactCover) 21 else 23))
            body.append(coverParagraph(plaintiffs, true, if (compactCover) 22 else 24))
        }
        if (defendants.isNotBlank()) {
            body.append(coverParagraph(secondPartyLabel, true, if (compactCover) 21 else 23))
            body.append(coverParagraph(defendants, true, if (compactCover) 22 else 24))
        }
        if (incoming.isNotBlank()) {
            val incomingParts = incoming.split(" لسنة ", limit = 2)
            body.append(coverParagraph("وارد ${incomingParts[0]}", true, if (compactCover) 20 else 22))
            if (incomingParts.size == 2) body.append(coverParagraph("لسنة ${incomingParts[1]}", true, if (compactCover) 20 else 22))
        }
        body.append(if (compactCover) tinySpacer() else compactSpacer())

        var sketchInserted = false
        val sketchBytes = siteSketchFile?.takeIf { it.isFile }?.readBytes()?.takeIf { it.isNotEmpty() }
        val sketchSize = sketchBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        fun appendSketch() {
            if (sketchBytes == null || sketchSize == null || sketchInserted) return
            val maxWidth = 5_900_000L
            val maxHeight = 6_800_000L
            val requestedCx = sketchSize.width * 9_525L
            val requestedCy = sketchSize.height * 9_525L
            val scale = minOf(maxWidth.toDouble() / requestedCx, maxHeight.toDouble() / requestedCy, 1.0)
            body.append(imageParagraph(sketchRelId, (requestedCx * scale).toLong().coerceAtLeast(1), (requestedCy * scale).toLong().coerceAtLeast(1), "مخطط الموقع"))
            body.append(compactSpacer())
            sketchInserted = true
        }
        sections.forEach { (sectionTitle, content) ->
            body.append(heading(sectionTitle))
            content.lines().forEach { line ->
                if (line.isBlank()) body.append(compactSpacer()) else body.append(normal(line))
            }
            body.append(compactSpacer())
            if (sectionTitle.contains("معاين")) appendSketch()
        }

        val headerReference = if (runningHeader.isNotBlank()) "<w:headerReference w:type=\"default\" r:id=\"rId2\"/>" else ""
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>$body<w:sectPr>$headerReference<w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="720" w:right="850" w:bottom="720" w:left="850"/><w:bidi/></w:sectPr></w:body></w:document>"""

        val parts = mutableListOf(
            "[Content_Types].xml" to contentTypes(hasHeader = runningHeader.isNotBlank(), hasSketch = sketchBytes != null, hasLogo = logoBytes != null).toByteArray(),
            "_rels/.rels" to RELS.toByteArray(),
            "word/_rels/document.xml.rels" to documentRelationships(hasHeader = runningHeader.isNotBlank(), hasSketch = sketchBytes != null, hasLogo = logoBytes != null).toByteArray(),
            "word/styles.xml" to STYLES.toByteArray(),
            "word/document.xml" to document.toByteArray()
        )
        if (runningHeader.isNotBlank()) {
            parts += "word/header1.xml" to headerXml(runningHeader).toByteArray()
        }
        if (logoBytes != null) parts += "word/media/logo.png" to logoBytes
        if (sketchBytes != null) parts += "word/media/site_sketch.png" to sketchBytes
        return zip(parts)
    }

    private fun centered(text: String, bold: Boolean, size: Int): String = paragraph(
        text = text,
        align = "center",
        bold = bold,
        size = size,
        after = 20,
        line = 300,
        keepNext = true
    )

    private fun compactCentered(text: String, bold: Boolean, size: Int): String = paragraph(
        text = text,
        align = "center",
        bold = bold,
        size = size,
        after = 4,
        line = 250,
        keepNext = true
    )

    private fun heading(text: String): String = paragraph(
        text = text,
        align = "start",
        bold = true,
        size = 25,
        after = 15,
        line = 320,
        keepNext = true
    )

    private fun normal(text: String): String = paragraph(
        text = text,
        align = "both",
        bold = false,
        size = 24,
        after = 8,
        line = 360,
        keepNext = false,
        firstLineIndent = 300
    )

    private fun compactSpacer(): String =
        "<w:p><w:pPr><w:bidi/><w:spacing w:before=\"0\" w:after=\"20\"/></w:pPr></w:p>"

    private fun tinySpacer(): String =
        "<w:p><w:pPr><w:bidi/><w:spacing w:before=\"0\" w:after=\"4\"/></w:pPr></w:p>"

    private fun imageParagraph(relId: String, cx: Long, cy: Long, name: String = "صورة"): String =
        """<w:p><w:pPr><w:bidi/><w:jc w:val="center"/></w:pPr><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="1" name="${escape(name)}"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="0" name="${escape(name)}"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="$relId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"""

    private fun paragraph(
        text: String,
        align: String,
        bold: Boolean,
        size: Int,
        after: Int,
        line: Int,
        keepNext: Boolean = false,
        firstLineIndent: Int = 0
    ): String {
        val boldXml = if (bold) "<w:b/>" else ""
        val keepNextXml = if (keepNext) "<w:keepNext/>" else ""
        val indentXml = if (firstLineIndent > 0) "<w:ind w:firstLine=\"$firstLineIndent\"/>" else ""
        return "<w:p><w:pPr><w:bidi/><w:widowControl/>$keepNextXml<w:jc w:val=\"$align\"/>$indentXml<w:spacing w:before=\"0\" w:after=\"$after\" w:line=\"$line\" w:lineRule=\"auto\"/></w:pPr><w:r><w:rPr><w:rtl/><w:lang w:val=\"ar-EG\" w:bidi=\"ar-EG\"/><w:rFonts w:ascii=\"Traditional Arabic\" w:hAnsi=\"Traditional Arabic\" w:cs=\"Traditional Arabic\"/>$boldXml<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/></w:rPr><w:t xml:space=\"preserve\">${escape(text)}</w:t></w:r></w:p>"
    }

    private fun headerXml(text: String): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:pPr><w:bidi/><w:jc w:val="start"/><w:spacing w:after="0"/></w:pPr><w:r><w:rPr><w:rtl/><w:b/><w:lang w:val="ar-EG" w:bidi="ar-EG"/><w:rFonts w:ascii="Traditional Arabic" w:hAnsi="Traditional Arabic" w:cs="Traditional Arabic"/><w:sz w:val="20"/><w:szCs w:val="20"/></w:rPr><w:t xml:space="preserve">${escape(text)}</w:t></w:r></w:p></w:hdr>"""

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

    private fun contentTypes(hasHeader: Boolean, hasSketch: Boolean, hasLogo: Boolean): String {
        val headerOverride = if (hasHeader) "<Override PartName=\"/word/header1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml\"/>" else ""
        val imageDefault = if (hasSketch || hasLogo) "<Default Extension=\"png\" ContentType=\"image/png\"/>" else ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>$imageDefault<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>$headerOverride</Types>"""
    }

    private fun documentRelationships(hasHeader: Boolean, hasSketch: Boolean, hasLogo: Boolean): String {
        var nextId = 2
        val headerRel = if (hasHeader) {
            "<Relationship Id=\"rId${nextId++}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/header\" Target=\"header1.xml\"/>"
        } else ""
        val logoRel = if (hasLogo) {
            "<Relationship Id=\"rId${nextId++}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/logo.png\"/>"
        } else ""
        val sketchRel = if (hasSketch) {
            "<Relationship Id=\"rId${nextId}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/site_sketch.png\"/>"
        } else ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>$headerRel$logoRel$sketchRel</Relationships>"""
    }

    companion object {
        private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Traditional Arabic" w:hAnsi="Traditional Arabic" w:cs="Traditional Arabic"/><w:lang w:val="ar-EG" w:bidi="ar-EG"/><w:sz w:val="24"/><w:szCs w:val="24"/><w:rtl/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:bidi/><w:jc w:val="start"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:pPr><w:bidi/><w:jc w:val="start"/></w:pPr><w:rPr><w:rtl/><w:lang w:val="ar-EG" w:bidi="ar-EG"/></w:rPr></w:style></w:styles>"""
    }
}
