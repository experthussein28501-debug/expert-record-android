package com.khabir.app.data.export

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OfficialMailCoverDocxBuilder {
    fun buildToFile(
        outputFile: File,
        expertName: String,
        officeAddress: String,
        rows: List<List<String>>
    ): File {
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(build(expertName, officeAddress, rows))
        return outputFile
    }

    fun build(expertName: String, officeAddress: String, rows: List<List<String>>): ByteArray {
        val body = buildString {
            append(center("وزارة العدل", bold = true, size = 28))
            append(center("قطاع الخبراء", bold = true, size = 26))
            append(center("إدارة خبراء أسوان", bold = true, size = 24))
            append(center("حافظة بريد المكتب", bold = true, size = 26))
            append(line("خبير / $expertName", bold = true, size = 21))
            if (officeAddress.isNotBlank()) append(line(officeAddress, size = 19))
            append(spacer())
            append(table(rows))
            append(spacer())
            append(line("توقيع الخبير: ..............................", bold = true, size = 20))
        }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="500" w:right="500" w:bottom="500" w:left="500"/><w:bidi/></w:sectPr></w:body></w:document>"""
        return zip(document)
    }

    private fun table(rows: List<List<String>>): String {
        val columns = listOf("م", "اسم المرسل إليه", "العنوان", "رقم الدعوى", "المحكمة")
        val widths = listOf(650, 2500, 3500, 1900, 2300)
        return buildString {
            append("<w:tbl><w:tblPr><w:tblW w:w=\"10850\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/><w:bidiVisual/><w:jc w:val=\"right\"/><w:tblBorders><w:top w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:left w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:bottom w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:right w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/><w:insideH w:val=\"single\" w:sz=\"6\" w:color=\"000000\"/><w:insideV w:val=\"single\" w:sz=\"6\" w:color=\"000000\"/></w:tblBorders></w:tblPr><w:tblGrid>")
            widths.forEach { append("<w:gridCol w:w=\"$it\"/>") }
            append("</w:tblGrid>")
            append(tableRow(columns, widths, true))
            rows.forEach { append(tableRow(it, widths, false)) }
            append("</w:tbl>")
        }
    }

    private fun tableRow(values: List<String>, widths: List<Int>, header: Boolean): String = buildString {
        append("<w:tr><w:trPr><w:cantSplit/></w:trPr>")
        widths.indices.forEach { index ->
            val value = values.getOrNull(index).orEmpty()
            val bold = if (header) "<w:b/>" else ""
            val align = if (index == 0) "center" else "right"
            append("<w:tc><w:tcPr><w:tcW w:w=\"${widths[index]}\" w:type=\"dxa\"/><w:vAlign w:val=\"center\"/><w:tcMar><w:top w:w=\"70\" w:type=\"dxa\"/><w:right w:w=\"80\" w:type=\"dxa\"/><w:bottom w:w=\"70\" w:type=\"dxa\"/><w:left w:w=\"80\" w:type=\"dxa\"/></w:tcMar></w:tcPr><w:p><w:pPr><w:bidi/><w:jc w:val=\"$align\"/><w:spacing w:before=\"0\" w:after=\"0\"/></w:pPr><w:r><w:rPr><w:rtl/><w:lang w:val=\"ar-EG\" w:bidi=\"ar-EG\"/><w:rFonts w:ascii=\"Traditional Arabic\" w:hAnsi=\"Traditional Arabic\" w:cs=\"Traditional Arabic\"/>$bold<w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/></w:rPr><w:t xml:space=\"preserve\">${escape(value)}</w:t></w:r></w:p></w:tc>")
        }
        append("</w:tr>")
    }

    private fun center(text: String, bold: Boolean = false, size: Int = 20): String = paragraph(text, "center", bold, size)
    private fun line(text: String, bold: Boolean = false, size: Int = 20): String = paragraph(text, "right", bold, size)

    private fun paragraph(text: String, align: String, bold: Boolean, size: Int): String {
        val b = if (bold) "<w:b/>" else ""
        return "<w:p><w:pPr><w:bidi/><w:jc w:val=\"$align\"/><w:spacing w:before=\"0\" w:after=\"20\"/></w:pPr><w:r><w:rPr><w:rtl/><w:lang w:val=\"ar-EG\" w:bidi=\"ar-EG\"/><w:rFonts w:ascii=\"Traditional Arabic\" w:hAnsi=\"Traditional Arabic\" w:cs=\"Traditional Arabic\"/>$b<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/></w:rPr><w:t xml:space=\"preserve\">${escape(text)}</w:t></w:r></w:p>"
    }

    private fun spacer() = "<w:p><w:pPr><w:bidi/><w:spacing w:before=\"0\" w:after=\"0\"/></w:pPr></w:p>"

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
        private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Traditional Arabic" w:hAnsi="Traditional Arabic" w:cs="Traditional Arabic"/><w:lang w:val="ar-EG" w:bidi="ar-EG"/><w:sz w:val="20"/><w:szCs w:val="20"/><w:rtl/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:bidi/><w:jc w:val="right"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:pPr><w:bidi/><w:jc w:val="right"/></w:pPr><w:rPr><w:rtl/><w:lang w:val="ar-EG" w:bidi="ar-EG"/></w:rPr></w:style></w:styles>"""
    }
}
