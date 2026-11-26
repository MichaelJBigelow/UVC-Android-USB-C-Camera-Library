package com.herohan.uvcapp

import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.serenegiant.usb.Format
import com.serenegiant.usb.IButtonCallback
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCControl
import com.serenegiant.usb.UVCParam
import com.serenegiant.uvccamera.BuildConfig
import java.util.WeakHashMap

private val DEBUG = BuildConfig.DEBUG
private const val TAG = "CameraHelper"

open class CameraHelper : ICameraHelper {

    private val mAsyncHandlerThread = HandlerThread(TAG).apply { start() }
    private val mAsyncHandler = Handler(mAsyncHandlerThread.looper)

    private val mMainHandler = Handler(Looper.getMainLooper())

    // Set on the helper thread and read on the caller's thread / 在辅助线程上设置，在调用方线程上读取
    @Volatile
    private var mService: ICameraConnection? = CameraConnectionService.newConnection()

    @Volatile
    private var mCallbackWrapper: ICameraHelper.StateCallback? = null

    @Volatile
    private var mUsbDevice: UsbDevice? = null
    private val mDetachedDeviceMap = WeakHashMap<UsbDevice, Any?>()

    private var mCameraPreviewConfig = CameraPreviewConfig()
    private var mImageCaptureConfig = ImageCaptureConfig()
    private var mVideoCaptureConfig = VideoCaptureConfig()

    init {
        if (DEBUG) Log.d(TAG, "Constructor:")
    }

    override fun setStateCallback(callback: ICameraHelper.StateCallback?) {
        if (callback != null) {
            mCallbackWrapper = StateCallbackWrapper(callback)
            registerCallback()
        } else {
            unregisterCallback()
            mCallbackWrapper = null
        }
    }

    override val deviceList: List<UsbDevice>?
        get() {
            if (DEBUG) Log.d(TAG, "getDeviceList:")
            return callService("getDeviceList") { it.deviceList }
        }

    private fun isDetached(usbDevice: UsbDevice): Boolean =
        synchronized(mDetachedDeviceMap) { mDetachedDeviceMap.containsKey(usbDevice) }

    override fun selectDevice(device: UsbDevice?) {
        if (DEBUG) Log.d(TAG, "selectDevice:device=" + device?.deviceName + " " + this)
        if (device == null) {
            Log.w(TAG, "selectDevice: ignoring a null device")
            return
        }
        mAsyncHandler.post {
            val service = mService
            if (service != null && !isDetached(device)) {
                mUsbDevice = device
                try {
                    service.selectDevice(device)
                } catch (e: Exception) {
                    if (DEBUG) Log.e(TAG, "selectDevice:", e)
                }
            }
        }
    }

    override val supportedFormatList: List<Format>?
        get() {
            if (DEBUG) Log.d(TAG, "getSupportedFormatList:")
            return callCamera("getSupportedFormatList") { service, device -> service.getSupportedFormatList(device) }
        }

    override val supportedSizeList: List<Size>?
        get() {
            if (DEBUG) Log.d(TAG, "getSupportedSizeList:")
            return callCamera("getSupportedSizeList") { service, device -> service.getSupportedSizeList(device) }
        }

    override var previewSize: Size?
        get() {
            if (DEBUG) Log.d(TAG, "getPreviewSize:")
            return callCamera("getPreviewSize") { service, device -> service.getPreviewSize(device) }
        }
        set(size) {
            if (DEBUG) Log.d(TAG, "setPreviewSize:$size")
            if (size == null) {
                Log.w(TAG, "setPreviewSize: ignoring a null size")
                return
            }
            postToCamera("setPreviewSize") { service, device -> service.setPreviewSize(device, size) }
        }

    override fun addSurface(surface: Any, isRecordable: Boolean) {
        if (DEBUG) Log.d(TAG, "addSurface:surface=$surface,isRecordable=$isRecordable")
        checkSurfaceType(surface)
        mAsyncHandler.post {
            val sur = fetchSurface(surface)
            if (sur == null) {
                Log.w(TAG, "addSurface: surface is not ready")
            } else {
                callCamera("addSurface") { service, device -> service.addSurface(device, sur, isRecordable) }
            }
        }
    }

