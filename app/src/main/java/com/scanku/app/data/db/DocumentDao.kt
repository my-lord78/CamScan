package com.scanku.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    /** [pattern] must already be LIKE-escaped (see FileNames.escapeLike). Searches titles and OCR text. */
    @Query(
        """
        SELECT d.id, d.name, d.updatedAt,
            (SELECT COUNT(*) FROM pages p WHERE p.documentId = d.id) AS pageCount,
            (SELECT p.fileName FROM pages p WHERE p.documentId = d.id ORDER BY p.position ASC LIMIT 1) AS coverFile
        FROM documents d
        WHERE :pattern = ''
            OR d.name LIKE '%' || :pattern || '%' ESCAPE '\'
            OR EXISTS (
                SELECT 1 FROM pages p
                WHERE p.documentId = d.id AND p.ocrText LIKE '%' || :pattern || '%' ESCAPE '\'
            )
        ORDER BY d.updatedAt DESC
        """,
    )
    fun observeSummaries(pattern: String): Flow<List<DocumentSummary>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeDocument(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocument(id: Long): DocumentEntity?

    @Insert
    suspend fun insertDocument(document: DocumentEntity): Long

    @Query("UPDATE documents SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE documents SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY position ASC")
    fun observePages(documentId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY position ASC")
    suspend fun getPages(documentId: Long): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id")
    fun observePage(id: Long): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPage(id: Long): PageEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM pages WHERE documentId = :documentId")
    suspend fun maxPosition(documentId: Long): Int

    @Insert
    suspend fun insertPage(page: PageEntity): Long

    @Update
    suspend fun updatePages(pages: List<PageEntity>)

    @Query("UPDATE pages SET ocrText = :text WHERE id = :id")
    suspend fun setOcrText(id: Long, text: String?)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePage(id: Long)
}
