package com.herohan.uvcapp

import android.util.Log
import android.view.Surface
import com.serenegiant.opengl.renderer.RendererHolderCallback
import com.serenegiant.usb.Format
import com.serenegiant.usb.IButtonCallback
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.Size
import com.serenegiant.usb.USBMonitor.UsbControlBlock
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.UVCControl
import com.serenegiant.usb.UVCParam
import com.serenegiant.utils.UVCUtils
import com.serenegiant.uvccamera.BuildConfig
import com.serenegiant.uvccamera.R
import java.util.concurrent.CopyOnWriteArrayList

private val DEBUG = BuildConfig.DEBUG
private const val TAG = "CameraInternal"

private const val DEFAULT_WIDTH = 640
private const val DEFAULT_HEIGHT = 480

internal class CameraInternal(private val mCtrlBlock: UsbControlBlock) : ICameraInternal {

    private var mFrameWidth = DEFAULT_WIDTH
    private var mFrameHeight = DEFAULT_HEIGHT

    /**
     * Guards the native camera of this instance only, so cameras don't wait for each other.
     *
     * 仅保护本实例的 native 相机，使不同相机之间无需互相等待。
     */
    private val mSync = Any()

    private var mRendererHolder: ICameraRendererHolder?

    @Volatile
    private var mIsPreviewing = false

    /**
     * for accessing UVC camera
     *
     * 用于访问 UVC 相机
     */
    @Volatile
    private var mUVCCamera: UVCCamera? = null

    // Registered and called on different threads / 在不同线程上注册和调用
    private val mCallbacks: MutableList<ICameraInternal.StateCallback> = CopyOnWriteArrayList()

    @Volatile
    private var mImageCapture: IImageCapture? = null

    @Volatile
    private var mVideoCapture: VideoCapture? = null

    init {
        if (DEBUG) Log.d(TAG, "Constructor:")
        mRendererHolder = CameraRendererHolder(mFrameWidth, mFrameHeight, object : RendererHolderCallback {
            override fun onPrimarySurfaceCreate(surface: Surface) {
                // After primary surface has been created during previewing, invoking startPreview method again.
                // 预览过程中主 surface 创建完成后，再次调用 startPreview 方法。
                if (mIsPreviewing) {
                    startPreview()
                }
            }

            override fun onFrameAvailable() {
            }

            override fun onPrimarySurfaceDestroy() {
            }
        })
    }

    override fun registerCallback(callback: ICameraInternal.StateCallback) {
        if (DEBUG) Log.d(TAG, "registerCallback:")
        mCallbacks.add(callback)
    }

    override fun unregisterCallback(callback: ICameraInternal.StateCallback) {
        if (DEBUG) Log.d(TAG, "unregisterCallback:")
        mCallbacks.remove(callback)
    }

    override fun clearCallbacks() {
        if (DEBUG) Log.d(TAG, "clearCallbacks:")
        mCallbacks.clear()
    }

    override val supportedFormatList: List<Format>?
        get() = mUVCCamera?.supportedFormatList

    override val supportedSizeList: List<Size>?
        get() = mUVCCamera?.supportedSizeList

    override val previewSize: Size?
        get() = mUVCCamera?.previewSize

    /**
     * Changes the preview size. Refused while recording, since the encoders are sized to the
     * stream; if the camera rejects the size, the camera is closed.
     *
     * 更改预览尺寸。录像期间拒绝更改，因为编码器的尺寸与视频流一致；
     * 如果相机不接受该尺寸，则关闭相机。
     *
     * @throws IllegalStateException while recording / 录像期间
     */
    override fun setPreviewSize(size: Size) {
        if (DEBUG) Log.d(TAG, "setPreviewSize:$size")
        check(!isRecording) { "Can't change the preview size while recording" }
        try {
            synchronized(mSync) {
                val camera = mUVCCamera ?: return
                camera.setPreviewSize(size)
            }

            // The preview size may have changed: resize the captures that depend on it.
            // 预览尺寸可能已改变：调整依赖它的拍照和录像组件的尺寸。
            val previewSize = previewSize
            mVideoCapture?.setResolution(previewSize)
            (mImageCapture as? ImageCapture2)?.setResolution(previewSize)
        } catch (e: Exception) {
            Log.e(TAG, "setPreviewSize:", e)
            // unexpectedly #setPreviewSize failed: close the camera the usual way, so the app hears
            // about it and the captures are released
            // #setPreviewSize 意外失败：按常规方式关闭相机，使应用收到通知，并释放拍照和录像组件
            closeCamera()
        }
    }

    private fun updateRendererSize(width: Int, height: Int) {
        if (DEBUG) Log.d(TAG, "updateRendererSize:")
        mFrameWidth = width
        mFrameHeight = height
        mRendererHolder?.updatePrimarySize(width, height)
    }

    override fun addSurface(surface: Any, isRecordable: Boolean) {
        if (DEBUG) Log.d(TAG, "addSurface:surface=$surface")
        mRendererHolder?.addSlaveSurface(surface.hashCode(), surface, isRecordable)
    }

