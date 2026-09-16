package com.khabir.app.data.export

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OfficialEnvelopeDocxBuilder {
    fun buildToFile(outputFile: File, recipients: List<Pair<String, String>>): File {
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(build(recipients))
        return outputFile
    }

    fun build(recipients: List<Pair<String, String>>): ByteArray {
        val body = buildString {
            recipients.forEachIndexed { index, (name, address) ->
                append(recipientBlock(name.trim(), address.trim()))
                if (index < recipients.lastIndex) append(pageBreak())
            }
        }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="12472" w:h="6236" w:orient="landscape"/><w:pgMar w:top="360" w:right="720" w:bottom="360" w:left="720"/><w:bidi/></w:sectPr></w:body></w:document>"""
        return zip(document)
    }

    private fun recipientBlock(name: String, address: String): String = buildString {
        append(paragraph(name, bold = true, size = 34, before = 1500))
        append(paragraph(address, bold = false, size = 28, before = 260))
    }

    private fun paragraph(text: String, bold: Boolean, size: Int, before: Int): String {
        val boldTag = if (bold) "<w:b/>" else ""
        return "<w:p><w:pPr><w:bidi/><w:jc w:val=\"center\"/><w:spacing w:before=\"$before\" w:after=\"0\"/></w:pPr><w:r><w:rPr><w:rtl/><w:lang w:val=\"ar-EG\" w:bidi=\"ar-EG\"/><w:rFonts w:ascii=\"Traditional Arabic\" w:hAnsi=\"Traditional Arabic\" w:cs=\"Traditional Arabic\"/>$boldTag<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/></w:rPr><w:t xml:space=\"preserve\">${escape(text)}</w:t></w:r></w:p>"
    }

    private fun pageBreak() = "<w:p><w:pPr><w:bidi/></w:pPr><w:r><w:rPr><w:rtl/></w:rPr><w:br w:type=\"page\"/></w:r></w:p>"

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
        private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Traditional Arabic" w:hAnsi="Traditional Arabic" w:cs="Traditional Arabic"/><w:lang w:val="ar-EG" w:bidi="ar-EG"/><w:rtl/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:bidi/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:pPr><w:bidi/></w:pPr><w:rPr><w:rtl/><w:lang w:val="ar-EG" w:bidi="ar-EG"/></w:rPr></w:style></w:styles>"""
    }
}
