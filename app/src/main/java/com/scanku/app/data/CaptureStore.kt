package com.scanku.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.scanku.app.core.util.FileNames
import com.scanku.app.imaging.BitmapIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Temporary raw captures (camera shots and gallery imports) waiting for crop/filter.
 * Captures are passed between screens by *name only*; [resolve] accepts nothing but
 * app-generated UUID names, so a navigation argument can never point outside this folder.
 */
class CaptureStore(private val context: Context) {

    private val dir = File(context.cacheDir, "captures")

    fun newFile(): File {
        dir.mkdirs()
        return File(dir, "${UUID.randomUUID()}.jpg")
    }

    fun resolve(name: String): File? {
        if (!FileNames.isInternalImageName(name)) return null
        return File(dir, name).takeIf { it.isFile }
    }

    fun delete(name: String) {
        resolve(name)?.delete()
    }

    /**
     * Copies a picked image into the capture folder after validating size and that it
     * actually decodes as an image. Returns the capture name, or null if rejected.
     */
    suspend fun importFrom(uri: Uri): String? = withContext(Dispatchers.IO) {
        val file = newFile()
        try {
            BitmapIo.copyLimited(context.contentResolver, uri, file)
            if (BitmapIo.readBounds(file) == null) throw IOException("Not a decodable image")
            file.name
        } catch (e: IOException) {
            Log.w(TAG, "Import rejected: ${e.message}")
            file.delete()
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Import not permitted")
            file.delete()
            null
        }
    }

    /** Captures are disposable; clear leftovers from an interrupted session on start. */
    fun clear() {
        dir.deleteRecursively()
    }

    private companion object {
        const val TAG = "CaptureStore"
    }
}
