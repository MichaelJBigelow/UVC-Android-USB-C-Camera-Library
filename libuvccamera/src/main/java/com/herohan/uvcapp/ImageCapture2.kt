package com.herohan.uvcapp

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.herohan.uvcapp.IImageCapture.ImageCaptureError
import com.serenegiant.usb.Size
import java.lang.ref.WeakReference

/**
 * Takes pictures by attaching an [ImageReader] to the camera
 * ([IImageCapture.CAPTURE_STRATEGY_IMAGE_READER]).
 *
 * One reader is kept for the current preview size. It is attached to the camera only while
 * pictures are waiting, and each frame it receives answers the oldest waiting request.
 *
 * 通过将 [ImageReader] 挂接到相机来拍照([IImageCapture.CAPTURE_STRATEGY_IMAGE_READER])。
 *
 * 为当前预览尺寸保留一个 reader。仅在有等待中的拍照请求时才将其挂接到相机，
 * 它收到的每一帧都用于响应最早的等待请求。
 */
class ImageCapture2 internal constructor(
    cameraInternal: ICameraInternal,
    config: ImageCaptureConfig,
    resolution: Size?,
) : BaseImageCapture(config, TAG) {

    private val mCameraInternalWeak = WeakReference(cameraInternal)

    /**
     * Held while the reader is attached to or detached from the camera, which can wait for the
     * render thread, so a new picture can't slip in between deciding to detach and detaching.
     * Always taken before mSync.
     *
     * 在将 reader 挂接到相机或从相机移除时持有(这可能需要等待渲染线程)，
     * 使新的拍照请求无法插入到“决定移除”与“实际移除”之间。始终先于 mSync 获取。
     */
    private val mAttachLock = Any()

    /**
     * Guards the reader, the resolution and the waiting requests. Never held while waiting for
     * the render thread with an image open.
     *
     * 保护 reader、分辨率和等待中的请求。持有图像时不会在持有它的情况下等待渲染线程。
     */
    private val mSync = Any()
    private var mResolution: Size? = null
    private var mImageReader: ImageReader? = null

    /**
     * Whether the reader's surface is attached to the camera.
     *
     * reader 的 surface 是否已挂接到相机。
     */
    private var mReaderAttached = false
    private val mPendingCaptures = ArrayDeque<PendingCapture>()
    private var mReleased = false

    /**
     * Thread on which the reader reports new frames.
     *
     * reader 报告新帧所在的线程。
     */
    private val mReaderThread = HandlerThread(TAG + "_reader").apply { start() }
    private val mReaderHandler = Handler(mReaderThread.looper)

    /**
     * A picture that was asked for and has not been taken yet.
     *
     * 已请求但尚未拍摄的照片。
     */
    private class PendingCapture(
        val options: IImageCapture.OutputFileOptions,
        val callback: IImageCapture.OnImageCaptureCallback,
        val jpegQuality: Int,
    )

    init {
        synchronized(mSync) {
            mResolution = resolution
            createImageReader()
        }
    }

    /**
     * Creates the reader for the current resolution. The caller holds mSync.
     *
     * 为当前分辨率创建 reader。调用方需持有 mSync。
     */
    @SuppressLint("WrongConstant")
    private fun createImageReader() {
        val resolution = mResolution ?: return
        mImageReader = ImageReader.newInstance(resolution.width, resolution.height, PixelFormat.RGBA_8888, 2).also {
            it.setOnImageAvailableListener(::onImageAvailable, mReaderHandler)
        }
    }

    /**
     * Detaches the reader from the camera and closes it. The caller holds mSync.
     *
     * 将 reader 从相机上移除并关闭它。调用方需持有 mSync。
     */
    private fun closeImageReader() {
        val reader = mImageReader ?: return
        detachReader()
        reader.setOnImageAvailableListener(null, null)
        reader.close()
        mImageReader = null
    }

    /**
     * Stops sending camera frames to the reader. The caller holds mSync.
     *
     * 停止向 reader 发送相机帧。调用方需持有 mSync。
     */
    private fun detachReader() {
        val reader = mImageReader
        if (mReaderAttached && reader != null) {
            mCameraInternalWeak.get()?.removeSurface(reader.surface)
        }
        mReaderAttached = false
    }

    /**
     * Fails every waiting request. The caller holds mSync.
     *
     * 使所有等待中的请求失败。调用方需持有 mSync。
     */
    private fun failPendingCaptures(@ImageCaptureError error: Int, message: String) {
        while (true) {
            val capture = mPendingCaptures.removeFirstOrNull() ?: break
            postError(capture.callback, error, message, null)
        }
    }

    /**
     * Follows a new preview size: waiting pictures fail, since the reader is recreated at the new size.
     *
     * 跟随新的预览尺寸：等待中的照片会失败，因为 reader 会按新尺寸重新创建。
     */
    internal fun setResolution(resolution: Size?) {
        synchronized(mAttachLock) {
            synchronized(mSync) {
                if (mReleased) {
                    return
                }
                failPendingCaptures(
                    IImageCapture.ERROR_CAPTURE_FAILED,
                    "The preview size changed before the picture was taken",
                )
                closeImageReader()
                mResolution = resolution
                createImageReader()
            }
        }
    }

    /**
     * Captures a new still image and saves to a file.
     *
     * The callback will be called only once for every invocation of this method.
     *
     * 拍摄一张新的静态图像并保存到文件。
     *
     * 此方法每调用一次，回调只会被调用一次。
     *
     * @param options  Options to store the newly captured image. / 用于保存新拍摄图像的选项。
     * @param callback Callback to be called for the newly captured image. / 新图像拍摄完成后调用的回调。
     */
    override fun takePicture(
        options: IImageCapture.OutputFileOptions,
        callback: IImageCapture.OnImageCaptureCallback,
    ) {
        val cameraInternal = mCameraInternalWeak.get()
        if (cameraInternal == null) {
            postError(callback, IImageCapture.ERROR_INVALID_CAMERA, "Not bound to a Camera", null)
            return
        }
        synchronized(mAttachLock) {
            val surfaceToAttach: Surface?
            synchronized(mSync) {
                if (mReleased) {
                    postError(callback, IImageCapture.ERROR_CAMERA_CLOSED, "Camera is closed", null)
                    return
                }
                val reader = mImageReader
                if (reader == null) {
                    postError(callback, IImageCapture.ERROR_UNKNOWN, "ImageReader is null", null)
                    return
                }
                mPendingCaptures.addLast(PendingCapture(options, callback, getJpegQualityInternal()))
                surfaceToAttach = if (mReaderAttached) null else reader.surface
                mReaderAttached = true
            }
            // Attach outside mSync, so the reader thread can keep handling frames meanwhile.
            // 在 mSync 之外挂接，使 reader 线程在此期间仍可处理帧。
            if (surfaceToAttach != null) {
                cameraInternal.addSurface(surfaceToAttach, false)
            }
        }
    }

    /**
     * Answers the oldest waiting request with the newest frame. Runs on the reader thread.
     *
     * The frame is copied and the image closed while holding mSync, so the reader can't be
     * closed under it; the reader is detached afterwards, with no image open.
     *
     * 用最新的帧响应最早的等待请求。在 reader 线程上运行。
     *
     * 在持有 mSync 时复制帧并关闭图像，因此 reader 不会在使用期间被关闭；
     * 之后在没有打开图像的情况下移除 reader。
     */
    private fun onImageAvailable(reader: ImageReader) {
        val capture: PendingCapture?
        val idle: Boolean
        var bitmap: Bitmap? = null
        var width = 0
        var height = 0
        var failure: Exception? = null
        synchronized(mSync) {
            // A frame of a reader that was closed or replaced since
            // 来自之后已被关闭或替换的 reader 的帧
            if (reader !== mImageReader) {
                return
            }
            val image = try {
                reader.acquireLatestImage()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "onImageAvailable:", e)
                return
            } ?: return
            try {
                capture = mPendingCaptures.removeFirstOrNull()
                idle = mPendingCaptures.isEmpty()
                val resolution = mResolution
                if (capture != null && resolution != null) {
                    width = resolution.width
                    height = resolution.height
                    try {
                        bitmap = copyToBitmap(image, width)
                    } catch (e: Exception) {
                        failure = e
                    }
                }
            } finally {
                image.close()
            }
        }

        if (idle) {
            detachIfIdle(reader)
        }
        if (capture == null) {
            // A frame that was already on its way when the reader was detached
            // 移除 reader 时已在途中的帧
            return
        }
        val captured = bitmap
        if (captured == null) {
            val message = failure?.message ?: "captureImage failed with an unknown exception"
            postError(capture.callback, IImageCapture.ERROR_CAPTURE_FAILED, message, failure)
            return
        }
        try {
            mExecutor.execute(
                ImageSaver(captured, width, height, capture.jpegQuality, capture.options, wrapCallback(capture.callback))
            )
        } catch (e: Exception) {
            // The capture was released meanwhile / 拍照组件在此期间已被释放
            captured.recycle()
            postError(capture.callback, IImageCapture.ERROR_CAMERA_CLOSED, "Camera is closed", e)
        }
    }

    /**
     * Detaches [reader] from the camera if no picture is waiting any more.
     *
     * 如果已没有等待中的照片，则将 [reader] 从相机上移除。
     */
    private fun detachIfIdle(reader: ImageReader) {
        synchronized(mAttachLock) {
            synchronized(mSync) {
                if (reader !== mImageReader || mPendingCaptures.isNotEmpty()) {
                    return
                }
                detachReader()
            }
        }
    }

    override fun release() {
        synchronized(mAttachLock) {
            synchronized(mSync) {
                if (mReleased) {
                    return
                }
                mReleased = true
                failPendingCaptures(IImageCapture.ERROR_CAMERA_CLOSED, "Camera is closed")
                closeImageReader()
            }
        }
        mReaderThread.quitSafely()
        super.release()
    }

    private companion object {
        const val TAG = "ImageCapture2"

        /**
         * Copies an RGBA image into a new bitmap, keeping the row padding (ImageSaver crops it).
         *
         * 将 RGBA 图像复制到新位图中，保留行填充(由 ImageSaver 裁剪)。
         */
        fun copyToBitmap(image: Image, width: Int): Bitmap {
            val planes = image.planes
            check(planes.isNotEmpty()) { "The captured image has no data" }
            // Since we requested RGBA data, everything is stored in planes[0]
            // 因为我们要求的是RGBA格式的数据，所以全部的存储在planes[0]中
            val plane = planes[0]
            val buffer = plane.buffer
            // Because the buffer in the Image is data-aligned, its size is not necessarily the size specified when the ImageReader instance was created.
            // ImageReader automatically adds padding to the right side of each row for alignment, and the number of aligned bytes may vary by hardware,
            // so this padding data must be ignored when reading the data out.
            // 由于Image中的缓冲区存在数据对齐，所以其大小不一定是我们生成ImageReader实例时指定的大小，
            // ImageReader会自动为画面每一行最右侧添加一个padding，以进行对齐，对齐多少字节可能因硬件而异，
            // 所以我们在取出数据时需要忽略这一部分数据。
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            // create bitmap
            // 创建 bitmap
            val bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buffer)
            return bmp
        }
    }
}
