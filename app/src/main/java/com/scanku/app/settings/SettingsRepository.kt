package com.scanku.app.settings

import android.content.Context
import com.scanku.app.core.util.PdfPageSize
import com.scanku.app.imaging.ScanFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Appearance follows the system by default; the choice is a per-device preference. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val autoCapture: Boolean = true,
    val defaultFilter: ScanFilter = ScanFilter.MAGIC,
    val pdfPageSize: PdfPageSize = PdfPageSize.A4,
)

/** Tiny preference store (SharedPreferences) exposed as a StateFlow. */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<AppSettings> = state.asStateFlow()

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        prefs.edit()
            .putString(KEY_THEME, next.theme.name)
            .putBoolean(KEY_AUTO, next.autoCapture)
            .putString(KEY_FILTER, next.defaultFilter.name)
            .putString(KEY_PDF, next.pdfPageSize.name)
            .apply()
        state.value = next
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = enumOr(prefs.getString(KEY_THEME, null), d.theme),
            autoCapture = prefs.getBoolean(KEY_AUTO, d.autoCapture),
            defaultFilter = enumOr(prefs.getString(KEY_FILTER, null), d.defaultFilter),
            pdfPageSize = enumOr(prefs.getString(KEY_PDF, null), d.pdfPageSize),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_AUTO = "auto_capture"
        const val KEY_FILTER = "default_filter"
        const val KEY_PDF = "pdf_page_size"
    }
}
