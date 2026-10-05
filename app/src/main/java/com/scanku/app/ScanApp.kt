package com.scanku.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.scanku.app.data.CaptureStore
import com.scanku.app.data.DocumentRepository
import com.scanku.app.data.db.AppDatabase
import com.scanku.app.export.ExportService
import com.scanku.app.imaging.OpenCv
import com.scanku.app.ocr.OcrEngine
import com.scanku.app.settings.SettingsRepository

class ScanApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        OpenCv.init()
        container = AppContainer(this)
        // Temp captures and shared exports are disposable; don't let sensitive copies accumulate.
        container.captures.clear()
        container.export.clear()
    }
}

/** Manual dependency container — the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    private val database: AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "scanku.db").build()

    val documents = DocumentRepository(context, database)
    val captures = CaptureStore(context)
    val settings = SettingsRepository(context)
    val export = ExportService(context, documents)
    val ocr by lazy { OcrEngine() }
}
