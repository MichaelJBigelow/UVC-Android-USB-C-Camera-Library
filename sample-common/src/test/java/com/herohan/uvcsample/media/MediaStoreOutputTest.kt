package com.herohan.uvcsample.media

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class MediaStoreOutputTest {

    @Test
    fun fileName_isTheTimeAndExtension() {
        val time = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
        assertEquals("2026-01-02_03-04-05.jpg", MediaStoreOutput.fileName(time, ".jpg"))
    }

    @Test
    fun relativePath_putsTheAppFolderInTheDirectory() {
        assertEquals("Pictures/UVCApp/", MediaStoreOutput.relativePath("Pictures", "UVCApp"))
    }

    @Test
    fun relativePath_replacesCharactersThatCantBeInAFolderName() {
        assertEquals("Movies/a_b_c/", MediaStoreOutput.relativePath("Movies", "a/b:c"))
    }

    @Test
    fun relativePath_fallsBackWhenTheNameIsBlank() {
        assertEquals("Movies/UVC/", MediaStoreOutput.relativePath("Movies", "  "))
    }
}
