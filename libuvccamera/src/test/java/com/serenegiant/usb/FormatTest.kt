package com.serenegiant.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    private fun mjpeg() = Format(
        1,
        UVCCamera.UVC_VS_FORMAT_MJPEG,
        mutableListOf(
            Format.Descriptor(
                1, UVCCamera.UVC_VS_FRAME_MJPEG, 1920, 1080, 30, 333_333,
                mutableListOf(Format.Interval(1, 333_333, 30), Format.Interval(2, 666_666, 15)),
            ),
        ),
    )

    @Test
    fun cloneCopiesDescriptorsAndIntervals() {
        val format = mjpeg()

        val copy = format.clone()

        assertNotSame(format, copy)
        assertEquals(1, copy.index)
        assertEquals(UVCCamera.UVC_VS_FORMAT_MJPEG, copy.type)
        assertNotSame(format.frameDescriptors, copy.frameDescriptors)
        val descriptor = copy.frameDescriptors.single()
        assertNotSame(format.frameDescriptors[0], descriptor)
        assertEquals(1920, descriptor.width)
        assertEquals(1080, descriptor.height)
        assertEquals(listOf(30, 15), descriptor.intervals.map { it.fps })
        assertNotSame(format.frameDescriptors[0].intervals[0], descriptor.intervals[0])
    }

    @Test
    fun changingTheCloneLeavesTheOriginalAlone() {
        val format = mjpeg()

        val copy = format.clone()
        copy.frameDescriptors[0].width = 640
        copy.frameDescriptors[0].intervals[0].fps = 60
        copy.frameDescriptors.clear()

        val descriptor = format.frameDescriptors.single()
        assertEquals(1920, descriptor.width)
        assertEquals(30, descriptor.intervals[0].fps)
    }

    @Test
    fun cloneTurnsMissingListsIntoEmptyOnes() {
        val format = Format(2, UVCCamera.UVC_VS_FORMAT_UNCOMPRESSED, null)
        assertTrue(format.clone().frameDescriptors.isEmpty())

        val descriptor = Format.Descriptor(1, UVCCamera.UVC_VS_FRAME_UNCOMPRESSED, 640, 480, 30, 333_333, null)
        assertTrue(descriptor.clone().intervals.isEmpty())
    }

    @Test
    fun descriptorToStringShowsTheSizeFrameRateTypeAndIndex() {
        assertEquals("Size(1920x1080@30,type:7,index:1)", mjpeg().frameDescriptors[0].toString())
    }
}
