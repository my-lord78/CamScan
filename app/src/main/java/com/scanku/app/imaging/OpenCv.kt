package com.scanku.app.imaging

import android.util.Log
import org.opencv.android.OpenCVLoader

/** Loads OpenCV's native library once. Callers check [ready] and degrade gracefully when false. */
object OpenCv {
    private const val TAG = "OpenCv"

    @Volatile
    var ready: Boolean = false
        private set

    fun init() {
        if (ready) return
        ready = try {
            OpenCVLoader.initLocal()
        } catch (e: Throwable) {
            // UnsatisfiedLinkError on an unsupported ABI is an Error, not an Exception.
            Log.e(TAG, "OpenCV failed to load", e)
            false
        }
        Log.i(TAG, "OpenCV ready=$ready")
    }
}
