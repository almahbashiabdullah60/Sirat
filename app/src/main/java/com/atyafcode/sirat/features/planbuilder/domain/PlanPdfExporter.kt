package com.atyafcode.sirat.features.planbuilder.domain

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

class PlanPdfExporter(private val context: Context) {

    fun exportToPdf(uri: Uri, planText: String): Boolean {
        return try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val textPaint = TextPaint().apply {
                color = android.graphics.Color.BLACK
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val margin = 40f
            val width = pageInfo.pageWidth - (margin * 2)
            
            // Simple multi-line text drawing for Arabic
            val staticLayout = StaticLayout.Builder.obtain(planText, 0, planText.length, textPaint, width.toInt())
                .setAlignment(android.view.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(true)
                .build()

            canvas.save()
            canvas.translate(margin, margin)
            staticLayout.draw(canvas)
            canvas.restore()

            document.finishPage(page)

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
