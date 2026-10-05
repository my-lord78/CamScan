package com.scanku.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One scanned page. [fileName] is a bare UUID name; the file lives at
 * filesDir/docs/<documentId>/<fileName> and is resolved (and path-checked) by the repository.
 */
@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val position: Int,
    val fileName: String,
    val ocrText: String? = null,
    val createdAt: Long,
)

/** Row for the home grid. */
data class DocumentSummary(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val pageCount: Int,
    val coverFile: String?,
)
