package com.khabir.app.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfficeInteropService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun readDocxText(uri: Uri): String = withContext(Dispatchers.IO) {
        val xml = readZipEntry(uri, "word/document.xml") ?: error("ملف Word غير صالح")
        xml
            .replace(Regex("</w:p>"), "\n")
            .replace(Regex("<w:tab[^>]*/>"), "\t")
            .replace(Regex("<w:br[^>]*/>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .let(::unescapeXml)
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    suspend fun templateParagraphs(uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val xml = readZipEntry(uri, "word/document.xml") ?: error("اختر ملف DOCX")
        Regex("<w:p(?:\\s[^>]*)?>.*?</w:p>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .map { unescapeXml(it.value.replace(Regex("<[^>]+>"), "")) }.filter { it.isNotBlank() }.toList()
    }

    suspend fun canAutoFillBlankReportFields(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val xml = readZipEntry(uri, "word/document.xml") ?: error("اختر ملف DOCX")
        injectReportFieldsAfterBlankHeadings(xml, emptyMap(), previewOnly = true).second > 0
    }

    suspend fun fillDocxTemplate(uri: Uri, replacements: Map<String, String>, fileNamePrefix: String, paragraphMapping: Map<String, String> = emptyMap()): Uri = withContext(Dispatchers.IO) {
        val inputParts = linkedMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) inputParts[entry.name] = zip.readBytes()
                    entry = zip.nextEntry
                }
            }
        } ?: error("تعذر فتح قالب Word")
        if ("word/document.xml" !in inputParts) error("القالب ليس ملف Word صالحاً")

        val output = ByteArrayOutputStream()
        var replacementCount = 0
        ZipOutputStream(output).use { zip ->
            inputParts.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                val transformed = if (name.startsWith("word/") && name.endsWith(".xml")) {
                    val original = bytes.toString(Charsets.UTF_8)
                    var mapped = original
                    if (name == "word/document.xml") {
                        paragraphMapping.forEach { (paragraph, field) ->
                            mapped = replaceLogicalToken(mapped, paragraph, "{{$field}}")
                        }
                        val autoFilled = injectReportFieldsAfterBlankHeadings(mapped, replacements)
                        mapped = autoFilled.first
                        replacementCount += autoFilled.second
                    }
                    val filled = replaceTemplateTokensAcrossRuns(mapped, replacements)
                    if (filled != mapped) replacementCount++
                    filled.toByteArray(Charsets.UTF_8)
                } else bytes
                zip.write(transformed)
                zip.closeEntry()
            }
        }
        require(replacementCount > 0) { "لم تتم تعبئة أي خانة. اربط فقرات النموذج بالحقول أو استخدم علامات مثل {{الموضوع}}." }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${fileNamePrefix}_${System.currentTimeMillis()}.docx")
        file.writeBytes(output.toByteArray())
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Keeps the uploaded Word layout intact and fills only an empty paragraph directly after a
     * recognised report heading. This supports the expert's existing models without requiring
     * placeholders, while deliberately refusing to overwrite old case text.
     */
    internal fun injectReportFieldsAfterBlankHeadings(
        xml: String,
        replacements: Map<String, String>,
        previewOnly: Boolean = false
    ): Pair<String, Int> {
        val paragraphRegex = Regex("<w:p(?:\\s[^>]*)?>.*?</w:p>", RegexOption.DOT_MATCHES_ALL)
        val matches = paragraphRegex.findAll(xml).toList()
        if (matches.size < 2) return xml to 0
        var result = xml
        var count = 0
        for (index in matches.lastIndex - 1 downTo 0) {
            val heading = visibleParagraphText(matches[index].value).trim().trimEnd(':', '：', '-', '–')
            val key = reportFieldForHeading(heading) ?: continue
            val next = matches[index + 1]
            val nextText = visibleParagraphText(next.value).trim()
            if (nextText.isNotEmpty() && !nextText.matches(Regex("[._ـ…\\s]+"))) continue
            if (previewOnly) {
                count++
                continue
            }
            val value = replacements[key].orEmpty()
            if (value.isBlank()) continue
            count++
            val paragraph = addRtlTextToParagraph(next.value, value)
            result = result.replaceRange(next.range.first, next.range.last + 1, paragraph)
        }
        return result to count
    }

    private fun visibleParagraphText(paragraph: String): String =
        textNodes(paragraph).joinToString("") { it.text }

    private fun reportFieldForHeading(value: String): String? {
        val normalized = value.replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ة", "ه").trim()
        return when {
            normalized == "الموضوع" -> "الموضوع"
            normalized == "الماموريه" -> "المأمورية"
            normalized == "مباشره الماموريه" -> "مباشرة المأمورية"
            normalized.startsWith("اقوال المتهم") || normalized.contains("اقوال طرفي") || normalized.contains("اقوال الخصوم") -> "أقوال طرفي التداعي"
            normalized.contains("اقوال الشهود") || normalized.contains("سماع الشهود") -> "سماع الشهود"
            normalized.contains("المعاينه") -> "المعاينة"
            normalized == "الفحص" || normalized.contains("بحث المستندات") || normalized.contains("الاطلاع والمستندات") -> "بحث المستندات"
            normalized == "البحث" || normalized.startsWith("البحث والرد") || normalized == "البحث والدراسه" -> "البحث"
            normalized.contains("الحكم المستانف واسباب الاستئناف") -> "الوقائع"
            normalized == "النتيجه" || normalized.contains("النتيجه النهائيه") -> "النتيجة"
            // Work-minutes Word templates use the same blank-paragraph filling engine.
            // Keep these aliases here so a DOCX with ordinary Arabic headings can be
            // filled automatically without forcing the user through manual mapping.
            normalized == "رقم الدعوى" -> "رقم الدعوى"
            normalized == "السنه" || normalized == "سنه الدعوى" -> "السنة"
            normalized == "المحكمه" -> "المحكمة"
            normalized == "المرفوعه من" || normalized == "مرفوعه من" -> "المرفوعة من"
            normalized == "ضد" -> "ضد"
            normalized == "الوارد" || normalized == "رقم الوارد" -> "الوارد"
            normalized == "اسم الخبير" || normalized == "الخبير" -> "اسم الخبير"
            normalized == "القطاع" -> "القطاع"
            normalized == "الاداره" -> "الإدارة"
            normalized == "عدد النسخ" -> "عدد النسخ"
            normalized == "محاضر الاعمال" || normalized == "متن المحاضر" || normalized == "مجموعه محاضر الاعمال" -> "محاضر الأعمال"
            else -> null
        }
    }

    private fun addRtlTextToParagraph(paragraph: String, value: String): String {
        var output = paragraph
        if (!output.contains("<w:pPr")) {
            output = output.replaceFirst(">", "><w:pPr><w:bidi/><w:jc w:val=\"start\"/></w:pPr>")
        } else {
            val propertiesTag = Regex("<w:pPr(?:\\s[^>]*)?>").find(output)
            if (propertiesTag != null) {
                output = output.replaceRange(propertiesTag.range.last + 1, propertiesTag.range.last + 1, "<w:bidi/><w:jc w:val=\"start\"/>")
            }
        }
        val content = escapeXml(value.replace("\r\n", "\n").replace('\r', '\n'))
            .replace("\n", "</w:t><w:br/><w:t xml:space=\"preserve\">")
        val run = "<w:r><w:rPr><w:rtl/><w:lang w:bidi=\"ar-EG\"/></w:rPr><w:t xml:space=\"preserve\">$content</w:t></w:r>"
        return output.replace("</w:p>", "$run</w:p>")
    }

    internal fun replaceTemplateTokensAcrossRuns(xml: String, replacements: Map<String, String>): String {
        // Word is free to split one visible token across multiple <w:r>/<w:t> runs,
        // but a token must never be joined across separate paragraphs. Process each <w:p>
        // independently so placeholders remain paragraph-scoped while still supporting run splits.
        val paragraphRegex = Regex("<w:p(?:\\s[^>]*)?>.*?</w:p>", RegexOption.DOT_MATCHES_ALL)
        val matches = paragraphRegex.findAll(xml).toList()
        if (matches.isEmpty()) return replaceTokensInsideParagraph(xml, replacements)

        val builder = StringBuilder(xml)
        for (match in matches.asReversed()) {
            val replaced = replaceTokensInsideParagraph(match.value, replacements)
            builder.replace(match.range.first, match.range.last + 1, replaced)
        }
        return builder.toString()
    }

    private fun replaceTokensInsideParagraph(paragraphXml: String, replacements: Map<String, String>): String {
        var result = paragraphXml
        replacements.forEach { (key, value) ->
            result = replaceLogicalToken(result, "{{$key}}", value)
            result = replaceLogicalToken(result, "«$key»", value)
        }
        return result
    }

    private fun replaceLogicalToken(xml: String, token: String, replacement: String): String {
        if (token.isEmpty() || replacement.contains(token)) return xml
        var current = xml
        while (true) {
            val nodes = textNodes(current)
            if (nodes.isEmpty()) return current
            val logicalText = nodes.joinToString("") { it.text }
            val tokenStart = logicalText.indexOf(token)
            if (tokenStart < 0) return current
            val tokenEnd = tokenStart + token.length

            val firstNodeIndex = nodes.indexOfFirst { tokenStart < it.logicalEnd && tokenEnd > it.logicalStart }
            val lastNodeIndex = nodes.indexOfLast { tokenStart < it.logicalEnd && tokenEnd > it.logicalStart }
            if (firstNodeIndex < 0 || lastNodeIndex < 0) return current

            val updatedTexts = nodes.map { it.text }.toMutableList()
            val firstNode = nodes[firstNodeIndex]
            val lastNode = nodes[lastNodeIndex]
            val prefixLength = (tokenStart - firstNode.logicalStart).coerceAtLeast(0)
            val suffixStart = (tokenEnd - lastNode.logicalStart).coerceIn(0, lastNode.text.length)
            val prefix = firstNode.text.substring(0, prefixLength.coerceAtMost(firstNode.text.length))
            val suffix = lastNode.text.substring(suffixStart)

            val normalizedReplacement = replacement.replace("\r\n", "\n").replace('\r', '\n')
            updatedTexts[firstNodeIndex] = prefix + normalizedReplacement + if (firstNodeIndex == lastNodeIndex) suffix else ""
            for (index in firstNodeIndex + 1 until lastNodeIndex) updatedTexts[index] = ""
            if (lastNodeIndex != firstNodeIndex) updatedTexts[lastNodeIndex] = suffix

            val builder = StringBuilder(current)
            for (index in nodes.indices.reversed()) {
                val node = nodes[index]
                // A line break inside w:t is whitespace in Word, not a visible
                // paragraph break. Preserve multi-line report fields as Word
                // line-break elements while keeping the paragraph's style.
                val escaped = escapeXml(updatedTexts[index])
                    .replace("\n", "</w:t><w:br/><w:t xml:space=\"preserve\">")
                builder.replace(node.contentStart, node.contentEnd, escaped)
            }
            current = builder.toString()
        }
    }

    private data class WordTextNode(
        val contentStart: Int,
        val contentEnd: Int,
        val logicalStart: Int,
        val logicalEnd: Int,
        val text: String
    )

    private fun textNodes(xml: String): List<WordTextNode> {
        val regex = Regex("<w:t(?:\\s[^>]*)?>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
        var logicalOffset = 0
        return regex.findAll(xml).map { match ->
            val rawText = match.groupValues[1]
            val decoded = unescapeXml(rawText)
            val groupRange = match.groups[1]!!.range
            val node = WordTextNode(
                contentStart = groupRange.first,
                contentEnd = groupRange.last + 1,
                logicalStart = logicalOffset,
                logicalEnd = logicalOffset + decoded.length,
                text = decoded
            )
            logicalOffset += decoded.length
            node
        }.toList()
    }

    suspend fun readXlsxRows(uri: Uri): List<List<String>> = withContext(Dispatchers.IO) {
        val entries = readZipEntries(uri)
        val sharedStrings = entries["xl/sharedStrings.xml"]
            ?.let { xml -> Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si ->
                Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(si.groupValues[1])
                    .joinToString("") { unescapeXml(it.groupValues[1]) }
            }.toList() }
            ?: emptyList()

        val sheet = entries["xl/worksheets/sheet1.xml"] ?: error("لا توجد ورقة بيانات في ملف Excel")
        Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(sheet).map { rowMatch ->
            Regex("<c([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL).findAll(rowMatch.groupValues[1]).map { cell ->
                val attrs = cell.groupValues[1]
                val body = cell.groupValues[2]
                val type = Regex("t=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
                val inline = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)
                val raw = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1).orEmpty()
                when (type) {
                    "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                    "inlineStr", "str" -> unescapeXml(inline ?: raw)
                    else -> unescapeXml(raw)
                }
            }.toList()
        }.toList()
    }

    suspend fun exportXlsx(fileNamePrefix: String, rows: List<List<String>>): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${fileNamePrefix}_${System.currentTimeMillis()}.xlsx")
        file.writeBytes(buildXlsx(rows))
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun buildXlsx(rows: List<List<String>>): ByteArray {
        val sheetRows = rows.mapIndexed { rowIndex, cells ->
            val rowNo = rowIndex + 1
            val cellXml = cells.mapIndexed { colIndex, value ->
                val ref = "${columnName(colIndex)}$rowNo"
                "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escapeXml(value)}</t></is></c>"
            }.joinToString("")
            "<row r=\"$rowNo\">$cellXml</row>"
        }.joinToString("")

        val sheet = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$sheetRows</sheetData></worksheet>"""
        val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="البيانات" sheetId="1" r:id="rId1"/></sheets></workbook>"""
        val workbookRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
        val types = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""
        return zipBytes(listOf(
            "[Content_Types].xml" to types,
            "_rels/.rels" to rootRels,
            "xl/workbook.xml" to workbook,
            "xl/_rels/workbook.xml.rels" to workbookRels,
            "xl/worksheets/sheet1.xml" to sheet
        ))
    }

    private fun readZipEntry(uri: Uri, target: String): String? =
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == target) return@use zip.readBytes().toString(Charsets.UTF_8)
                    entry = zip.nextEntry
                }
                null
            }
        }

    private fun readZipEntries(uri: Uri): Map<String, String> {
        val result = linkedMapOf<String, String>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".xml")) result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                    entry = zip.nextEntry
                }
            }
        } ?: error("تعذر فتح الملف")
        return result
    }

    private fun zipBytes(parts: List<Pair<String, String>>): ByteArray {
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

    private fun columnName(index: Int): String {
        var n = index + 1
        val out = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            out.append(('A'.code + r).toChar())
            n = (n - 1) / 26
        }
        return out.reverse().toString()
    }

    private fun escapeXml(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun unescapeXml(text: String) = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
