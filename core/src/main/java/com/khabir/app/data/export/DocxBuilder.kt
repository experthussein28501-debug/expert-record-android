package com.khabir.app.data.export

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxBuilder {
    fun buildTitledTable(title: String, subtitle: String?, columns: List<String>, rows: List<List<String>>): ByteArray {
        val documentXml = buildDocumentXml(title, subtitle, columns, rows, landscape = true)
        return standardDocx(documentXml)
    }

    fun buildPortraitTitledTable(title: String, subtitle: String?, columns: List<String>, rows: List<List<String>>): ByteArray {
        val documentXml = buildDocumentXml(title, subtitle, columns, rows, landscape = false)
        return standardDocx(documentXml)
    }

    fun buildReport(reportTitle: String, coverFields: List<Pair<String, String>>, sections: List<Pair<String, String>>): ByteArray {
        val body = StringBuilder()
        body.append(paragraph(reportTitle, "Title"))
        body.append(EMPTY_PARAGRAPH)
        coverFields.forEach { (label, value) -> body.append(paragraph("$label: $value")) }
        body.append(pageBreakParagraph())
        sections.forEach { (heading, content) ->
            body.append(paragraph(heading, "Heading"))
            content.split("\n").forEach { body.append(paragraph(it)) }
            body.append(EMPTY_PARAGRAPH)
        }
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/><w:bidi/></w:sectPr></w:body></w:document>"""
        return standardDocx(xml)
    }

    fun buildReportToFile(outputFile: File, reportTitle: String, coverFields: List<Pair<String, String>>, sections: List<Pair<String, String>>): File {
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(buildReport(reportTitle, coverFields, sections))
        return outputFile
    }

    fun buildNotificationSheets(cards: List<List<Pair<String, String>>>): ByteArray {
        val body = StringBuilder()
        cards.chunked(4).forEachIndexed { sheetIndex, sheetCards ->
            val padded = sheetCards + List(4 - sheetCards.size) { emptyList() }
            body.append(notificationSheet(padded))
            if (sheetIndex < (cards.size - 1) / 4) body.append(pageBreakParagraph())
        }
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="16838" w:h="11906" w:orient="landscape"/><w:pgMar w:top="360" w:right="360" w:bottom="360" w:left="360"/><w:bidi/></w:sectPr></w:body></w:document>"""
        return standardDocx(xml)
    }

    fun buildNotificationSheetsToFile(outputFile: File, cards: List<List<Pair<String, String>>>): File {
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(buildNotificationSheets(cards))
        return outputFile
    }

    fun buildPortraitTitledTableToFile(outputFile: File, title: String, subtitle: String?, columns: List<String>, rows: List<List<String>>): File {
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(buildPortraitTitledTable(title, subtitle, columns, rows))
        return outputFile
    }

    fun buildEnvelopeSheetsToFile(outputFile: File, recipients: List<Pair<String, String>>): File {
        outputFile.parentFile?.mkdirs()
        val body = StringBuilder()
        recipients.forEachIndexed { index, (name, address) ->
            body.append("<w:p><w:pPr><w:bidi/><w:jc w:val=\"center\"/><w:spacing w:before=\"1800\"/></w:pPr><w:r><w:rPr><w:rtl/><w:b/><w:sz w:val=\"34\"/></w:rPr><w:t>${escape(name)}</w:t></w:r></w:p>")
            body.append("<w:p><w:pPr><w:bidi/><w:jc w:val=\"center\"/><w:spacing w:before=\"300\"/></w:pPr><w:r><w:rPr><w:rtl/><w:sz w:val=\"28\"/></w:rPr><w:t>${escape(address)}</w:t></w:r></w:p>")
            if (index < recipients.lastIndex) body.append(pageBreakParagraph())
        }
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="12472" w:h="6236" w:orient="landscape"/><w:pgMar w:top="360" w:right="720" w:bottom="360" w:left="720"/><w:bidi/></w:sectPr></w:body></w:document>"""
        outputFile.writeBytes(standardDocx(xml))
        return outputFile
    }

    private fun notificationSheet(cards: List<List<Pair<String, String>>>): String {
        fun cell(fields: List<Pair<String, String>>): String {
            val content = if (fields.isEmpty()) EMPTY_PARAGRAPH else buildString {
                append(paragraph("إخطار", "Title"))
                fields.forEach { (label, value) -> append(paragraph("$label: $value")) }
            }
            return "<w:tc><w:tcPr><w:tcW w:w=\"8010\" w:type=\"dxa\"/><w:tcMar><w:top w:w=\"140\" w:type=\"dxa\"/><w:left w:w=\"140\" w:type=\"dxa\"/><w:bottom w:w=\"140\" w:type=\"dxa\"/><w:right w:w=\"140\" w:type=\"dxa\"/></w:tcMar></w:tcPr>$content</w:tc>"
        }
        fun row(left: List<Pair<String, String>>, right: List<Pair<String, String>>) =
            "<w:tr><w:trPr><w:trHeight w:val=\"5400\" w:hRule=\"atLeast\"/></w:trPr>${cell(left)}${cell(right)}</w:tr>"
        return "<w:tbl><w:tblPr><w:tblW w:w=\"16020\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/><w:bidiVisual/><w:tblBorders><w:top w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:left w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:bottom w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:right w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:insideH w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:insideV w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/></w:tblBorders></w:tblPr><w:tblGrid><w:gridCol w:w=\"8010\"/><w:gridCol w:w=\"8010\"/></w:tblGrid>${row(cards[0], cards[1])}${row(cards[2], cards[3])}</w:tbl>"
    }

    private fun pageBreakParagraph() = "<w:p><w:pPr><w:bidi/></w:pPr><w:r><w:br w:type=\"page\"/></w:r></w:p>"

    fun buildTitledTableToFile(outputFile: File, title: String, subtitle: String?, columns: List<String>, rows: List<List<String>>): File {
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(buildTitledTable(title, subtitle, columns, rows))
        return outputFile
    }

    private fun standardDocx(documentXml: String): ByteArray = zipParts(
        listOf(
            "[Content_Types].xml" to CONTENT_TYPES_XML,
            "_rels/.rels" to RELS_XML,
            "word/_rels/document.xml.rels" to DOCUMENT_RELS_XML,
            "word/styles.xml" to STYLES_XML,
            "word/document.xml" to documentXml
        )
    )

    private fun zipParts(parts: List<Pair<String, String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            parts.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun buildDocumentXml(title: String, subtitle: String?, columns: List<String>, rows: List<List<String>>, landscape: Boolean): String {
        val body = StringBuilder()
        body.append(paragraph(title, "Title"))
        if (!subtitle.isNullOrBlank()) body.append(paragraph(subtitle, "Subtitle"))
        body.append(EMPTY_PARAGRAPH)
        body.append(table(columns, rows))
        body.append(EMPTY_PARAGRAPH)
        val page = if (landscape) {
            "<w:pgSz w:w=\"16838\" w:h=\"11906\" w:orient=\"landscape\"/><w:pgMar w:top=\"720\" w:right=\"720\" w:bottom=\"720\" w:left=\"720\"/>"
        } else {
            "<w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"720\" w:right=\"720\" w:bottom=\"720\" w:left=\"720\"/>"
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr>$page<w:bidi/></w:sectPr></w:body></w:document>"""
    }

    private fun paragraph(text: String, styleId: String? = null): String {
        val styleTag = if (styleId != null) "<w:pPr><w:pStyle w:val=\"$styleId\"/><w:bidi/></w:pPr>" else "<w:pPr><w:bidi/></w:pPr>"
        return "<w:p>$styleTag<w:r><w:rPr><w:rtl/></w:rPr><w:t xml:space=\"preserve\">${escape(text)}</w:t></w:r></w:p>"
    }

    private fun table(columns: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/><w:tblW w:w=\"0\" w:type=\"auto\"/><w:bidiVisual/></w:tblPr><w:tblGrid>")
        repeat(columns.size) { sb.append("<w:gridCol/>") }
        sb.append("</w:tblGrid>")
        sb.append(tableRow(columns, true))
        rows.forEach { sb.append(tableRow(it, false)) }
        sb.append("</w:tbl>")
        return sb.toString()
    }

    private fun tableRow(cells: List<String>, isHeader: Boolean): String {
        val sb = StringBuilder("<w:tr>")
        cells.forEach { cellText ->
            val runProps = if (isHeader) "<w:rPr><w:rtl/><w:b/></w:rPr>" else "<w:rPr><w:rtl/></w:rPr>"
            sb.append("<w:tc><w:tcPr><w:tcW w:w=\"0\" w:type=\"auto\"/></w:tcPr><w:p><w:pPr><w:bidi/></w:pPr><w:r>$runProps<w:t xml:space=\"preserve\">${escape(cellText)}</w:t></w:r></w:p></w:tc>")
        }
        sb.append("</w:tr>")
        return sb.toString()
    }

    private fun escape(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    companion object {
        private const val EMPTY_PARAGRAPH = "<w:p/>"
        private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>"""
        private const val RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        private const val DOCUMENT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
        private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:cs="Traditional Arabic"/><w:sz w:val="22"/><w:rtl/></w:rPr></w:rPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style><w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:pPr><w:jc w:val="center"/></w:pPr><w:rPr><w:b/><w:sz w:val="32"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/><w:basedOn w:val="Normal"/><w:pPr><w:jc w:val="center"/></w:pPr><w:rPr><w:i/><w:sz w:val="24"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading"><w:name w:val="Heading"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="240" w:after="120"/></w:pPr><w:rPr><w:b/><w:sz w:val="26"/><w:u w:val="single"/></w:rPr></w:style><w:style w:type="table" w:styleId="TableGrid"><w:name w:val="Table Grid"/><w:tblPr><w:tblBorders><w:top w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:left w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:bottom w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:right w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:insideH w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:insideV w:val="single" w:sz="4" w:space="0" w:color="auto"/></w:tblBorders></w:tblPr></w:style></w:styles>"""
    }
}

