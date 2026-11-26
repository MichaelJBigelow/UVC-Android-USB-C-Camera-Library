package com.serenegiant.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class SizeTest {

    @Test
    fun toStringShowsTheSizeFrameRateAndType() {
        assertEquals(
            "Size(1280x720@30,type:7)",
            Size(UVCCamera.UVC_VS_FRAME_MJPEG, 1280, 720, 30, listOf(30, 15)).toString(),
        )
    }

    @Test
    fun cloneIsAnIndependentCopy() {
        val frameRates = mutableListOf(30, 15)
        val size = Size(UVCCamera.UVC_VS_FRAME_MJPEG, 1280, 720, 30, frameRates)

        val copy = size.clone()
        copy.width = 1920
        frameRates += 5

        assertNotSame(size, copy)
        assertEquals(1280, size.width)
        assertEquals(1920, copy.width)
        assertEquals(720, copy.height)
        assertEquals(30, copy.fps)
        assertEquals(UVCCamera.UVC_VS_FRAME_MJPEG, copy.type)
        assertEquals(listOf(30, 15), copy.fpsList)
    }

    @Test
    fun cloneKeepsAMissingFrameRateList() {
        // A Size read back from a Parcel has no frame rate list.
        // 从 Parcel 读回的 Size 没有帧率列表。
        val copy = Size(UVCCamera.UVC_VS_FRAME_UNCOMPRESSED, 640, 480, 15, null).clone()

        assertNull(copy.fpsList)
        assertEquals(640, copy.width)
    }
}
