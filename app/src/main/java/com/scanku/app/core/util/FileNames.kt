package com.scanku.app.core.util

/** Turns user-entered document names into safe file names and validates internal file tokens. */
object FileNames {
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val WHITESPACE = Regex("\\s+")

    /** Names the app itself generates for captures/pages: a UUID plus ".jpg". Nothing else is accepted. */
    private val INTERNAL_IMAGE = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.jpg")

    const val MAX_NAME_LENGTH = 80

    /**
     * Strips path separators, reserved characters and control characters, collapses whitespace,
     * removes leading/trailing dots (no hidden files, no "..") and caps the length.
     */
    fun sanitize(name: String, fallback: String = "Dokumen", maxLength: Int = MAX_NAME_LENGTH): String {
        val cleaned = name
            .replace(WHITESPACE, " ") // newlines/tabs become spaces before control chars are replaced
            .replace(ILLEGAL, "_")
            .trim()
            .trim('.')
            .trim()
        return cleaned.take(maxLength).trim().ifEmpty { fallback }
    }

    /** Validates a document title typed by the user (rename dialog). */
    fun normalizeTitle(input: String): String? {
        val t = input.replace(WHITESPACE, " ").trim()
        return t.takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }
    }

    fun isInternalImageName(name: String): Boolean = INTERNAL_IMAGE.matches(name)

    /** Escapes `%`, `_` and the escape char itself for a SQL `LIKE ... ESCAPE '\'` pattern. */
    fun escapeLike(query: String): String =
        query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
