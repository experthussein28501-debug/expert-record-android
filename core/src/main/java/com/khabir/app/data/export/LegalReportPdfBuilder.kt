package com.khabir.app.data.export

import com.khabir.app.domain.model.toArabicIndicDigits
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

/** A4 PDF renderer matching the recurring structure of Ministry of Justice expert reports. */
class LegalReportPdfBuilder {
    fun buildToFile(
        outputFile: File,
        reportTitle: String,
        coverFields: List<Pair<String, String>>,
        sections: List<Pair<String, String>>,
        siteSketchFile: File? = null,
        logoBytes: ByteArray? = null
    ): File {
        outputFile.parentFile?.mkdirs()
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 42
        val usableWidth = pageWidth - margin * 2
        val pageBottom = pageHeight - margin

        val fields = coverFields.toMap()
        val ministry = fields["الوزارة"].orEmpty().ifBlank { "وزارة العدل" }
        val sector = fields["القطاع"].orEmpty()
        val department = fields["الإدارة"].orEmpty()
        val expert = fields["الخبير"].orEmpty()
        val caseNumber = fields["رقم الدعوى"].orEmpty()
        val court = fields["المحكمة"].orEmpty()
        // الغلاف يعرض «المقامة من» و«ضد» فقط، مع دعم المفتاح القديم للتوافق.
        val plaintiffs = fields["المقامة من"] ?: fields["المرفوعة من"].orEmpty()
        val defendants = fields["ضد"].orEmpty()
        val incoming = fields["الوارد"].orEmpty()
        val caseIntro = fields["صيغة رقم القضية"].orEmpty().ifBlank { "فى الدعوى رقم" }
        val firstPartyLabel = fields["تسمية الطرف الأول"].orEmpty().ifBlank { "المقامة من" }
        val secondPartyLabel = fields["تسمية الطرف الثاني"].orEmpty().ifBlank { "ضـــــد" }
        val compactCover = fields["نمط الغلاف"] == "مختصر"
        val runningHeader = fields["رأس التقرير"].orEmpty().trim()

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        lateinit var canvas: Canvas
        var y = margin

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        fun buildLayout(text: String, centered: Boolean, justified: Boolean, width: Int = usableWidth, leftAligned: Boolean = false): StaticLayout {
            val displayText = text.toArabicIndicDigits()
            val alignment = when {
                centered -> Layout.Alignment.ALIGN_CENTER
                leftAligned -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }
            val builder = StaticLayout.Builder.obtain(displayText, 0, displayText.length, paint, width)
                .setAlignment(alignment)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false)
                .setLineSpacing(2f, 1f)
            if (justified) builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
            return builder.build()
        }

        fun drawRawLayout(layout: StaticLayout, top: Int, left: Int = margin) {
            canvas.save()
            canvas.translate(left.toFloat(), top.toFloat())
            layout.draw(canvas)
            canvas.restore()
        }

        fun newPage() {
            page?.let { pdf.finishPage(it) }
            pageNumber += 1
            val newPage = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            page = newPage
            canvas = newPage.canvas
            y = margin

            if (runningHeader.isNotBlank()) {
                paint.textSize = 10f
                paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                val headerLayout = buildLayout(runningHeader, centered = false, justified = false)
                drawRawLayout(headerLayout, margin)
                y = margin + headerLayout.height + 8
            }
        }

        fun drawLayout(layout: StaticLayout) {
            drawRawLayout(layout, y)
            y += layout.height
        }

        fun drawBlock(
            text: String,
            size: Float = 15f,
            bold: Boolean = false,
            centered: Boolean = false,
            justified: Boolean = false,
            after: Int = 8,
            minimumLinesWithBlock: Int = 0,
            leftAligned: Boolean = false
        ) {
            if (text.isBlank()) return
            paint.textSize = size
            paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)

