package com.scanku.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {

    @Test
    fun `sanitize replaces path separators and reserved characters`() {
        assertEquals("a_b_c_d_e_f_g_h_i", FileNames.sanitize("a/b\\c:d*e?f\"g<h>i"))
        assertEquals("x_y", FileNames.sanitize("x|y"))
    }

    @Test
    fun `sanitize neutralises traversal and hidden-file names`() {
        assertEquals("Dokumen", FileNames.sanitize(".."))
        assertEquals("_etc_passwd", FileNames.sanitize("../etc/passwd"))
        assertEquals("rahasia", FileNames.sanitize(".rahasia"))
    }

    @Test
    fun `sanitize strips control characters and collapses whitespace`() {
        assertEquals("Faktur Mei 2026", FileNames.sanitize("Faktur\n\t Mei   2026"))
        assertEquals("a_b", FileNames.sanitize("a\u0000b"))
    }

    @Test
    fun `sanitize falls back for blank names and caps length`() {
        assertEquals("Dokumen", FileNames.sanitize("   "))
        assertEquals(80, FileNames.sanitize("a".repeat(500)).length)
    }

    @Test
    fun `sanitize keeps ordinary unicode titles intact`() {
        assertEquals("Kontrak Sewa – Rumah №5", FileNames.sanitize("Kontrak Sewa – Rumah №5"))
    }

    @Test
    fun `normalizeTitle trims and rejects empty or over-long input`() {
        assertEquals("KTP Budi", FileNames.normalizeTitle("  KTP   Budi "))
        assertNull(FileNames.normalizeTitle("   "))
        assertNull(FileNames.normalizeTitle("x".repeat(81)))
    }

    @Test
    fun `isInternalImageName accepts only app-generated UUID jpg names`() {
        assertTrue(FileNames.isInternalImageName("3f2b9c1e-8a4d-4b7e-9c2a-1d3e5f7a9b0c.jpg"))
        assertFalse(FileNames.isInternalImageName("../3f2b9c1e-8a4d-4b7e-9c2a-1d3e5f7a9b0c.jpg"))
        assertFalse(FileNames.isInternalImageName("3f2b9c1e-8a4d-4b7e-9c2a-1d3e5f7a9b0c.jpg/../../x"))
        assertFalse(FileNames.isInternalImageName("photo.jpg"))
        assertFalse(FileNames.isInternalImageName(""))
    }

    @Test
    fun `escapeLike escapes LIKE wildcards and the escape character`() {
        assertEquals("100\\%", FileNames.escapeLike("100%"))
        assertEquals("a\\_b", FileNames.escapeLike("a_b"))
        assertEquals("c:\\\\x", FileNames.escapeLike("c:\\x"))
    }
}
