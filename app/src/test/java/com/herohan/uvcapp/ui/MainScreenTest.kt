package com.herohan.uvcapp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MainScreenTest {

    @Test
    fun formatsRecordTimeAsHoursMinutesSeconds() {
        assertEquals("00:00:00", formatRecordTime(0))
        assertEquals("00:01:05", formatRecordTime(65))
        assertEquals("01:00:00", formatRecordTime(3600))
        assertEquals("27:46:39", formatRecordTime(99_999))
    }

    @Test
    fun aspectRatioFallsBackToFourByThree() {
        assertEquals(16f / 9f, aspectRatioOf(1920, 1080), 0.0001f)
        assertEquals(4f / 3f, aspectRatioOf(0, 480), 0.0001f)
    }
}
