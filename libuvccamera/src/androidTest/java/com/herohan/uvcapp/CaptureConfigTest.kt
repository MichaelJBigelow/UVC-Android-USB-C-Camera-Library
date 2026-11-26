package com.herohan.uvcapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.serenegiant.opengl.renderer.MirrorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the defaults and setters of the preview, picture and video settings. They are kept in a
 * [android.os.Bundle], which only works on a device.
 *
 * 检查预览、拍照和录像设置的默认值与 setter。它们保存在 [android.os.Bundle] 中，只能在设备上运行。
 */
@RunWith(AndroidJUnit4::class)
class CaptureConfigTest {

    @Test
    fun previewConfig_defaultsToNoRotationOrMirror() {
        val config = CameraPreviewConfig()

        assertEquals(CameraPreviewConfig.DEFAULT_ROTATION, config.rotation)
        assertEquals(MirrorMode.MIRROR_NORMAL, config.mirror)
    }

    @Test
    fun previewConfig_acceptsQuarterTurns() {
        for (rotation in listOf(0, 90, 180, 270)) {
            assertEquals(rotation, CameraPreviewConfig().setRotation(rotation).rotation)
        }
    }

    @Test
    fun previewConfig_rejectsOtherRotations() {
        for (rotation in listOf(-90, 45, 360)) {
            assertThrows(IllegalArgumentException::class.java) { CameraPreviewConfig().setRotation(rotation) }
        }
    }

    @Test
    fun previewConfig_cloneIsIndependent() {
        val config = CameraPreviewConfig().setRotation(90).setMirror(MirrorMode.MIRROR_HORIZONTAL)

        val copy = config.clone().setRotation(180).setMirror(MirrorMode.MIRROR_BOTH)

        assertEquals(90, config.rotation)
        assertEquals(MirrorMode.MIRROR_HORIZONTAL, config.mirror)
        assertEquals(180, copy.rotation)
        assertEquals(MirrorMode.MIRROR_BOTH, copy.mirror)
    }

    @Test
    fun imageCaptureConfig_defaultsToOpenGlLowLatency() {
        val config = ImageCaptureConfig()

        assertEquals(IImageCapture.CAPTURE_STRATEGY_OPENGL_ES, config.captureStrategy)
        assertEquals(IImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY, config.captureMode)
        assertEquals(IImageCapture.JPEG_QUALITY_MINIMIZE_LATENCY_MODE, config.jpegCompressionQuality)
        assertFalse(config.hasJpegCompressionQuality())
    }

    @Test
    fun imageCaptureConfig_keepsWhatIsSet() {
        val config = ImageCaptureConfig()
            .setCaptureStrategy(IImageCapture.CAPTURE_STRATEGY_IMAGE_READER)
            .setCaptureMode(IImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegCompressionQuality(80)

        assertEquals(IImageCapture.CAPTURE_STRATEGY_IMAGE_READER, config.captureStrategy)
        assertEquals(IImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY, config.captureMode)
        assertEquals(80, config.jpegCompressionQuality)
        assertTrue(config.hasJpegCompressionQuality())
    }

    @Test
    fun imageCaptureConfig_cloneIsIndependent() {
        val config = ImageCaptureConfig()

        val copy = config.clone().setJpegCompressionQuality(50)

        assertFalse(config.hasJpegCompressionQuality())
        assertEquals(50, copy.jpegCompressionQuality)
    }

    @Test
    fun videoCaptureConfig_defaults() {
        val config = VideoCaptureConfig()

        assertEquals(30, config.videoFrameRate)
        assertEquals(8 * 1024 * 1024, config.bitRate)
        assertFalse(config.hasBitRate())
        assertEquals(1, config.iFrameInterval)
        assertTrue(config.audioCaptureEnable)
        assertEquals(64000, config.audioBitRate)
        assertEquals(8000, config.audioSampleRate)
        assertEquals(1, config.audioChannelCount)
        assertEquals(1024, config.audioMinBufferSize)
    }

    @Test
    fun videoCaptureConfig_keepsWhatIsSet() {
        val config = VideoCaptureConfig()
            .setVideoFrameRate(15)
            .setBitRate(2_000_000)
            .setIFrameInterval(2)
            .setAudioCaptureEnable(false)
            .setAudioBitRate(128_000)
            .setAudioSampleRate(44_100)
            .setAudioChannelCount(2)
            .setAudioMinBufferSize(4096)

        assertEquals(15, config.videoFrameRate)
        assertEquals(2_000_000, config.bitRate)
        assertTrue(config.hasBitRate())
        assertEquals(2, config.iFrameInterval)
        assertFalse(config.audioCaptureEnable)
        assertEquals(128_000, config.audioBitRate)
        assertEquals(44_100, config.audioSampleRate)
        assertEquals(2, config.audioChannelCount)
        assertEquals(4096, config.audioMinBufferSize)
    }

    @Test
    fun videoCaptureConfig_cloneIsIndependent() {
        val config = VideoCaptureConfig()

        val copy = config.clone().setBitRate(1_000_000).setAudioCaptureEnable(false)

        assertFalse(config.hasBitRate())
        assertTrue(config.audioCaptureEnable)
        assertEquals(1_000_000, copy.bitRate)
        assertFalse(copy.audioCaptureEnable)
    }
}
