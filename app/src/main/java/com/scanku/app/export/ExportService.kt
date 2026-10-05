package com.scanku.app.export

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import com.scanku.app.core.util.FileNames
import com.scanku.app.core.util.PdfLayout
import com.scanku.app.core.util.PdfPageSize
import com.scanku.app.data.DocumentRepository
import com.scanku.app.imaging.BitmapIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Builds shareable copies of a document. Everything is written to cache/exports — the only
 * directory exposed through FileProvider — and that folder is wiped on app start.
 */
class ExportService(private val context: Context, private val repo: DocumentRepository) {

    private val exportDir: File get() = File(context.cacheDir, "exports").apply { mkdirs() }

    /** Renders all pages into a PDF and returns the file. */
    @Throws(IOException::class)
    suspend fun exportPdf(documentId: Long, pageSize: PdfPageSize): File = withContext(Dispatchers.IO) {
        val doc = repo.getDocument(documentId) ?: throw IOException("Document not found")
        val pages = repo.getPages(documentId)
        if (pages.isEmpty()) throw IOException("Document has no pages")

        val out = File(exportDir, FileNames.sanitize(doc.name) + ".pdf")
        val pdf = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        try {
            var written = 0
            pages.forEachIndexed { index, page ->
                // ~200 dpi on A4: sharp text, reasonable file size.
                val bmp = BitmapIo.decodeSampled(repo.pageFile(page), PDF_IMAGE_MAX_DIM)
                if (bmp == null) {
                    Log.w(TAG, "Skipping unreadable page ${index + 1}")
                    return@forEachIndexed
                }
                try {
                    val plan = PdfLayout.plan(pageSize, bmp.width, bmp.height)
                    val info = PdfDocument.PageInfo.Builder(plan.pageWidth, plan.pageHeight, index + 1).create()
                    val pdfPage = pdf.startPage(info)
                    val r = plan.image
                    pdfPage.canvas.drawBitmap(bmp, null, RectF(r.left, r.top, r.left + r.width, r.top + r.height), paint)
                    pdf.finishPage(pdfPage)
                    written++
                } finally {
                    bmp.recycle()
                }
            }
            if (written == 0) throw IOException("No readable pages")
            FileOutputStream(out).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        out
    }

    /** Writes the PDF to a user-chosen location (Storage Access Framework). */
    @Throws(IOException::class)
    suspend fun exportPdfTo(documentId: Long, pageSize: PdfPageSize, target: Uri) {
        val pdf = exportPdf(documentId, pageSize)
        withContext(Dispatchers.IO) {
            val os = context.contentResolver.openOutputStream(target) ?: throw IOException("Cannot open target")
            os.use { o -> pdf.inputStream().use { it.copyTo(o) } }
            pdf.delete()
        }
    }

    /** Copies page JPEGs to the export folder with readable names. */
    @Throws(IOException::class)
    suspend fun exportImages(documentId: Long, pageIds: List<Long>? = null): List<File> =
        withContext(Dispatchers.IO) {
            val doc = repo.getDocument(documentId) ?: throw IOException("Document not found")
            val all = repo.getPages(documentId)
            val selected = if (pageIds == null) all else all.filter { it.id in pageIds }
            val base = FileNames.sanitize(doc.name)
            selected.map { page ->
                val number = all.indexOf(page) + 1
                repo.pageFile(page).copyTo(File(exportDir, "${base}_hal$number.jpg"), overwrite = true)
            }
        }

    fun clear() {
        File(context.cacheDir, "exports").deleteRecursively()
    }

    private companion object {
        const val TAG = "ExportService"
        const val PDF_IMAGE_MAX_DIM = 2339
    }
}