    override fun removeSurface(surface: Any) {
        removeSurface(surface, null)
    }

    override fun removeSurface(surface: Any, onRemoved: Runnable?) {
        if (DEBUG) Log.d(TAG, "removeSurface:surface=$surface")
        checkSurfaceType(surface)
        val posted = mAsyncHandler.post {
            val sur = fetchSurface(surface)
            if (sur != null) {
                callCamera("handleRemoveSurface") { service, device -> service.removeSurface(device, sur) }
            }
            if (onRemoved != null) {
                mMainHandler.post(onRemoved)
            }
        }
        // Released helper: nothing draws on the surface any more
        // helper 已释放：不再有任何内容绘制到该 surface 上
        if (!posted && onRemoved != null) {
            mMainHandler.post(onRemoved)
        }
    }

    override fun setButtonCallback(callback: IButtonCallback?) {
        if (DEBUG) Log.d(TAG, "setButtonCallback:$callback")
        postToCamera("setButtonCallback") { service, device -> service.setButtonCallback(device, callback) }
    }

    override fun setFrameCallback(callback: IFrameCallback?, pixelFormat: Int) {
        if (DEBUG) Log.d(TAG, "setFrameCallback:$pixelFormat")
        postToCamera("setFrameCallback") { service, device ->
            service.setFrameCallback(device, callback, pixelFormat)
        }
    }

    override fun openCamera() {
        openCamera(UVCParam())
    }

    override fun openCamera(size: Size?) {
        openCamera(UVCParam(size, 0))
    }

    override fun openCamera(param: UVCParam) {
        if (DEBUG) Log.d(TAG, "openCamera: $this")
        mAsyncHandler.post {
            val service = mService
            val device = mUsbDevice
            if (service != null && device != null && !isDetached(device)) {
                try {
                    if (!service.isCameraOpened(device)) {
                        service.openCamera(
                            device, param,
                            mCameraPreviewConfig,
                            mImageCaptureConfig,
                            mVideoCaptureConfig,
                        )
                    }
                } catch (e: Exception) {
                    if (DEBUG) Log.e(TAG, "openCamera:", e)
                }
            }
        }
    }

    override fun closeCamera() {
        if (DEBUG) Log.d(TAG, "closeCamera: $this")
        postToCamera("closeCamera") { service, device ->
            if (service.isCameraOpened(device)) {
                service.closeCamera(device)
            }
        }
    }

    override fun startPreview() {
        if (DEBUG) Log.d(TAG, "startPreview: $this")
        postToCamera("startPreview") { service, device -> service.startPreview(device) }
    }

    override fun stopPreview() {
        if (DEBUG) Log.d(TAG, "stopPreview: $this")
        postToCamera("stopPreview") { service, device -> service.stopPreview(device) }
    }

    override fun getUVCControl(): UVCControl? {
        if (DEBUG) Log.d(TAG, "getUVCControl:")
        return callCamera("getUVCControl") { service, device -> service.getUVCControl(device) }
    }

    override fun takePicture(
        options: IImageCapture.OutputFileOptions,
        callback: IImageCapture.OnImageCaptureCallback,
    ) {
        if (DEBUG) Log.d(TAG, "takePicture")
        val mainThreadCallback = MainThreadImageCallback(callback)
        mAsyncHandler.post {
            val service = mService
            val device = mUsbDevice
            if (service != null && device != null) {
                try {
                    service.takePicture(device, options, mainThreadCallback)
                } catch (e: Exception) {
                    if (DEBUG) Log.e(TAG, "takePicture", e)
                    val message = e.message ?: "takePicture failed with an unknown exception"
                    mainThreadCallback.onError(IImageCapture.ERROR_UNKNOWN, message, e)
                }
            } else {
                val message = "Camera is released"
                mainThreadCallback.onError(IImageCapture.ERROR_CAMERA_CLOSED, message, IllegalStateException(message))
            }
        }
    }

