package com.herohan.uvcapp

import android.os.Handler
import android.os.Looper
import androidx.annotation.IntRange
import com.herohan.uvcapp.IImageCapture.ImageCaptureError
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Code shared by the two capture strategies, [ImageCapture] and [ImageCapture2]: the
 * configuration and JPEG quality, the executor that saves the pictures, and the callback that
 * reports the result on the main thread.
 *
 * Sealed, since only these two strategies may extend it.
 *
 * 两种拍照策略 [ImageCapture] 和 [ImageCapture2] 共用的代码：
 * 配置和 JPEG 质量、保存照片的执行器，以及在主线程上报告结果的回调。
 *
 * 该类为 sealed，因为只有这两种策略可以继承它。
 */
sealed class BaseImageCapture(config: ImageCaptureConfig, threadNamePrefix: String) : IImageCapture {

    @Volatile
    private var mConfig: ImageCaptureConfig = config.clone()

    protected val mMainHandler = Handler(Looper.getMainLooper())

    protected val mExecutor: ExecutorService = AtomicInteger(0).let { threadId ->
        Executors.newFixedThreadPool(1) { r ->
            Thread(r, threadNamePrefix + "image_capture" + threadId.getAndIncrement())
        }
    }

    override fun setConfig(config: ImageCaptureConfig) {
        mConfig = config.clone()
    }

    /**
     * Gets the JPEG quality.
     *
     * Range is 1-100; larger is higher quality.
     *
     * 获取 JPEG 质量。
     *
     * 范围为 1-100;值越大质量越高。
     *
     * @return Compression quality of the captured JPEG image. / 所拍摄 JPEG 图像的压缩质量。
     */
    @IntRange(from = 1, to = 100)
    internal fun getJpegQualityInternal(): Int {
        val config = mConfig
        if (config.hasJpegCompressionQuality()) {
            return config.jpegCompressionQuality
        }

        return when (config.captureMode) {
            IImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY -> IImageCapture.JPEG_QUALITY_MAXIMIZE_QUALITY_MODE
            IImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY -> IImageCapture.JPEG_QUALITY_MINIMIZE_LATENCY_MODE
            else -> throw IllegalStateException("CaptureMode " + config.captureMode + " is invalid")
        }
    }

    /**
     * Converts the [ImageSaver.OnImageSavedCallback] to the caller's
     * [IImageCapture.OnImageCaptureCallback]: scans the saved file and reports the result on
     * the main thread.
     *
     * 将 [ImageSaver.OnImageSavedCallback] 转换为调用方的
     * [IImageCapture.OnImageCaptureCallback]：扫描已保存的文件，并在主线程上报告结果。
     */
    internal fun wrapCallback(imageSavedCallback: IImageCapture.OnImageCaptureCallback): ImageSaver.OnImageSavedCallback =
        object : ImageSaver.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: IImageCapture.OutputFileResults) {
                MediaScanHelper.scanMediaFile(outputFileResults.savedUri)

                mMainHandler.post { imageSavedCallback.onImageSaved(outputFileResults) }
            }

            override fun onError(saveError: ImageSaver.SaveError, message: String, cause: Throwable?) {
                mMainHandler.post {
                    // Every other save error stays ERROR_UNKNOWN
                    // 其他保存错误都保持为 ERROR_UNKNOWN
                    @ImageCaptureError val imageCaptureError = when (saveError) {
                        ImageSaver.SaveError.FILE_IO_FAILED -> IImageCapture.ERROR_FILE_IO
                        else -> IImageCapture.ERROR_UNKNOWN
                    }

                    imageSavedCallback.onError(imageCaptureError, message, cause)
                }
            }
        }

    /**
     * Reports an error to the caller on the main thread, like every other capture result.
     *
     * 与其他拍照结果一样，在主线程上向调用方报告错误。
     */
    internal fun postError(
        callback: IImageCapture.OnImageCaptureCallback,
        @ImageCaptureError error: Int,
        message: String,
        cause: Throwable?,
    ) {
        mMainHandler.post { callback.onError(error, message, cause) }
    }

    override fun release() {
        mExecutor.shutdown()
    }
}
