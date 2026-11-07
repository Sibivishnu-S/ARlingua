package com.example.document

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfGenerator(private val context: Context) {

    private val TAG = "PdfGenerator"

    companion object {
        // Standard A4 PDF dimensions in points (72 points/inch)
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN_HORIZONTAL = 40
        private const val MARGIN_TOP = 50
        private const val MARGIN_BOTTOM = 60
    }

    /**
     * Generates a PDF containing the translated document text with Unicode font rendering.
     */
    suspend fun generateTranslatedPdf(
        title: String,
        translatedText: String,
        sourceLanguage: String,
        targetLanguage: String,
        originalText: String? = null
    ): GeneratedPdfInfo = withContext(Dispatchers.IO) {

        val pdfDocument = PdfDocument()
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        val printableWidth = PAGE_WIDTH - (MARGIN_HORIZONTAL * 2)
        var pageNumber = 1

        // Paint setup
        val headerTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B4D3E") // Emerald
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4A5568")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A202C")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E0")
            strokeWidth = 1f
        }

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#718096")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        // Start Page 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // Render Header
        canvas.drawText("ARLinguaLearn Document Translation", MARGIN_HORIZONTAL.toFloat(), MARGIN_TOP.toFloat(), headerTitlePaint)
        canvas.drawText("Source: $sourceLanguage  ➔  Target: $targetLanguage  |  Date: $dateStr", MARGIN_HORIZONTAL.toFloat(), (MARGIN_TOP + 18).toFloat(), metaPaint)
        canvas.drawLine(MARGIN_HORIZONTAL.toFloat(), (MARGIN_TOP + 28).toFloat(), (PAGE_WIDTH - MARGIN_HORIZONTAL).toFloat(), (MARGIN_TOP + 28).toFloat(), dividerPaint)

        var currentY = MARGIN_TOP + 45

        // Render Body Text using StaticLayout for multi-line line wrapping & Unicode script support
        val paragraphs = translatedText.split("\n")

        for (paragraph in paragraphs) {
            val textToDraw = if (paragraph.isBlank()) " " else paragraph
            val staticLayout = StaticLayout.Builder
                .obtain(textToDraw, 0, textToDraw.length, bodyPaint, printableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.15f)
                .setIncludePad(true)
                .build()

            val layoutHeight = staticLayout.height

            // Check page overflow
            if (currentY + layoutHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                // Draw footer on current page
                drawFooter(canvas, pageNumber, footerPaint)
                pdfDocument.finishPage(page)

                // Start new page
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas

                // Minimal Header on subsequent pages
                canvas.drawText("ARLinguaLearn Translation — Page $pageNumber", MARGIN_HORIZONTAL.toFloat(), 35f, metaPaint)
                canvas.drawLine(MARGIN_HORIZONTAL.toFloat(), 42f, (PAGE_WIDTH - MARGIN_HORIZONTAL).toFloat(), 42f, dividerPaint)
                currentY = MARGIN_TOP + 10
            }

            canvas.save()
            canvas.translate(MARGIN_HORIZONTAL.toFloat(), currentY.toFloat())
            staticLayout.draw(canvas)
            canvas.restore()

            currentY += layoutHeight + 8
        }

        // Finish last page footer
        drawFooter(canvas, pageNumber, footerPaint)
        pdfDocument.finishPage(page)

        // Save PDF file to storage
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "ARLingua_Doc_${targetLanguage}_$timeStamp.pdf"

        val docsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ARLingua")
        if (!docsDir.exists()) {
            docsDir.mkdirs()
        }

        val pdfFile = File(docsDir, fileName)
        val fos = FileOutputStream(pdfFile)
        pdfDocument.writeTo(fos)
        fos.flush()
        fos.close()
        pdfDocument.close()

        Log.i(TAG, "PDF generated successfully at: ${pdfFile.absolutePath}")

        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val sizeFormatted = "${pdfFile.length() / 1024} KB"

        return@withContext GeneratedPdfInfo(
            fileUri = fileUri,
            filePath = pdfFile.absolutePath,
            fileName = fileName,
            title = title,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            pageCount = pageNumber,
            fileSizeFormatted = sizeFormatted
        )
    }

    private fun drawFooter(canvas: android.graphics.Canvas, pageNumber: Int, paint: TextPaint) {
        val footerText = "Generated with ARLinguaLearn AI  •  Page $pageNumber"
        val textWidth = paint.measureText(footerText)
        val x = (PAGE_WIDTH - textWidth) / 2
        canvas.drawText(footerText, x, (PAGE_HEIGHT - 25).toFloat(), paint)
    }

    /**
     * Creates an Intent to view/open the PDF in an external viewer
     */
    fun createOpenPdfIntent(fileUri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates a Share Intent for sending the PDF
     */
    fun createSharePdfIntent(fileUri: Uri, fileName: String): Intent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "ARLinguaLearn Document Translation - $fileName")
            putExtra(Intent.EXTRA_TEXT, "Here is my translated document PDF from ARLinguaLearn.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Share Translated PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