    override val isRecording: Boolean
        get() {
            if (DEBUG) Log.d(TAG, "isRecording:")
            return callCamera("isRecording") { service, device -> service.isRecording(device) } ?: false
        }

    override fun startRecording(
        options: VideoCapture.OutputFileOptions,
        callback: VideoCapture.OnVideoCaptureCallback,
    ) {
        if (DEBUG) Log.d(TAG, "startRecording")
        val mainThreadCallback = MainThreadVideoCallback(callback)
        mAsyncHandler.post {
            val service = mService
            val device = mUsbDevice
            if (service != null && device != null) {
                try {
                    service.startRecording(device, options, mainThreadCallback)
                } catch (e: Exception) {
                    if (DEBUG) Log.e(TAG, "startRecording", e)
                    val message = e.message ?: "startRecording failed with an unknown exception"
                    mainThreadCallback.onError(VideoCapture.ERROR_UNKNOWN, message, e)
                }
            } else {
                val message = "Camera is released"
                mainThreadCallback.onError(VideoCapture.ERROR_INVALID_CAMERA, message, IllegalStateException(message))
            }
        }
    }

    override fun stopRecording() {
        if (DEBUG) Log.d(TAG, "stopRecording:")
        mAsyncHandler.post {
            if (isRecording) {
                callCamera("stopRecording") { service, device -> service.stopRecording(device) }
            }
        }
    }

    override val isCameraOpened: Boolean
        get() {
            if (DEBUG) Log.d(TAG, "isCameraOpened:")
            return callCamera("isCameraOpened") { service, device -> service.isCameraOpened(device) } ?: false
        }

    override fun release() {
        if (DEBUG) Log.d(TAG, "release: $this")
        releaseService { service ->
            mUsbDevice?.let { service.releaseCamera(it) }
        }
    }

    override fun releaseAll() {
        if (DEBUG) Log.d(TAG, "releaseAll: $this")
        releaseService { service -> service.releaseAllCamera() }
    }

    /**
     * Ends any wait on the helper thread, then releases [releaseCameras] and the service there and
     * stops the helper thread.
     *
     * 结束辅助线程上的所有等待，然后在该线程上执行 [releaseCameras] 并释放服务，最后停止辅助线程。
     */
    private fun releaseService(releaseCameras: (ICameraConnection) -> Unit) {
        cancelServiceWaits()
        mAsyncHandler.post {
            val service = mService
            if (service != null) {
                try {
                    releaseCameras(service)
                    service.release()
                } catch (e: Exception) {
                    if (DEBUG) Log.e(TAG, "release:", e)
                }

                mCallbackWrapper = null
                mService = null
            }

            mUsbDevice = null
            mAsyncHandlerThread.quitSafely()
            synchronized(mDetachedDeviceMap) {
                mDetachedDeviceMap.clear()
            }
        }
    }

    /**
     * Returns the current preview settings for this Camera.
     * If modifications are made to the returned Config, they must be set
     * back to this property to take effect.
     *
     * 返回此 Camera 当前的预览设置。
     * 如果对返回的 Config 进行了修改,必须将其重新设置给此属性才能生效。
     */
    override var previewConfig: CameraPreviewConfig
        get() = mCameraPreviewConfig
        set(config) {
            if (DEBUG) Log.d(TAG, "setCameraPreviewConfig:")
            mCameraPreviewConfig = config
            postToOpenedCamera("setCameraPreviewConfig") { service, device ->
                service.setPreviewConfig(device, mCameraPreviewConfig)
            }
        }

    /**
     * Returns the current ImageCapture settings for this Camera.
     * If modifications are made to the returned Config, they must be set
     * back to this property to take effect.
     *
     * 返回此 Camera 当前的 ImageCapture 设置。
     * 如果对返回的 Config 进行了修改,必须将其重新设置给此属性才能生效。
     */
    override var imageCaptureConfig: ImageCaptureConfig
        get() = mImageCaptureConfig
        set(config) {
            if (DEBUG) Log.d(TAG, "setImageCaptureConfig:")
            mImageCaptureConfig = config
            postToOpenedCamera("setImageCaptureConfig") { service, device ->
                service.setImageCaptureConfig(device, mImageCaptureConfig)
            }
        }

