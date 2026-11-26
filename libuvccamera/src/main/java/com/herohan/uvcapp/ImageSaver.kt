package com.herohan.uvcapp

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.IntRange
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Encodes a captured picture as JPEG and saves it to the requested destination.
 *
 * 将拍摄的照片编码为 JPEG，并保存到指定的目标位置。
 */
class ImageSaver private constructor(
    // Encodes the image that was captured
    // 编码捕获到的图像
    private val mEncoder: JpegEncoder,
    // The compression quality level of the output JPEG image
    // 输出 JPEG 图像的压缩质量等级
    @param:IntRange(from = 1, to = 100) private val mJpegQuality: Int,
    // The target location to save the image to.
    // 图像的保存目标位置。
    private val mOutputFileOptions: IImageCapture.OutputFileOptions,
    // The callback to call on completion
    // 完成时调用的回调
    private val mCallback: OnImageSavedCallback,
) : Runnable {

    /**
     * Encodes the captured picture as JPEG at the given quality.
     *
     * 以给定的质量将拍摄的照片编码为 JPEG。
     */
    private fun interface JpegEncoder {
        @Throws(Exception::class)
        fun encode(jpegQuality: Int): ByteArray
    }

    /**
     * Saves a picture read back from the renderer, as RGBA pixels.
     *
     * 保存从渲染器读回的照片(RGBA 像素)。
     */
    internal constructor(
        image: ImageRawData,
        @IntRange(from = 1, to = 100) jpegQuality: Int,
        outputFileOptions: IImageCapture.OutputFileOptions,
        callback: OnImageSavedCallback,
    ) : this(JpegEncoder { quality -> rawDataToJpeg(image, quality) }, jpegQuality, outputFileOptions, callback)

    /**
     * Saves the top-left [width] x [height] pixels of a bitmap read from an ImageReader, whose rows
     * may be padded. The bitmap is recycled once it is encoded.
     *
     * 保存从 ImageReader 读取的位图左上角 [width] x [height] 的像素(其每行可能带有填充)。
     * 位图在编码后会被回收。
     */
    constructor(
        image: Bitmap,
        width: Int,
        height: Int,
        @IntRange(from = 1, to = 100) jpegQuality: Int,
        outputFileOptions: IImageCapture.OutputFileOptions,
        callback: OnImageSavedCallback,
    ) : this(
        JpegEncoder { quality -> bitmapToJpeg(image, width, height, quality) },
        jpegQuality,
        outputFileOptions,
        callback,
    )

    override fun run() {
        var saveError: SaveError? = null
        var errorMessage: String? = null
        var exception: Exception? = null
        var outputUri: Uri? = null

        try {
            val data = encodeJpeg()
            if (data == null) {
                saveError = SaveError.ENCODE_FAILED
                errorMessage = "Failed to encode mImage"
            } else if (isSaveToMediaStore()) {
                val resolver = mOutputFileOptions.contentResolver!!
                val values = mOutputFileOptions.contentValues?.let { ContentValues(it) } ?: ContentValues()
                setContentValuePending(values, PENDING)
                outputUri = resolver.insert(mOutputFileOptions.saveCollection!!, values)
                if (outputUri == null) {
                    saveError = SaveError.FILE_IO_FAILED
                    errorMessage = "Failed to insert URI."
                } else {
                    // Only true once the image is written and published
                    // 仅在图片写入并发布之后才为 true
                    var saved = false
                    try {
                        if (copyByteArrayToUri(data, outputUri)) {
                            setUriNotPending(outputUri)
                            saved = true
                        } else {
                            saveError = SaveError.FILE_IO_FAILED
                            errorMessage = "Failed to save to URI."
                        }
                    } finally {
                        if (!saved) {
                            // Delete the pending entry instead of publishing an empty or broken image
                            // 删除待处理的条目，而不是发布一张空白或损坏的图片
                            deleteUri(outputUri)
                        }
                    }
                }
            } else if (isSaveToOutputStream()) {
                mOutputFileOptions.outputStream!!.write(data)
            } else if (isSaveToFile()) {
                val targetFile = mOutputFileOptions.file!!
                copyByteArrayToFile(data, targetFile)
                outputUri = Uri.fromFile(targetFile)
            }
        } catch (e: IOException) {
            saveError = SaveError.FILE_IO_FAILED
            errorMessage = "Failed to write destination file."
            exception = e
        } catch (e: RuntimeException) {
            // e.g. SecurityException or IllegalArgumentException from ContentResolver
            // 例如 ContentResolver 抛出的 SecurityException 或 IllegalArgumentException
            saveError = SaveError.UNKNOWN
            errorMessage = "Failed to save image."
            exception = e
        }

        if (saveError != null) {
            mCallback.onError(saveError, errorMessage!!, exception)
        } else {
            mCallback.onImageSaved(IImageCapture.OutputFileResults(outputUri))
        }
    }

    /**
     * @return the JPEG data, or null if encoding failed / JPEG 数据；编码失败时返回 null
     */
    private fun encodeJpeg(): ByteArray? =
        try {
            mEncoder.encode(mJpegQuality)
        } catch (e: Exception) {
            Log.e(TAG, "failed to save file", e)
            null
        }

    private fun isSaveToMediaStore(): Boolean =
        mOutputFileOptions.saveCollection != null &&
            mOutputFileOptions.contentResolver != null &&
            mOutputFileOptions.contentValues != null

    private fun isSaveToFile(): Boolean = mOutputFileOptions.file != null

    private fun isSaveToOutputStream(): Boolean = mOutputFileOptions.outputStream != null

    /**
     * Set IS_PENDING flag to [ContentValues].
     *
     * 为 [ContentValues] 设置 IS_PENDING 标志。
     */
    private fun setContentValuePending(values: ContentValues, isPending: Int) {
        values.put(MediaStore.Images.Media.IS_PENDING, isPending)
    }

    /**
     * Removes IS_PENDING flag during the writing to [Uri].
     *
     * 在写入 [Uri] 的过程中移除 IS_PENDING 标志。
     */
    private fun setUriNotPending(outputUri: Uri) {
        val values = ContentValues()
        setContentValuePending(values, NOT_PENDING)
        mOutputFileOptions.contentResolver!!.update(outputUri, values, null, null)
    }

    private fun deleteUri(outputUri: Uri) {
        try {
            mOutputFileOptions.contentResolver!!.delete(outputUri, null, null)
        } catch (e: RuntimeException) {
            Log.w(TAG, "failed to delete $outputUri", e)
        }
    }

    /**
     * Copies  byte array to [Uri].
     *
     * 将字节数组复制到 [Uri]。
     *
     * @return false if the [Uri] is not writable. / 如果 [Uri] 不可写则返回 false。
     */
    @Throws(IOException::class)
    private fun copyByteArrayToUri(data: ByteArray, uri: Uri): Boolean {
        // A null stream means the URI is not writable.
        // 流为 null 表示该 URI 不可写。
        val outputStream = mOutputFileOptions.contentResolver!!.openOutputStream(uri) ?: return false
        outputStream.use { it.write(data) }
        return true
    }

    /**
     * Copies  byte array to [File].
     *
     * 将字节数组复制到 [File]。
     */
    @Throws(IOException::class)
    private fun copyByteArrayToFile(data: ByteArray, file: File) {
        FileOutputStream(file).use { it.write(data) }
    }

    /**
     * Type of error that occurred during save
     *
     * 保存过程中发生的错误类型
     */
    enum class SaveError {
        /**
         * Failed to write to or close the file
         *
         * 写入或关闭文件失败
         */
        FILE_IO_FAILED,

        /**
         * Failure when attempting to encode image
         *
         * 尝试编码图像时失败
         */
        ENCODE_FAILED,
        UNKNOWN,
    }

    interface OnImageSavedCallback {

        fun onImageSaved(outputFileResults: IImageCapture.OutputFileResults)

        fun onError(saveError: SaveError, message: String, cause: Throwable?)
    }

    private companion object {
        const val TAG = "ImageSaver"

        const val PENDING = 1
        const val NOT_PENDING = 0

        @Throws(IOException::class)
        fun rawDataToJpeg(image: ImageRawData, jpegQuality: Int): ByteArray =
            ByteArrayOutputStream().use { out ->
                val bmp = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                try {
                    bmp.copyPixelsFromBuffer(ByteBuffer.wrap(image.data))
                    bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
                } finally {
                    bmp.recycle()
                }
                out.toByteArray()
            }

        @Throws(IOException::class)
        fun bitmapToJpeg(image: Bitmap, width: Int, height: Int, jpegQuality: Int): ByteArray {
            try {
                val cropped = Bitmap.createBitmap(image, 0, 0, width, height)
                try {
                    return ByteArrayOutputStream().use { out ->
                        cropped.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
                        out.toByteArray()
                    }
                } finally {
                    if (cropped !== image) {
                        cropped.recycle()
                    }
                }
            } finally {
                image.recycle()
            }
        }
    }
}
