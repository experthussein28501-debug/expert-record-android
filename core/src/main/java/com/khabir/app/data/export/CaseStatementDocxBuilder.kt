package com.khabir.app.data.export

import com.khabir.app.domain.model.toArabicIndicDigits
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds a print-ready RTL Word statement for the typed case registers. */
class CaseStatementDocxBuilder {
    fun build(title: String, headers: List<String>, rows: List<List<String>>): ByteArray {
        require(headers.size == 10) { "بيان القضايا يجب أن يحتوي عشرة أعمدة" }
        val body = StringBuilder()
        body.append(titleParagraph(title))
        body.append(table(headers, rows))
        body.append(footerParagraph("إجمالي عدد القضايا: ${rows.size}"))

        // A4 landscape gives the ten official register columns enough printable width.
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="16838" w:h="11906" w:orient="landscape"/><w:pgMar w:top="420" w:right="360" w:bottom="420" w:left="360"/><w:bidi/></w:sectPr></w:body></w:document>"""
        return zip(document)
    }

    private fun table(headers: List<String>, rows: List<List<String>>): String {
        val widths = listOf(430, 900, 1050, 900, 720, 1700, 2700, 2450, 1150, 1450)
        val sb = StringBuilder()
        sb.append("<w:tbl><w:tblPr><w:tblW w:w=\"15450\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/><w:bidiVisual/><w:jc w:val=\"center\"/><w:tblBorders><w:top w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:left w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:bottom w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:right w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:insideH w:val=\"single\" w:sz=\"5\" w:color=\"000000\"/><w:insideV w:val=\"single\" w:sz=\"5\" w:color=\"000000\"/></w:tblBorders></w:tblPr><w:tblGrid>")
        widths.forEach { sb.append("<w:gridCol w:w=\"$it\"/>") }
        sb.append("</w:tblGrid>")
        sb.append(row(headers, widths, header = true))
        rows.forEach { sb.append(row(it, widths, header = false)) }
        sb.append("</w:tbl>")
        return sb.toString()
    }

    private fun row(cells: List<String>, widths: List<Int>, header: Boolean): String {
        val sb = StringBuilder("<w:tr><w:trPr><w:cantSplit/>")
        if (header) sb.append("<w:tblHeader/>")
        sb.append("</w:trPr>")
        widths.forEachIndexed { index, width ->
            val text = cells.getOrNull(index).orEmpty()
            val bold = if (header) "<w:b/>" else ""
            val size = if (header) 19 else 17
            sb.append("<w:tc><w:tcPr><w:tcW w:w=\"$width\" w:type=\"dxa\"/><w:vAlign w:val=\"center\"/><w:tcMar><w:top w:w=\"45\" w:type=\"dxa\"/><w:left w:w=\"40\" w:type=\"dxa\"/><w:bottom w:w=\"45\" w:type=\"dxa\"/><w:right w:w=\"40\" w:type=\"dxa\"/></w:tcMar></w:tcPr><w:p><w:pPr><w:bidi/><w:jc w:val=\"center\"/><w:spacing w:before=\"0\" w:after=\"0\" w:line=\"210\" w:lineRule=\"auto\"/></w:pPr><w:r><w:rPr><w:rtl/><w:lang w:val=\"ar-EG\" w:bidi=\"ar-EG\"/><w:rFonts w:ascii=\"Traditional Arabic\" w:hAnsi=\"Traditional Arabic\" w:cs=\"Traditional Arabic\"/>$bold<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/></w:rPr><w:t xml:space=\"preserve\">${escape(text.toArabicIndicDigits())}</w:t></w:r></w:p></w:tc>")
        }
        sb.append("</w:tr>")
        return sb.toString()
    }

    private fun titleParagraph(text: String) =
        "<w:p><w:pPr><w:bidi/><w:jc w:val=\"center\"/><w:keepNext/><w:spacing w:before=\"0\" w:after=\"120\"/></w:pPr><w:r><w:rPr><w:rtl/><w:b/><w:lang w:val=\"ar-EG\" w:bidi=\"ar-EG\"/><w:rFonts w:ascii=\"Traditional Arabic\" w:hAnsi=\"Traditional Arabic\" w:cs=\"Traditional Arabic\"/><w:sz w:val=\"26\"/><w:szCs w:val=\"26\"/></w:rPr><w:t xml:space=\"preserve\">${escape(text)}</w:t></w:r></w:p>"

    private fun footerParagraph(text: String) =
        "<w:p><w:pPr><w:bidi/><w:jc w:val=\"right\"/><w:spacing w:before=\"100\" w:after=\"0\"/></w:pPr><w:r><w:rPr><w:rtl/><w:b/><w:lang w:val=\"ar-EG\" w:bidi=\"ar-EG\"/><w:rFonts w:ascii=\"Traditional Arabic\" w:hAnsi=\"Traditional Arabic\" w:cs=\"Traditional Arabic\"/><w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/></w:rPr><w:t>${escape(text.toArabicIndicDigits())}</w:t></w:r></w:p>"

    private fun escape(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun zip(documentXml: String): ByteArray {
        val parts = listOf(
            "[Content_Types].xml" to CONTENT_TYPES,
            "_rels/.rels" to RELS,
            "word/_rels/document.xml.rels" to DOC_RELS,
            "word/styles.xml" to STYLES,
            "word/document.xml" to documentXml
        )
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

    companion object {
        private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>"""
        private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        private const val DOC_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
        private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Traditional Arabic" w:hAnsi="Traditional Arabic" w:cs="Traditional Arabic"/><w:lang w:val="ar-EG" w:bidi="ar-EG"/><w:rtl/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:bidi/><w:jc w:val="right"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:pPr><w:bidi/><w:jc w:val="right"/></w:pPr><w:rPr><w:rtl/><w:lang w:val="ar-EG" w:bidi="ar-EG"/></w:rPr></w:style></w:styles>"""
    }
}