    /**
     * Returns the current VideoCapture settings for this Camera.
     * If modifications are made to the returned Config, they must be set
     * back to this property to take effect.
     *
     * 返回此 Camera 当前的 VideoCapture 设置。
     * 如果对返回的 Config 进行了修改,必须将其重新设置给此属性才能生效。
     */
    override var videoCaptureConfig: VideoCaptureConfig
        get() = mVideoCaptureConfig
        set(config) {
            if (DEBUG) Log.d(TAG, "setVideoCaptureConfig:")
            mVideoCaptureConfig = config
            postToOpenedCamera("setVideoCaptureConfig") { service, device ->
                service.setVideoCaptureConfig(device, mVideoCaptureConfig)
            }
        }

    fun registerCallback() {
        if (DEBUG) Log.d(TAG, "registerCallback:")
        callService("registerCallback") { it.register(mCallbackWrapper) }
    }

    fun unregisterCallback() {
        if (DEBUG) Log.d(TAG, "unregisterCallback:")
        callService("unregisterCallback") { it.unregister(mCallbackWrapper) }
    }

    /**
     * Runs [action] on the service now, on the caller's thread.
     *
     * 立即在调用方线程上对服务执行 [action]。
     *
     * @return the result, or null if there is no service or [action] threw / 结果；没有服务或 [action] 抛出异常时为 null
     */
    private inline fun <T> callService(name: String, action: (ICameraConnection) -> T): T? {
        val service = mService ?: return null
        return try {
            action(service)
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "$name:", e)
            null
        }
    }

    /**
     * Runs [action] on the service and the selected device now, on the caller's thread.
     *
     * 立即在调用方线程上对服务和所选设备执行 [action]。
     *
     * @return the result, or null if no device is selected or [action] threw / 结果；未选择设备或 [action] 抛出异常时为 null
     */
    private inline fun <T> callCamera(name: String, action: (ICameraConnection, UsbDevice) -> T): T? {
        val service = mService ?: return null
        val device = mUsbDevice ?: return null
        return try {
            action(service, device)
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "$name:", e)
            null
        }
    }

    /**
     * Runs [action] on the service and the selected device on the helper thread.
     *
     * 在辅助线程上对服务和所选设备执行 [action]。
     */
    private fun postToCamera(name: String, action: (ICameraConnection, UsbDevice) -> Unit) {
        mAsyncHandler.post { callCamera(name, action) }
    }

    /**
     * Runs [action] on the helper thread, only if the camera is open by then.
     *
     * 在辅助线程上执行 [action]，仅当届时相机已打开。
     */
    private fun postToOpenedCamera(name: String, action: (ICameraConnection, UsbDevice) -> Unit) {
        mAsyncHandler.post {
            if (isCameraOpened) {
                callCamera(name, action)
            }
        }
    }

    /**
     * Ends a wait for a camera or permission on the helper thread now, so the release posted after
     * it runs without waiting for that wait to time out.
     *
     * 立即结束辅助线程上对相机或权限的等待，使随后投递的释放操作无需等待其超时。
     */
    private fun cancelServiceWaits() {
        mService?.cancelWaits()
    }

    /**
     * Runs [action] on the main thread: now if this is the main thread, otherwise posted.
     *
     * 在主线程上运行 [action]：若当前即为主线程则立即运行，否则投递到主线程。
     */
    private fun runOnMainThread(action: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            mMainHandler.post(action)
        }
    }

    /**
     * Delivers every picture result on the main thread, whichever thread reports it.
     *
     * 无论由哪个线程报告，都在主线程上分发每个拍照结果。
     */
    private inner class MainThreadImageCallback(
        private val mCallback: IImageCapture.OnImageCaptureCallback,
    ) : IImageCapture.OnImageCaptureCallback {

        override fun onImageSaved(outputFileResults: IImageCapture.OutputFileResults) {
            runOnMainThread { mCallback.onImageSaved(outputFileResults) }
        }

        override fun onError(imageCaptureError: Int, message: String, cause: Throwable?) {
            runOnMainThread { mCallback.onError(imageCaptureError, message, cause) }
        }
    }

    /**
     * Delivers every recording event on the main thread, whichever thread reports it.
     *
     * 无论由哪个线程报告，都在主线程上分发每个录像事件。
     */
    private inner class MainThreadVideoCallback(
        private val mCallback: VideoCapture.OnVideoCaptureCallback,
    ) : VideoCapture.OnVideoCaptureCallback {

        override fun onStart() {
            runOnMainThread { mCallback.onStart() }
        }

        override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
            runOnMainThread { mCallback.onVideoSaved(outputFileResults) }
        }

        override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
            runOnMainThread { mCallback.onError(videoCaptureError, message, cause) }
        }
    }

    private inner class StateCallbackWrapper(
        private val mCallback: ICameraHelper.StateCallback,
    ) : ICameraHelper.StateCallback {

        override fun onAttach(device: UsbDevice) {
            if (DEBUG) Log.d(TAG, "onAttach:")
            synchronized(mDetachedDeviceMap) {
                mDetachedDeviceMap.remove(device)
            }
            mMainHandler.post { mCallback.onAttach(device) }
        }

        override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
            if (DEBUG) Log.d(TAG, "onDeviceOpen:")
            mMainHandler.post { mCallback.onDeviceOpen(device, isFirstOpen) }
        }

        override fun onCameraOpen(device: UsbDevice) {
            if (DEBUG) Log.d(TAG, "onCameraOpen:")
            mMainHandler.post { mCallback.onCameraOpen(device) }
        }

        override fun onCameraClose(device: UsbDevice) {
            if (DEBUG) Log.d(TAG, "onCameraClose:")
            mMainHandler.post { mCallback.onCameraClose(device) }
        }

        override fun onDeviceClose(device: UsbDevice) {
            if (DEBUG) Log.d(TAG, "onDeviceClose:")
            mMainHandler.post { mCallback.onDeviceClose(device) }
        }

        override fun onDetach(device: UsbDevice) {
            if (DEBUG) Log.d(TAG, "onDetach:")
            synchronized(mDetachedDeviceMap) {
                mDetachedDeviceMap[device] = null
            }
            mMainHandler.post { mCallback.onDetach(device) }
        }

        override fun onCancel(device: UsbDevice) {
            if (DEBUG) Log.d(TAG, "onCancel:")
            mMainHandler.post { mCallback.onCancel(device) }
        }

        override fun onError(device: UsbDevice, e: CameraException) {
            if (DEBUG) Log.d(TAG, "onError:")
            mMainHandler.post { mCallback.onError(device, e) }
        }
    }

    private companion object {
        /**
         * Checks the type of a preview surface on the caller's thread, so a wrong type is reported to
         * the caller instead of being thrown on the helper thread.
         *
         * 在调用方线程上检查预览 surface 的类型，使类型错误报告给调用方，而不是在辅助线程上抛出。
         */
        fun checkSurfaceType(surface: Any) {
            require(
                surface is SurfaceView || surface is SurfaceHolder ||
                    surface is Surface || surface is SurfaceTexture
            ) {
                "addSurface() can only be called with an instance of " +
                    "Surface, SurfaceView, SurfaceTexture or SurfaceHolder at the moment."
            }
        }

        /**
         * The Surface or SurfaceTexture to draw on, or null if a SurfaceView or SurfaceHolder has none yet.
         *
         * 用于绘制的 Surface 或 SurfaceTexture；如果 SurfaceView 或 SurfaceHolder 尚无 surface，则为 null。
         */
        fun fetchSurface(surface: Any): Any? = when (surface) {
            is SurfaceView -> surface.holder.surface
            is SurfaceHolder -> surface.surface
            else -> surface
        }
    }
}
