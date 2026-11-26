package com.herohan.uvcapp

import android.os.Bundle
import androidx.annotation.IntRange
import com.herohan.uvcapp.IImageCapture.CaptureMode
import com.herohan.uvcapp.IImageCapture.CaptureStrategy

class ImageCaptureConfig internal constructor() : Cloneable {

    private var mMutableConfig = Bundle()

    /**
     * Sets the image capture strategy.
     *
     * Valid capture strategies are [IImageCapture.CAPTURE_STRATEGY_OPENGL_ES], which
     * implemented by OpenGLES, or [IImageCapture.CAPTURE_STRATEGY_IMAGE_READER],
     * which implemented by ImageReader.
     *
     * If not set, the capture strategy will default to
     * [IImageCapture.CAPTURE_STRATEGY_OPENGL_ES].
     *
     * 设置图像捕获策略。
     *
     * 有效的捕获策略为：由 OpenGLES 实现的 [IImageCapture.CAPTURE_STRATEGY_OPENGL_ES]，
     * 或由 ImageReader 实现的 [IImageCapture.CAPTURE_STRATEGY_IMAGE_READER]。
     *
     * 如果未设置，捕获策略默认为
     * [IImageCapture.CAPTURE_STRATEGY_OPENGL_ES]。
     *
     * @param strategy The requested image capture strategy. / 请求的图像捕获策略
     * @return The current Builder. / 当前的 Builder
     */
    fun setCaptureStrategy(@CaptureStrategy strategy: Int): ImageCaptureConfig {
        mMutableConfig.putInt(OPTION_CAPTURE_STRATEGY, strategy)
        return this
    }

    @get:CaptureStrategy
    val captureStrategy: Int
        get() = mMutableConfig.getInt(OPTION_CAPTURE_STRATEGY, DEFAULT_CAPTURE_STRATEGY)

    /**
     * Sets the image capture mode.
     *
     * Valid capture modes are [IImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY], which
     * prioritizes latency over image quality, or [IImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY],
     * which prioritizes image quality over latency.
     *
     * If not set, the capture mode will default to
     * [IImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY].
     *
     * 设置图像捕获模式。
     *
     * 有效的捕获模式为：[IImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY]（延迟优先于图像质量），
     * 或 [IImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY]（图像质量优先于延迟）。
     *
     * 如果未设置，捕获模式默认为
     * [IImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY]。
     *
     * @param mode The requested image capture mode. / 请求的图像捕获模式
     * @return The current Builder. / 当前的 Builder
     */
    fun setCaptureMode(@CaptureMode mode: Int): ImageCaptureConfig {
        mMutableConfig.putInt(OPTION_CAPTURE_MODE, mode)
        return this
    }

    @get:CaptureMode
    val captureMode: Int
        get() = mMutableConfig.getInt(OPTION_CAPTURE_MODE, DEFAULT_CAPTURE_MODE)

    /**
     * Sets the image jpeg compression quality.
     *
     * 设置图像的 jpeg 压缩质量。
     *
     * @param quality The requested image jpeg compression quality. / 请求的图像 jpeg 压缩质量
     * @return The current Builder. / 当前的 Builder
     */
    fun setJpegCompressionQuality(@IntRange(from = 1, to = 100) quality: Int): ImageCaptureConfig {
        mMutableConfig.putInt(OPTION_JPEG_COMPRESSION_QUALITY, quality)
        return this
    }

    val jpegCompressionQuality: Int
        get() = mMutableConfig.getInt(OPTION_JPEG_COMPRESSION_QUALITY, DEFAULT_JPEG_COMPRESSION_QUALITY)

    fun hasJpegCompressionQuality(): Boolean =
        mMutableConfig.containsKey(OPTION_JPEG_COMPRESSION_QUALITY)

    public override fun clone(): ImageCaptureConfig =
        (super.clone() as ImageCaptureConfig).also {
            it.mMutableConfig = mMutableConfig.clone() as Bundle
        }

    private companion object {
        const val OPTION_CAPTURE_STRATEGY = "imageCapture.captureStrategy"
        const val OPTION_CAPTURE_MODE = "imageCapture.captureMode"
        const val OPTION_JPEG_COMPRESSION_QUALITY = "imageCapture.jpegCompressionQuality"

        @CaptureStrategy
        const val DEFAULT_CAPTURE_STRATEGY = IImageCapture.CAPTURE_STRATEGY_OPENGL_ES

        @CaptureMode
        const val DEFAULT_CAPTURE_MODE = IImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        const val DEFAULT_JPEG_COMPRESSION_QUALITY = IImageCapture.JPEG_QUALITY_MINIMIZE_LATENCY_MODE
    }
}
