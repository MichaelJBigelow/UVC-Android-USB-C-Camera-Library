package com.herohan.uvcapp

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.IntDef
import java.io.File
import java.io.OutputStream

interface IImageCapture {

    fun setConfig(config: ImageCaptureConfig)

    fun takePicture(options: OutputFileOptions, callback: OnImageCaptureCallback)

    fun release()

    /**
     * Options for saving newly captured image.
     *
     * this class is used to configure save location and other options.
     * Save location can be either a [File], [MediaStore] or a [OutputStream].
     *
     * 用于保存新拍摄图像的选项。
     *
     * 此类用于配置保存位置及其他选项。
     * 保存位置可以是 [File]、[MediaStore] 或 [OutputStream]。
     */
    class OutputFileOptions internal constructor(
        val file: File?,
        val contentResolver: ContentResolver?,
        val saveCollection: Uri?,
        val contentValues: ContentValues?,
        val outputStream: OutputStream?,
    ) {

        class Builder {
            private var mFile: File? = null
            private var mContentResolver: ContentResolver? = null
            private var mSaveCollection: Uri? = null
            private var mContentValues: ContentValues? = null
            private var mOutputStream: OutputStream? = null

            /**
             * Creates options to write captured image to a [File].
             *
             * 创建将拍摄的图像写入 [File] 的选项。
             *
             * @param file save location of the image. / 图像的保存位置。
             */
            constructor(file: File?) {
                mFile = file
            }

            /**
             * Creates options to write captured image to [MediaStore].
             *
             * Example:
             *
             * 创建将拍摄的图像写入 [MediaStore] 的选项。
             *
             * 示例：
             *
             * ```
             * ContentValues contentValues = new ContentValues();
             * contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "NEW_IMAGE");
             * contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
             *
             * ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(
             *         getContentResolver(),
             *         MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
             *         contentValues).build();
             * ```
             *
             * @param contentResolver to access [MediaStore] / 用于访问 [MediaStore]
             * @param saveCollection  The URL of the table to insert into. / 要插入的表的 URL。
             * @param contentValues   to be included in the created image file. / 要包含在所创建图像文件中的内容值。
             */
            constructor(contentResolver: ContentResolver?, saveCollection: Uri?, contentValues: ContentValues?) {
                mContentResolver = contentResolver
                mSaveCollection = saveCollection
                mContentValues = contentValues
            }

            /**
             * Creates options that write captured image to a [OutputStream].
             *
             * 创建将拍摄的图像写入 [OutputStream] 的选项。
             *
             * @param outputStream save location of the image. / 图像的保存位置。
             */
            constructor(outputStream: OutputStream?) {
                mOutputStream = outputStream
            }

            /**
             * Builds [OutputFileOptions].
             *
             * 构建 [OutputFileOptions]。
             */
            fun build(): OutputFileOptions =
                OutputFileOptions(mFile, mContentResolver, mSaveCollection, mContentValues, mOutputStream)
        }
    }

    /**
     * Info about the saved image file.
     *
     * 已保存图像文件的相关信息。
     *
     * @property savedUri The [Uri] of the saved file. This field is only returned if the
     * [OutputFileOptions] is backed by [MediaStore] constructed with
     * [OutputFileOptions.Builder] (ContentResolver, Uri, ContentValues), or by a file. /
     * 已保存文件的 [Uri]。仅当 [OutputFileOptions] 由 [MediaStore] 支持，并且是通过
     * [OutputFileOptions.Builder] (ContentResolver, Uri, ContentValues) 构造时，或保存到文件时，
     * 才会返回此字段。
     */
    class OutputFileResults internal constructor(val savedUri: Uri?)

