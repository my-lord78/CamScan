package com.scanku.app.imaging

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.scanku.app.core.util.ImageMath
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Safe bitmap decoding/encoding: bounded memory, EXIF-aware, atomic writes. */
object BitmapIo {
    private const val TAG = "BitmapIo"

    /** Imports larger than this are rejected before decoding. */
    const val MAX_IMPORT_BYTES: Long = 40L * 1024 * 1024

    /** Long-side cap for working images (≈ A4 at 270 dpi). Keeps peak heap well under 150 MB. */
    const val WORKING_MAX_DIM = 3200

    /** Reads only the header. Returns null when the file is not a decodable image (magic-byte check). */
    fun readBounds(file: File): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        return if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    }

    /**
     * Decodes [file] upright (EXIF orientation applied) with its long side ≤ [maxDim].
     * Returns null for non-images or when memory is insufficient.
     */
    fun decodeSampled(file: File, maxDim: Int): Bitmap? {
        val (w, h) = readBounds(file) ?: return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = ImageMath.inSampleSize(w, h, maxDim)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = try {
            BitmapFactory.decodeFile(file.path, opts)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Out of memory decoding image", e)
            null
        } ?: return null
        val oriented = applyExifOrientation(file, decoded)
        return scaleDown(oriented, maxDim)
    }

    fun scaleDown(src: Bitmap, maxDim: Int): Bitmap {
        val (tw, th) = ImageMath.scaledToMax(src.width, src.height, maxDim)
        if (tw == src.width && th == src.height) return src
        val scaled = Bitmap.createScaledBitmap(src, tw, th, true)
        if (scaled !== src) src.recycle()
        return scaled
    }

    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = try {
            ExifInterface(file.path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /** Rotates by a multiple of 90°. Returns [src] itself for 0°. */
    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return src
        val m = Matrix().apply { postRotate(d.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /** Writes a JPEG atomically (temp file + rename) so a crash never leaves a half-written page. */
    @Throws(IOException::class)
    fun saveJpeg(bitmap: Bitmap, target: File, quality: Int = 90) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                throw IOException("JPEG encoding failed")
            }
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("Could not move page into place")
        }
    }

    /** Copies a content:// stream to [dest], refusing anything larger than [maxBytes]. */
    @Throws(IOException::class)
    fun copyLimited(resolver: ContentResolver, uri: Uri, dest: File, maxBytes: Long = MAX_IMPORT_BYTES) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open input")
        input.use { ins ->
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw IOException("Import exceeds size limit")
                    out.write(buf, 0, n)
                }
            }
        }
    }
}