    override fun removeSurface(surface: Any) {
        if (DEBUG) Log.d(TAG, "removeSurface:surface=$surface")
        mRendererHolder?.removeSlaveSurface(surface.hashCode())
    }

    override fun setButtonCallback(callback: IButtonCallback?) {
        if (DEBUG) Log.d(TAG, "setButtonCallback:callback=$callback")
        try {
            mUVCCamera?.setButtonCallback(callback)
        } catch (e: Exception) {
            Log.e(TAG, "setButtonCallback:", e)
        }
    }

    override fun setFrameCallback(callback: IFrameCallback?, pixelFormat: Int) {
        if (DEBUG) Log.d(TAG, "setFrameCallback:surface=$callback")
        try {
            mUVCCamera?.setFrameCallback(callback, pixelFormat)
        } catch (e: Exception) {
            Log.e(TAG, "setFrameCallback:", e)
        }
    }

    override fun openCamera(
        param: UVCParam,
        previewConfig: CameraPreviewConfig,
        imageCaptureConfig: ImageCaptureConfig,
        videoCaptureConfig: VideoCaptureConfig,
    ) {
        if (DEBUG) Log.d(TAG, "openCamera:")
        if (!isCameraOpened) {
            openUVCCamera(param, previewConfig, imageCaptureConfig, videoCaptureConfig)
        } else {
            if (DEBUG) Log.d(TAG, "have already opened camera, just call callback")
            processOnCameraOpen()
        }
    }

    private fun resetUVCCamera() {
        if (DEBUG) Log.d(TAG, "resetUVCCamera: $this")
        stopRecording()
        synchronized(mSync) {
            mUVCCamera?.let {
                it.stopPreview()
                it.destroy(true)
            }
            mUVCCamera = null
            mRendererHolder?.removeSlaveSurfaceAll()
        }
    }