    /**
     * Describes the error that occurred during an image capture operation
     *
     * 描述图像拍摄操作期间发生的错误
     */
    @IntDef(
        ERROR_UNKNOWN, ERROR_FILE_IO, ERROR_CAPTURE_FAILED,
        ERROR_CAMERA_CLOSED, ERROR_INVALID_CAMERA,
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class ImageCaptureError

    /**
     * Capture strategy options for ImageCapture.
     *
     * ImageCapture 的拍摄策略选项。
     */
    @IntDef(CAPTURE_STRATEGY_OPENGL_ES, CAPTURE_STRATEGY_IMAGE_READER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class CaptureStrategy

    /**
     * Capture mode options for ImageCapture.
     *
     * ImageCapture 的拍摄模式选项。
     */
    @IntDef(CAPTURE_MODE_MAXIMIZE_QUALITY, CAPTURE_MODE_MINIMIZE_LATENCY)
    @Retention(AnnotationRetention.SOURCE)
    annotation class CaptureMode

    /**
     * Listener containing callbacks for image file I/O events.
     *
     * 包含图像文件 I/O 事件回调的监听器。
     */
    interface OnImageCaptureCallback {

        /**
         * Called when an image has been successfully saved.
         *
         * 在图像成功保存后调用。
         */
        fun onImageSaved(outputFileResults: OutputFileResults)

        /**
         * Called when an error occurs while attempting to save an image.
         *
         * 在尝试保存图像时发生错误时调用。
         */
        fun onError(@ImageCaptureError imageCaptureError: Int, message: String, cause: Throwable?)
    }

    companion object {
        /**
         * An unknown error occurred.
         *
         * See message parameter in onError callback or log for more details.
         *
         * 发生未知错误。
         *
         * 详情请参见 onError 回调中的 message 参数或日志。
         */
        const val ERROR_UNKNOWN = 0

        /**
         * An error occurred while attempting to read or write a file, such as when saving an image
         * to a File.
         *
         * 尝试读取或写入文件时发生错误，例如将图像保存到 File 时。
         */
        const val ERROR_FILE_IO = 1

        /**
         * An error reported by camera framework indicating the capture request is failed.
         *
         * 由相机框架报告的错误，表示拍摄请求失败。
         */
        const val ERROR_CAPTURE_FAILED = 2

        /**
         * An error indicating the request cannot be done due to camera is closed.
         *
         * 表示由于相机已关闭而无法完成该请求的错误。
         */
        const val ERROR_CAMERA_CLOSED = 3

        /**
         * An error indicating this ImageCapture is not bound to a valid camera.
         *
         * 表示此 ImageCapture 未绑定到有效相机的错误。
         */
        const val ERROR_INVALID_CAMERA = 4

        /**
         * Implementation based on OPENGL ES
         *
         * 基于 OPENGL ES 的实现
         */
        const val CAPTURE_STRATEGY_OPENGL_ES = 0

        /**
         * Implementation based on ImageReader
         *
         * 基于 ImageReader 的实现
         */
        const val CAPTURE_STRATEGY_IMAGE_READER = 1

        /**
         * Optimizes capture pipeline to prioritize image quality over latency. When the capture
         * mode is set to MAX_QUALITY, images may take longer to capture.
         *
         * 优化拍摄流水线，优先保证图像质量而非延迟。当拍摄模式设置为 MAX_QUALITY 时，
         * 图像的拍摄可能需要更长时间。
         */
        const val CAPTURE_MODE_MAXIMIZE_QUALITY = 0

        /**
         * Optimizes capture pipeline to prioritize latency over image quality. When the capture
         * mode is set to MIN_LATENCY, images may capture faster but the image quality may be
         * reduced.
         *
         * 优化拍摄流水线，优先保证延迟而非图像质量。当拍摄模式设置为 MIN_LATENCY 时，
         * 图像拍摄可能更快，但图像质量可能会降低。
         */
        const val CAPTURE_MODE_MINIMIZE_LATENCY = 1

        const val JPEG_QUALITY_MAXIMIZE_QUALITY_MODE = 100
        const val JPEG_QUALITY_MINIMIZE_LATENCY_MODE = 95
    }
}
