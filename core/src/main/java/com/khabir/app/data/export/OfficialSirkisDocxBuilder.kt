package com.khabir.app.data.export

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OfficialSirkisDocxBuilder {
    fun buildToFile(
        outputFile: File,
        expertName: String,
        rows: List<List<String>>,
        ministryOrSector: String = "وزارة العدل",
        department: String = "إدارة خبراء أسوان"
    ): File {
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(build(expertName, rows, ministryOrSector, department))
        return outputFile
    }

    fun build(expertName: String, rows: List<List<String>>, ministryOrSector: String = "وزارة العدل", department: String = "إدارة خبراء أسوان"): ByteArray {
        val body = StringBuilder()
        val pages = rows.chunked(31).ifEmpty { listOf(emptyList()) }
        pages.forEachIndexed { pageIndex, pageRows ->
            body.append(subHeader(ministryOrSector.ifBlank { "وزارة العدل" }))
            body.append(subHeader("قطاع الخبراء — ${department.ifBlank { "إدارة خبراء أسوان" }}"))
            body.append(header("سركي إخطارات"))
            body.append(subHeader("الخبير المحال إليه المأمورية"))
            body.append(subHeader("الخبير / $expertName"))
            body.append(table(pageRows))
            if (pageIndex < pages.lastIndex) body.append(pageBreak())
        }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="300" w:right="300" w:bottom="300" w:left="300"/><w:bidi/></w:sectPr></w:body></w:document>"""
        return zip(document)
    }

    private fun table(rows: List<List<String>>): String {
        val widths = listOf(520, 1120, 1450, 1180, 3350, 1120, 1120)
        val headers = listOf("م", "تحريراً", "رقم الدعوى", "تاريخ الجلسة", "اسماء الخصوم", "الصادر", "التوقيع")
        val paddedRows = rows + List((31 - rows.size).coerceAtLeast(0)) { List(7) { "" } }
        val sb = StringBuilder()
        sb.append("<w:tbl><w:tblPr><w:tblW w:w=\"9860\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/><w:bidiVisual/><w:jc w:val=\"right\"/><w:tblBorders><w:top w:val=\"single\" w:sz=\"6\" w:color=\"000000\"/><w:left w:val=\"single\" w:sz=\"6\" w:color=\"000000\"/><w:bottom w:val=\"single\" w:sz=\"6\" w:color=\"000000\"/><w:right w:val=\"single\" w:sz=\"6\" w:color=\"000000\"/><w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"000000\"/><w:insideV w:val=\"single\" w:sz=\"4\" w:color=\"000000\"/></w:tblBorders></w:tblPr><w:tblGrid>")
        widths.forEach { sb.append("<w:gridCol w:w=\"$it\"/>") }
        sb.append("</w:tblGrid>")
        sb.append(row(headers, widths, header = true))
        paddedRows.forEach { sb.append(row(it, widths, header = false)) }
        sb.append("</w:tbl>")
        return sb.toString()
    }

    private fun row(cells: List<String>, widths: List<Int>, header: Boolean): String {
        val height = if (header) 420 else 390
        val sb = StringBuilder("<w:tr><w:trPr><w:trHeight w:val=\"$height\" w:hRule=\"exact\"/><w:cantSplit/></w:trPr>")
        widths.forEachIndexed { index, width ->
            val text = cells.getOrNull(index).orEmpty()
            val alignment = if (!header && index == 4) "right" else "center"
            val bold = if (header) "<w:b/>" else ""
            sb.append("<w:tc><w:tcPr><w:tcW w:w=\"$width\" w:type=\"dxa\"/><w:vAlign w:val=\"center\"/><w:tcMar><w:top w:w=\"20\" w:type=\"dxa\"/><w:left w:w=\"25\" w:type=\"dxa\"/><w:bottom w:w=\"20\" w:type=\"dxa\"/><w:right w:w=\"25\" w:type=\"dxa\"/></w:tcMar></w:tcPr><w:p><w:pPr><w:bidi/><w:jc w:val=\"$alignment\"/><w:spacing w:before=\"0\" w:after=\"0\" w:line=\"180\" w:lineRule=\"auto\"/></w:pPr><w:r><w:rPr><w:rtl/><w:lang w:val=\"ar-EG\" w:bidi=\"ar-EG\"/><w:rFonts w:ascii=\"Traditional Arabic\" w:hAnsi=\"Traditional Arabic\" w:cs=\"Traditional Arabic\"/>$bold<w:sz w:val=\"15\"/><w:szCs w:val=\"15\"/></w:rPr><w:t xml:space=\"preserve\">${escape(arabicDigits(text))}</w:t></w:r></w:p></w:tc>")
        }
        sb.append("</w:tr>")
        return sb.toString()
    }

    private fun header(text: String) = paragraph(text, 22, true)
    private fun subHeader(text: String) = paragraph(text, 17, false)

    private fun paragraph(text: String, size: Int, bold: Boolean): String {
        val b = if (bold) "<w:b/>" else ""
        return "<w:p><w:pPr><w:bidi/><w:jc w:val=\"center\"/><w:spacing w:before=\"0\" w:after=\"20\"/></w:pPr><w:r><w:rPr><w:rtl/><w:lang w:val=\"ar-EG\" w:bidi=\"ar-EG\"/><w:rFonts w:ascii=\"Traditional Arabic\" w:hAnsi=\"Traditional Arabic\" w:cs=\"Traditional Arabic\"/>$b<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/></w:rPr><w:t xml:space=\"preserve\">${escape(text)}</w:t></w:r></w:p>"
    }

    private fun pageBreak() = "<w:p><w:pPr><w:bidi/></w:pPr><w:r><w:rPr><w:rtl/></w:rPr><w:br w:type=\"page\"/></w:r></w:p>"

    private fun escape(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun arabicDigits(text: String): String = buildString(text.length) {
        text.forEach { append(if (it in '0'..'9') "٠١٢٣٤٥٦٧٨٩"[it - '0'] else it) }
    }

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
        private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Traditional Arabic" w:hAnsi="Traditional Arabic" w:cs="Traditional Arabic"/><w:lang w:val="ar-EG" w:bidi="ar-EG"/><w:sz w:val="15"/><w:szCs w:val="15"/><w:rtl/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:bidi/><w:jc w:val="right"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:pPr><w:bidi/><w:jc w:val="right"/></w:pPr><w:rPr><w:rtl/><w:lang w:val="ar-EG" w:bidi="ar-EG"/></w:rPr></w:style></w:styles>"""
    }
}