            var remaining = text.trim()
            while (remaining.isNotEmpty()) {
                var layout = buildLayout(remaining, centered, justified, leftAligned = leftAligned)
                var availableHeight = pageBottom - y

                if (minimumLinesWithBlock > 0 && layout.lineCount > 0) {
                    val requiredLine = (minimumLinesWithBlock - 1).coerceAtMost(layout.lineCount - 1)
                    val requiredHeight = layout.getLineBottom(requiredLine) + after
                    if (availableHeight < requiredHeight) {
                        newPage()
                        availableHeight = pageBottom - y
                    }
                }

                if (layout.height <= availableHeight) {
                    drawLayout(layout)
                    y += after
                    break
                }

                if (availableHeight <= paint.fontSpacing.toInt()) {
                    newPage()
                    continue
                }

                var lastFittingLine = -1
                for (lineIndex in 0 until layout.lineCount) {
                    if (layout.getLineBottom(lineIndex) <= availableHeight) {
                        lastFittingLine = lineIndex
                    } else {
                        break
                    }
                }

                if (lastFittingLine < 0) {
                    newPage()
                    continue
                }

                val splitAt = layout.getLineEnd(lastFittingLine).coerceIn(1, remaining.length)
                val pageText = remaining.substring(0, splitAt).trimEnd()
                if (pageText.isNotEmpty()) {
                    layout = buildLayout(pageText, centered, justified, leftAligned = leftAligned)
                    drawLayout(layout)
                }

                remaining = remaining.substring(splitAt).trimStart()
                if (remaining.isNotEmpty()) newPage() else y += after
            }
        }

        fun ensureHeadingWithBody(heading: String, firstBodyLine: String?) {
            paint.textSize = 16f
            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            val headingHeight = buildLayout(heading, centered = false, justified = false).height

            val bodyHeight = if (firstBodyLine.isNullOrBlank()) 0 else {
                paint.textSize = 14f
                paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                val bodyLayout = buildLayout(firstBodyLine, centered = false, justified = true)
                if (bodyLayout.lineCount > 0) bodyLayout.getLineBottom(0) else 0
            }

            if (pageBottom - y < headingHeight + 4 + bodyHeight) newPage()
        }

        newPage()

        if (compactCover) {
            paint.textSize = 14f
            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            val half = usableWidth / 2
            val rightText = listOf(ministry, sector, department).filter(String::isNotBlank).joinToString("\n")
            val rightLayout = buildLayout(rightText, centered = false, justified = false, width = half)
            val leftText = if (incoming.isBlank()) "" else "وارد $incoming"
            val leftLayout = buildLayout(leftText, centered = false, justified = false, width = half, leftAligned = true)
            val top = y
            drawRawLayout(leftLayout, top, margin)
            drawRawLayout(rightLayout, top, margin + half)
            y += maxOf(leftLayout.height, rightLayout.height) + 8

            drawBlock(reportTitle, 19f, bold = true, centered = true, after = 3)
            if (caseNumber.isNotBlank()) {
                drawBlock("$caseIntro $caseNumber${if (court.isBlank()) "" else " $court"}", 14f, bold = true, centered = true, after = 2)
            }
            if (plaintiffs.isNotBlank()) {
                drawBlock(firstPartyLabel, 13f, bold = true, centered = true, after = 1)
                drawBlock(plaintiffs, 13f, bold = true, centered = true, after = 2)
            }
            if (defendants.isNotBlank()) {
                drawBlock(secondPartyLabel, 13f, bold = true, centered = true, after = 1)
                drawBlock(defendants, 13f, bold = true, centered = true, after = 2)
            }
            y += 2
        } else {
            val logoBitmap = logoBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            if (logoBitmap != null) {
                val logoHeight = 56
                val logoWidth = (logoBitmap.width.toFloat() / logoBitmap.height * logoHeight).toInt().coerceAtLeast(1)
                val left = margin + (usableWidth - logoWidth) / 2
                canvas.drawBitmap(logoBitmap, null, Rect(left, y, left + logoWidth, y + logoHeight), null)
                y += logoHeight + 6
            }

            drawBlock(ministry, 17f, bold = true, centered = true, after = 3)
            if (sector.isNotBlank()) drawBlock(sector, 15f, bold = true, centered = true, after = 3)
            if (department.isNotBlank()) drawBlock(department, 15f, bold = true, centered = true, after = 8)
            drawBlock(reportTitle, 22f, bold = true, centered = true, after = 8)
            if (expert.isNotBlank()) drawBlock("مقدم من $expert خبير وزارة العدل", 14f, bold = true, centered = true, after = 3)
            if (caseNumber.isNotBlank()) {
                drawBlock("$caseIntro $caseNumber${if (court.isBlank()) "" else " $court"}", 15f, bold = true, centered = true, after = 5)
            }
            if (plaintiffs.isNotBlank()) {
                drawBlock(firstPartyLabel, 14f, bold = true, centered = true, after = 1)
                drawBlock(plaintiffs, 14f, bold = true, centered = true, after = 4)
            }
            if (defendants.isNotBlank()) {
                drawBlock(secondPartyLabel, 14f, bold = true, centered = true, after = 1)
                drawBlock(defendants, 14f, bold = true, centered = true, after = 4)
            }
            if (incoming.isNotBlank()) {
                val incomingParts = incoming.split(" لسنة ", limit = 2)
                drawBlock("وارد ${incomingParts[0]}", 13f, bold = true, centered = true, after = 1)
                if (incomingParts.size == 2) drawBlock("لسنة ${incomingParts[1]}", 13f, bold = true, centered = true, after = 5)
            }
            y += 6
        }
        val siteSketch = siteSketchFile?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath) }
        var sketchInserted = false
        fun drawInspectionSketch() {
            val sketch = siteSketch ?: return
            if (sketchInserted) return
            var available = pageBottom - y
            var scale = minOf(usableWidth.toFloat() / sketch.width, available.toFloat() / sketch.height)
            if (scale <= 0f || available < 90) {
                newPage()
                available = pageBottom - y
                scale = minOf(usableWidth.toFloat() / sketch.width, available.toFloat() / sketch.height)
            }
            val width = (sketch.width * scale).toInt().coerceAtLeast(1)
            val height = (sketch.height * scale).toInt().coerceAtLeast(1)
            val left = margin + (usableWidth - width) / 2
            canvas.drawBitmap(sketch, null, Rect(left, y, left + width, y + height), null)
            y += height + 8
            sketchInserted = true
        }

        sections.forEach { (heading, content) ->
            val firstBodyLine = content.lineSequence().firstOrNull { it.isNotBlank() }
            ensureHeadingWithBody(heading, firstBodyLine)
            drawBlock(heading, 16f, bold = true, centered = false, after = 4)
            val isExpertSignature = heading.contains("نتيجة أعمالنا") || content.trimStart().startsWith("الخبير")
            content.lines().forEach { line ->
                if (line.isNotBlank()) {
                    drawBlock(
                        line,
                        14f,
                        bold = isExpertSignature && line.trimStart().startsWith("الخبير"),
                        centered = false,
                        justified = !isExpertSignature,
                        after = 4,
                        leftAligned = isExpertSignature && line.trimStart().startsWith("الخبير")
                    )
                }
            }
            y += 5
            if (heading.contains("معاين")) drawInspectionSketch()
            if (y >= pageBottom) newPage()
        }

        page?.let { pdf.finishPage(it) }
        FileOutputStream(outputFile).use { pdf.writeTo(it) }
        pdf.close()
        return outputFile
    }
}
