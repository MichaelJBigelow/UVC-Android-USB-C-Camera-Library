package com.herohan.uvcapp.ui.dialog

import com.serenegiant.usb.Format
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFormatOptionsTest {

    private fun descriptor(type: Int, width: Int, height: Int, vararg fps: Int) =
        Format.Descriptor(0, type, width, height, fps.firstOrNull() ?: 0, 0, fps.map { Format.Interval(0, 0, it) })

    private val mjpeg = Format(
        1,
        UVCCamera.UVC_VS_FORMAT_MJPEG,
        listOf(
            descriptor(UVCCamera.UVC_VS_FRAME_MJPEG, 1920, 1080, 30, 15),
            descriptor(UVCCamera.UVC_VS_FRAME_MJPEG, 640, 480, 30, 25, 15),
        ),
    )
    private val yuv = Format(
        2,
        UVCCamera.UVC_VS_FORMAT_UNCOMPRESSED,
        listOf(descriptor(UVCCamera.UVC_VS_FRAME_UNCOMPRESSED, 640, 480, 10, 5)),
    )

    /** A frame-based format (e.g. H.264) the library can't preview. / 本库无法预览的基于帧的格式(例如 H.264)。 */
    private val unsupported = Format(3, 0x10, listOf(descriptor(0x11, 1280, 720, 30)))

    private val options = VideoFormatOptions(listOf(mjpeg, yuv, unsupported))

    @Test
    fun offersOnlyMjpegAndYuvInCameraOrder() {
        assertEquals(listOf(UVCCamera.UVC_VS_FRAME_MJPEG, UVCCamera.UVC_VS_FRAME_UNCOMPRESSED), options.frameTypes)
        assertEquals(
            listOf(Resolution(1920, 1080), Resolution(640, 480)),
            options.resolutions(UVCCamera.UVC_VS_FRAME_MJPEG),
        )
        assertEquals(listOf(30, 25, 15), options.frameRates(UVCCamera.UVC_VS_FRAME_MJPEG, Resolution(640, 480)))
    }

    @Test
    fun keepsSupportedSelection() {
        val size = Size(UVCCamera.UVC_VS_FRAME_MJPEG, 640, 480, 25, null)
        assertEquals(
            VideoFormatSelection(UVCCamera.UVC_VS_FRAME_MJPEG, Resolution(640, 480), 25),
            options.coerce(size),
        )
    }

    @Test
    fun switchingFormatKeepsResolutionAndFallsBackToFirstFrameRate() {
        assertEquals(
            VideoFormatSelection(UVCCamera.UVC_VS_FRAME_UNCOMPRESSED, Resolution(640, 480), 10),
            options.coerce(UVCCamera.UVC_VS_FRAME_UNCOMPRESSED, Resolution(640, 480), 30),
        )
    }

    @Test
    fun unsupportedResolutionFallsBackToFirstOne() {
        assertEquals(
            VideoFormatSelection(UVCCamera.UVC_VS_FRAME_UNCOMPRESSED, Resolution(640, 480), 10),
            options.coerce(UVCCamera.UVC_VS_FRAME_UNCOMPRESSED, Resolution(1920, 1080), 30),
        )
    }

    @Test
    fun unsupportedFrameTypeFallsBackToFirstOne() {
        assertEquals(
            VideoFormatSelection(UVCCamera.UVC_VS_FRAME_MJPEG, Resolution(1920, 1080), 30),
            options.coerce(0x11, Resolution(1280, 720), 30),
        )
    }

    @Test
    fun noUsableFormatIsEmpty() {
        assertTrue(VideoFormatOptions(listOf(unsupported)).isEmpty)
        assertTrue(VideoFormatOptions(emptyList()).isEmpty)
    }

    @Test
    fun resolutionPrintsAsWidthByHeight() {
        assertEquals("1920x1080", Resolution(1920, 1080).toString())
    }
}
