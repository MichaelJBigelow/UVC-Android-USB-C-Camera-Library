package com.herohan.uvcsample.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewSizeTest {

    @Test
    fun aspectRatioFallsBackToFourByThree() {
        assertEquals(16f / 9f, aspectRatioOf(1920, 1080), 0.0001f)
        assertEquals(4f / 3f, aspectRatioOf(0, 480), 0.0001f)
    }
}
