package com.atyafcode.sirat.features.planbuilder.domain

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PlanPdfExporter(private val context: Context) {

    fun exportToPdf(uri: Uri, planText: String): Boolean {
        return try {
            val document = PdfDocument()
            val pageWidth = 595 // A4 width
            val pageHeight = 842 // A4 height
            val margin = 40f
            val contentWidth = pageWidth - (margin * 2)

            val textPaint = TextPaint().apply {
                color = android.graphics.Color.BLACK
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val titlePaint = TextPaint().apply {
                color = android.graphics.Color.rgb(0, 102, 204)
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val datePaint = TextPaint().apply {
                color = android.graphics.Color.GRAY
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }

            // Create a layout to measure total height
            val fullLayout = StaticLayout.Builder.obtain(planText, 0, planText.length, textPaint, contentWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(true)
                .build()

            val headerHeight = 100f
            val footerHeight = 40f
            val availableHeightPerPage = pageHeight - margin - headerHeight - footerHeight
            
            var currentLine = 0
            val totalLines = fullLayout.lineCount
            var pageNumber = 1

            while (currentLine < totalLines) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = document.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                // Draw Header
                canvas.drawText("خطة التعافي المخصصة - تطبيق صراط", margin, margin + 20f, titlePaint)
                val currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                canvas.drawText("تاريخ التوليد: $currentDateTime", margin, margin + 45f, datePaint)
                canvas.drawLine(margin, margin + 55f, pageWidth - margin, margin + 55f, Paint().apply { strokeWidth = 1f; color = android.graphics.Color.LTGRAY })

                // Determine how many lines fit on this page
                var linesOnPage = 0
                var accumulatedHeight = 0f
                while (currentLine + linesOnPage < totalLines) {
                    val lineBottom = fullLayout.getLineBottom(currentLine + linesOnPage)
                    val lineTop = fullLayout.getLineTop(currentLine + linesOnPage)
                    val lineHeight = (lineBottom - lineTop).toFloat()
                    
                    if (accumulatedHeight + lineHeight > availableHeightPerPage) break
                    accumulatedHeight += lineHeight
                    linesOnPage++
                }

                // Draw text for this page
                canvas.save()
                canvas.translate(margin, margin + headerHeight)
                
                // We need a sub-layout for just the lines on this page
                val startOffset = fullLayout.getLineStart(currentLine)
                val endOffset = fullLayout.getLineEnd(currentLine + linesOnPage - 1)
                val pageText = planText.substring(startOffset, endOffset)
                
                val pageLayout = StaticLayout.Builder.obtain(pageText, 0, pageText.length, textPaint, contentWidth.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.2f)
                    .setIncludePad(true)
                    .build()
                
                pageLayout.draw(canvas)
                canvas.restore()

                // Draw Footer
                canvas.drawText("صفحة $pageNumber", pageWidth / 2f - 20f, pageHeight - 20f, datePaint)

                document.finishPage(page)
                currentLine += linesOnPage
                pageNumber++
                
                if (linesOnPage == 0) break // Safety break
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                document.writeTo(outputStream)
            }
            document.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