    private fun openUVCCamera(
        param: UVCParam,
        previewConfig: CameraPreviewConfig,
        imageCaptureConfig: ImageCaptureConfig,
        videoCaptureConfig: VideoCaptureConfig,
    ) {
        resetUVCCamera()
        if (DEBUG) Log.d(TAG, "openUVCCamera: $this")
        try {
            val camera: UVCCamera
            val result: Int
            synchronized(mSync) {
                camera = UVCCamera(param)
                mUVCCamera = camera
                result = camera.open(mCtrlBlock)
            }
            if (result != 0) {
                val context = UVCUtils.getApplication()
                throw if (result == UVCCamera.UVC_ERROR_BUSY) {
                    CameraException(
                        CameraException.CAMERA_OPEN_ERROR_BUSY,
                        context.getString(R.string.error_messge_camera_open_busy),
                    )
                } else {
                    CameraException(
                        CameraException.CAMERA_OPEN_ERROR_UNKNOWN,
                        context.getString(R.string.error_messge_camera_open_unknown),
                    )
                }
            }
            if (DEBUG) Log.i(TAG, "supportedSize:" + camera.supportedSize)

            setPreviewConfig(previewConfig)

            createImageCapture(imageCaptureConfig)
            mVideoCapture = VideoCapture(requireRendererHolder(), videoCaptureConfig, previewSize)

            processOnCameraOpen()
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "openUVCCamera:", e)
            // Undo the partial open, so isCameraOpened stays false and the native camera is freed
            // 撤销未完成的打开操作，使 isCameraOpened 保持为 false，并释放 native 相机
            releaseAfterOpenFailure()
            val ex = e as? CameraException
                ?: CameraException(CameraException.CAMERA_OPEN_ERROR_UNKNOWN, e)
            processOnError(ex)
        }
    }

    private fun releaseAfterOpenFailure() {
        synchronized(mSync) {
            mUVCCamera?.let {
                try {
                    it.destroy(true)
                } catch (e: Exception) {
                    Log.w(TAG, "releaseAfterOpenFailure:", e)
                }
            }
            mUVCCamera = null
        }
        releaseCaptures()
    }

    /**
     * Releases the picture and video captures, if any.
     *
     * 释放拍照和录像组件(如有)。
     */
    private fun releaseCaptures() {
        mImageCapture?.release()
        mImageCapture = null
        mVideoCapture?.release()
        mVideoCapture = null
    }

    private fun createImageCapture(config: ImageCaptureConfig) {
        mImageCapture?.release()
        mImageCapture = null
        mImageCapture = when (config.captureStrategy) {
            IImageCapture.CAPTURE_STRATEGY_IMAGE_READER -> ImageCapture2(this, config, previewSize)
            else -> ImageCapture(requireRendererHolder(), config)
        }
    }

    /**
     * The renderer, which exists until [release].
     *
     * 渲染器，在 [release] 之前一直存在。
     *
     * @throws IllegalStateException after [release] / 调用 [release] 之后
     */
    private fun requireRendererHolder(): ICameraRendererHolder =
        checkNotNull(mRendererHolder) { "Camera is released" }

    override fun closeCamera() {
        if (DEBUG) Log.d(TAG, "closeCamera: $this")
        stopRecording()
        var closed = false
        synchronized(mSync) {
            mUVCCamera?.let {
                it.stopPreview()
                it.destroy()
                closed = true
            }
            mUVCCamera = null
            mIsPreviewing = false
        }
        if (closed) {
            processOnCameraClose()
        }

        releaseCaptures()
    }

    override fun startPreview() {
        if (DEBUG) Log.d(TAG, "startPreview: $this")
        synchronized(mSync) {
            val camera = mUVCCamera ?: return
            val rendererHolder = mRendererHolder ?: return

            camera.previewSize?.let { updateRendererSize(it.width, it.height) }

            camera.setPreviewDisplay(rendererHolder.primarySurface)
            camera.startPreview()

            mIsPreviewing = true
        }
    }

    override fun stopPreview() {
        if (DEBUG) Log.d(TAG, "stopPreview: $this")
        synchronized(mSync) {
            mUVCCamera?.stopPreview()
            mIsPreviewing = false
        }
    }

    override val uvcControl: UVCControl?
        get() = mUVCCamera?.control

    /**
     * Captures a new still image and saves to a file.
     *
     * The callback will be called only once for every invocation of this method.
     *
     * 拍摄一张新的静态图片并保存到文件。每次调用此方法，回调只会被调用一次。
     *
     * @param options  Options to store the newly captured image. / 用于保存新拍摄图片的选项。
     * @param callback Callback to be called for the newly captured image. / 新拍摄图片时调用的回调。
     */
    override fun takePicture(
        options: IImageCapture.OutputFileOptions,
        callback: IImageCapture.OnImageCaptureCallback,
    ) {
        val imageCapture = mImageCapture
        if (isCameraOpened && imageCapture != null) {
            imageCapture.takePicture(options, callback)
        } else {
            val message = "Not bound to a Camera"
            callback.onError(IImageCapture.ERROR_INVALID_CAMERA, message, IllegalStateException(message))
        }
    }

    override val isRecording: Boolean
        get() = mVideoCapture?.isRecording == true

    override fun startRecording(
        options: VideoCapture.OutputFileOptions,
        callback: VideoCapture.OnVideoCaptureCallback,
    ) {
        val videoCapture = mVideoCapture
        if (isCameraOpened && videoCapture != null) {
            videoCapture.startRecording(options, callback)
        } else {
            val message = "Not bound to a Camera"
            callback.onError(VideoCapture.ERROR_INVALID_CAMERA, message, IllegalStateException(message))
        }
    }

    override fun stopRecording() {
        mVideoCapture?.stopRecording()
    }

    override val isCameraOpened: Boolean
        get() = mUVCCamera != null

    override fun release() {
        if (DEBUG) Log.d(TAG, "release:")
        if (isCameraOpened) {
            closeCamera()
        }
        releaseResource()
    }

    override fun setPreviewConfig(config: CameraPreviewConfig) {
        if (DEBUG) Log.d(TAG, "setCameraPreviewConfig:")

        val rotation = config.rotation
        if (DEBUG) Log.d(TAG, "rotateTo:$rotation")
        mRendererHolder?.rotateTo(rotation)

        val mirror = config.mirror
        if (DEBUG) Log.d(TAG, "setMirrorMode:$mirror")
        mRendererHolder?.setMirrorMode(mirror)
    }

    override fun setImageCaptureConfig(config: ImageCaptureConfig) {
        if (DEBUG) Log.d(TAG, "setImageCaptureConfig:")
        createImageCapture(config)
    }

    override fun setVideoCaptureConfig(config: VideoCaptureConfig) {
        if (DEBUG) Log.d(TAG, "setVideoCaptureConfig:")
        mVideoCapture?.setConfig(config)
    }

    private fun releaseResource() {
        if (DEBUG) Log.d(TAG, "releaseResource: $this")
        synchronized(mSync) {
            clearCallbacks()

            mRendererHolder?.release()
            mRendererHolder = null
        }
    }

    //********************************************************************************
    private fun processOnCameraOpen() {
        if (DEBUG) Log.d(TAG, "processOnCameraOpen:")
        for (callback in mCallbacks) {
            try {
                callback.onCameraOpen()
            } catch (e: Exception) {
                Log.w(TAG, "processOnCameraOpen:", e)
            }
        }
    }

    private fun processOnCameraClose() {
        if (DEBUG) Log.d(TAG, "processOnCameraClose:")
        for (callback in mCallbacks) {
            try {
                callback.onCameraClose()
            } catch (e: Exception) {
                Log.w(TAG, "processOnCameraClose:", e)
            }
        }
    }

    private fun processOnError(ex: CameraException) {
        if (DEBUG) Log.d(TAG, "processOnError:$ex")
        for (callback in mCallbacks) {
            try {
                callback.onError(ex)
            } catch (e: Exception) {
                Log.w(TAG, "processOnError:", e)
            }
        }
    }
}
