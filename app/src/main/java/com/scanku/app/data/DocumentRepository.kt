package com.scanku.app.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.room.withTransaction
import com.scanku.app.core.util.FileNames
import com.scanku.app.data.db.AppDatabase
import com.scanku.app.data.db.DocumentEntity
import com.scanku.app.data.db.DocumentSummary
import com.scanku.app.data.db.PageEntity
import com.scanku.app.imaging.BitmapIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Single source of truth for documents: Room rows + page JPEGs in app-private storage. */
class DocumentRepository(context: Context, private val db: AppDatabase) {

    private val dao = db.documentDao()
    private val docsRoot = File(context.filesDir, "docs")

    fun observeSummaries(query: String): Flow<List<DocumentSummary>> =
        dao.observeSummaries(FileNames.escapeLike(query.trim()))

    fun observeDocument(id: Long): Flow<DocumentEntity?> = dao.observeDocument(id)

    fun observePages(documentId: Long): Flow<List<PageEntity>> = dao.observePages(documentId)

    fun observePage(id: Long): Flow<PageEntity?> = dao.observePage(id)

    suspend fun getDocument(id: Long): DocumentEntity? = dao.getDocument(id)

    suspend fun getPages(documentId: Long): List<PageEntity> = dao.getPages(documentId)

    /**
     * Resolves a page file, refusing anything that would escape the document's directory.
     * File names come from our own DB, but the check makes a corrupted/tampered row harmless.
     */
    fun pageFile(documentId: Long, fileName: String): File {
        require(FileNames.isInternalImageName(fileName)) { "Unexpected page file name" }
        val dir = File(docsRoot, documentId.toString())
        val file = File(dir, fileName)
        require(file.canonicalPath.startsWith(dir.canonicalPath + File.separator)) { "Page path escapes its directory" }
        return file
    }

    fun pageFile(page: PageEntity): File = pageFile(page.documentId, page.fileName)

    /**
     * Saves [bitmap] as a new last page. Creates the document (named [newDocumentName]) when
     * [documentId] is null or no longer exists. Returns the document id.
     */
    suspend fun addPage(documentId: Long?, bitmap: Bitmap, newDocumentName: String): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val existing = documentId?.let { dao.getDocument(it) }
            val docId = existing?.id
                ?: dao.insertDocument(DocumentEntity(name = newDocumentName, createdAt = now, updatedAt = now))

            val fileName = "${UUID.randomUUID()}.jpg"
            val file = pageFile(docId, fileName)
            BitmapIo.saveJpeg(bitmap, file, JPEG_QUALITY)
            try {
                db.withTransaction {
                    val position = dao.maxPosition(docId) + 1
                    dao.insertPage(
                        PageEntity(documentId = docId, position = position, fileName = fileName, createdAt = now),
                    )
                    dao.touch(docId, now)
                }
            } catch (e: Exception) {
                file.delete() // don't leave an orphan JPEG behind a failed insert
                throw e
            }
            docId
        }

    suspend fun rename(id: Long, name: String) = withContext(Dispatchers.IO) {
        dao.rename(id, name, System.currentTimeMillis())
    }

    suspend fun deleteDocument(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteDocument(id) // pages cascade
        val dir = File(docsRoot, id.toString())
        if (dir.exists() && !dir.deleteRecursively()) Log.w(TAG, "Could not fully delete document files")
    }

    suspend fun deletePage(pageId: Long) = withContext(Dispatchers.IO) {
        val page = dao.getPage(pageId) ?: return@withContext
        db.withTransaction {
            dao.deletePage(pageId)
            val remaining = dao.getPages(page.documentId)
            dao.updatePages(remaining.mapIndexed { i, p -> p.copy(position = i) })
            dao.touch(page.documentId, System.currentTimeMillis())
        }
        if (!pageFile(page).delete()) Log.w(TAG, "Page file already missing")
    }

    /** Moves a page by [delta] positions (−1 = earlier, +1 = later). No-op at the ends. */
    suspend fun movePage(pageId: Long, delta: Int) = withContext(Dispatchers.IO) {
        val page = dao.getPage(pageId) ?: return@withContext
        db.withTransaction {
            val pages = dao.getPages(page.documentId).toMutableList()
            val from = pages.indexOfFirst { it.id == pageId }
            val to = from + delta
            if (from < 0 || to !in pages.indices) return@withTransaction
            val moved = pages.removeAt(from)
            pages.add(to, moved)
            dao.updatePages(pages.mapIndexed { i, p -> p.copy(position = i) })
            dao.touch(page.documentId, System.currentTimeMillis())
        }
    }

    suspend fun setOcrText(pageId: Long, text: String) = withContext(Dispatchers.IO) {
        dao.setOcrText(pageId, text)
    }

    suspend fun getPage(pageId: Long): PageEntity? = dao.getPage(pageId)

    private companion object {
        const val TAG = "DocumentRepository"
        const val JPEG_QUALITY = 90
    }
}
