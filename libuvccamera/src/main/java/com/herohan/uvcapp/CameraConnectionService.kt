package com.herohan.uvcapp

import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.serenegiant.usb.DeviceFilter
import com.serenegiant.usb.Format
import com.serenegiant.usb.IButtonCallback
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.Size
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener
import com.serenegiant.usb.USBMonitor.UsbControlBlock
import com.serenegiant.usb.UVCControl
import com.serenegiant.usb.UVCParam
import com.serenegiant.utils.UVCUtils
import com.serenegiant.uvccamera.BuildConfig
import com.serenegiant.uvccamera.R
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val DEBUG = BuildConfig.DEBUG
private const val TAG = "CameraConnectionService"

/**
 * Longest wait for an opened device's camera to be registered.
 *
 * 等待已打开设备的相机完成注册的最长时间。
 */
private const val CAMERA_WAIT_TIMEOUT_MS = 3000L

/**
 * Longest wait in `selectDevice` for the USB permission result; the camera still opens
 * if the user answers later.
 *
 * `selectDevice` 等待 USB 权限结果的最长时间；用户稍后才作答时，相机仍会打开。
 */
private const val PERMISSION_WAIT_TIMEOUT_MS = 10000L

internal object CameraConnectionService {

    fun newConnection(): ICameraConnection = CameraConnection()

    private class CameraConnection : ICameraConnection {
        private val LOG_PREFIX = "CameraConnection#"

        /**
         * Guards the cameras, the pending permissions and mReleased; [mConnectionChanged] is
         * signalled whenever one of them changes.
         *
         * 保护相机、待处理的权限和 mReleased；其中任一项变化时都会通知 [mConnectionChanged]。
         */
        private val mConnectionLock = ReentrantLock()
        private val mConnectionChanged = mConnectionLock.newCondition()
        private val mCameras = HashMap<String, CameraInternal>()
        private var mLastCameraKey: String? = null

        /**
         * Keys of devices whose USB permission request has no result yet. Guarded by mConnectionLock.
         *
         * USB 权限请求尚无结果的设备 key。由 mConnectionLock 保护。
         */
        private val mPendingPermissionKeys = HashSet<String>()

        /**
         * Set by release(); waiters stop waiting. Guarded by mConnectionLock.
         *
         * 由 release() 置位；等待方停止等待。由 mConnectionLock 保护。
         */
        private var mReleased = false

        private val mListenerHandlerThread = HandlerThread(LOG_PREFIX + hashCode()).apply { start() }
        private val mListenerHandler = Handler(mListenerHandlerThread.looper)
        private var mUSBMonitor: USBMonitor? =
            USBMonitor(UVCUtils.getApplication(), MyOnDeviceConnectListener(), mListenerHandler)
        private var mWeakStateCallback: WeakReference<ICameraHelper.StateCallback>? = null

        //********************************************************************************
        private fun addCamera(device: UsbDevice, ctrlBlock: UsbControlBlock): CameraInternal {
            if (DEBUG) Log.d(TAG, "addCamera:device=" + device.deviceName)
            val key = getCameraKey(device)
            val cameraInternal = mConnectionLock.withLock {
                mLastCameraKey = null
                val camera = mCameras.getOrPut(key) {
                    CameraInternal(ctrlBlock)
                }
                mPendingPermissionKeys.remove(key)
                mConnectionChanged.signalAll()
                camera
            }
            checkExistCamera()
            return cameraInternal
        }

        private fun removeCamera(device: UsbDevice) {
            if (DEBUG) Log.d(TAG, "removeCamera:device=" + device.deviceName)
            val key = getCameraKey(device)
            mConnectionLock.withLock {
                mLastCameraKey = key
                mCameras.remove(key)?.release()
                mConnectionChanged.signalAll()
            }
            checkExistCamera()
        }

        /**
         * get CameraService that has specific key<br>
         * if device is null, just return top of CameraInternal instance(non-blocking method) if exists or null.<br>
         * if device is non-null, return specific CameraService if exist. block if not exists.<br>
         * return null if not exist matched specific device<br>
         *
         * 获取具有指定 key 的 CameraService<br>
         * 如果 device 为 null,则直接返回 CameraInternal 实例的顶部项(非阻塞方法)(若存在),否则返回 null。<br>
         * 如果 device 非 null,则返回指定的 CameraService(若存在);若不存在则阻塞。<br>
         * 如果不存在与指定设备匹配的项,则返回 null<br>
         */
        private fun getCamera(device: UsbDevice?, isBlocking: Boolean = true): CameraInternal? =
            mConnectionLock.withLock {
                if (device == null) {
                    return@withLock mCameras.values.firstOrNull()
                }
                val cameraKey = getCameraKey(device)
                var cameraInternal = mCameras[cameraKey]
                if (cameraInternal == null && isBlocking) {
                    // Don't wait for a camera that was just removed (that deadlocked async threads)
                    // 不要等待刚被移除的相机(否则异步线程会死锁)
                    if (cameraKey != mLastCameraKey) {
                        Log.i(TAG, "wait for service is ready")
                    }
                    val deadline = SystemClock.elapsedRealtime() + CAMERA_WAIT_TIMEOUT_MS
                    while (cameraInternal == null && !mReleased && cameraKey != mLastCameraKey) {
                        if (!waitUntil(deadline)) {
                            break
                        }
                        cameraInternal = mCameras[cameraKey]
                    }
                }
                cameraInternal
            }

        /**
         * Waits on [mConnectionChanged] until signalled or [deadline] (elapsedRealtime) passes.
         * The caller must hold mConnectionLock and re-check its condition afterwards.
         *
         * 在 [mConnectionChanged] 上等待，直到收到通知或超过 [deadline](elapsedRealtime)。
         * 调用方必须持有 mConnectionLock，并在之后重新检查其条件。
         *
         * @return false if the deadline passed or the thread was interrupted (the interrupt is
         * kept) / 超过截止时间或线程被中断(保留中断状态)时返回 false
         */
        private fun waitUntil(deadline: Long): Boolean {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) {
                return false
            }
            return try {
                mConnectionChanged.await(remaining, TimeUnit.MILLISECONDS)
                true
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }

        private fun checkExistCamera(): Boolean = mConnectionLock.withLock {
            val n = mCameras.size
            if (DEBUG) Log.d(TAG, "number of existed camera=$n")
            n == 0
        }

        private fun getCameraKey(device: UsbDevice): String = device.deviceId.toString()

        private fun getStateCallback(): ICameraHelper.StateCallback? = mWeakStateCallback?.get()

        /**
         * Runs [action] with the app's state callback, if any; an exception it throws is logged.
         *
         * 如果存在应用的状态回调，则用其执行 [action]；它抛出的异常会被记录。
         */
        private fun notifyStateCallback(action: (ICameraHelper.StateCallback) -> Unit) {
            val callback = getStateCallback() ?: return
            try {
                action(callback)
            } catch (e: Exception) {
                Log.w(TAG, "listener callback:", e)
            }
        }
        //********************************************************************************

        override fun register(callback: ICameraHelper.StateCallback?) {
            mWeakStateCallback = callback?.let { WeakReference(it) }

            mUSBMonitor?.let { monitor ->
                if (DEBUG) Log.d(TAG, "mUSBMonitor#register:")
                val filters = DeviceFilter.getDeviceFilters(UVCUtils.getApplication(), R.xml.device_filter)
                monitor.setDeviceFilter(filters)
                monitor.register()
            }
        }

        override fun unregister(callback: ICameraHelper.StateCallback?) {
            mUSBMonitor?.let { monitor ->
                if (DEBUG) Log.d(TAG, "mUSBMonitor#unregister:")
                // Keep the monitor so register() can be called again; release() destroys it
                // 保留 USBMonitor，以便可以再次调用 register()；由 release() 负责销毁
                monitor.unregister()
            }

            mWeakStateCallback?.clear()
        }

        override val deviceList: List<UsbDevice>?
            get() = mUSBMonitor?.deviceList

        /**
         * check usb permission, open device
         *
         * 检查 usb 权限,打开设备
         */
        override fun selectDevice(device: UsbDevice) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "selectDevice:device=" + device.deviceName)
            val cameraKey = getCameraKey(device)
            mConnectionLock.withLock {
                Log.i(TAG, "request permission")
                mPendingPermissionKeys.add(cameraKey)
                mUSBMonitor?.requestPermission(device)
                var cameraInternal = mCameras[cameraKey]
                if (cameraInternal == null) {
                    Log.i(TAG, "wait for getting permission")
                    // Wait until the camera is added, the request is cancelled or fails, or time runs out
                    // 等待直到相机被添加、请求被取消或失败，或者超时
                    val deadline = SystemClock.elapsedRealtime() + PERMISSION_WAIT_TIMEOUT_MS
                    while (cameraInternal == null && !mReleased && mPendingPermissionKeys.contains(cameraKey)) {
                        if (!waitUntil(deadline)) {
                            break
                        }
                        cameraInternal = mCameras[cameraKey]
                    }
                    checkNotNull(cameraInternal) { "failed to open USB device(has no permission yet)" }
                }
            }
            Log.i(TAG, "success to get service:serviceId=$cameraKey")
        }

        override fun getSupportedFormatList(device: UsbDevice): List<Format>? =
            getCamera(device, false)?.supportedFormatList

        override fun getSupportedSizeList(device: UsbDevice): List<Size>? =
            getCamera(device, false)?.supportedSizeList

        override fun getPreviewSize(device: UsbDevice): Size? =
            getCamera(device, false)?.previewSize

        override fun setPreviewSize(device: UsbDevice, size: Size) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "setPreviewSize:")
            requireCamera(device).setPreviewSize(size)
        }

        override fun addSurface(device: UsbDevice, surface: Any, isRecordable: Boolean) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "addSurface:surface=$surface")
            getCamera(device, false)?.addSurface(surface, isRecordable)
        }

        override fun removeSurface(device: UsbDevice, surface: Any) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "removeSurface:surface=$surface")
            getCamera(device, false)?.removeSurface(surface)
        }

        override fun setButtonCallback(device: UsbDevice, callback: IButtonCallback?) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "setButtonCallback:callback=$callback")
            getCamera(device)?.setButtonCallback(callback)
        }

        override fun setFrameCallback(device: UsbDevice, callback: IFrameCallback?, pixelFormat: Int) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "setFrameCallback:pixelFormat=$pixelFormat")
            getCamera(device)?.setFrameCallback(callback, pixelFormat)
        }

        /**
         * open device once again, open camera and start streaming
         *
         * 再次打开设备,打开摄像头并开始推流
         */
        override fun openCamera(
            device: UsbDevice,
            param: UVCParam,
            previewConfig: CameraPreviewConfig,
            imageCaptureConfig: ImageCaptureConfig,
            videoCaptureConfig: VideoCaptureConfig,
        ) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "openCamera:")
            requireCamera(device).openCamera(param, previewConfig, imageCaptureConfig, videoCaptureConfig)
        }

        /**
         * stop streaming and close device
         *
         * 停止推流并关闭设备
         */
        override fun closeCamera(device: UsbDevice) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "closeCamera:")
            requireCamera(device).closeCamera()
        }

        /**
         * start streaming
         *
         * 开始推流
         */
        override fun startPreview(device: UsbDevice) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "startPreview:")
            requireCamera(device).startPreview()
        }

        /**
         * stop streaming
         *
         * 停止推流
         */
        override fun stopPreview(device: UsbDevice) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "stopPreview:")
            requireCamera(device).stopPreview()
        }

        override fun getUVCControl(device: UsbDevice): UVCControl? =
            getCamera(device, false)?.uvcControl

        override fun takePicture(
            device: UsbDevice,
            options: IImageCapture.OutputFileOptions,
            callback: IImageCapture.OnImageCaptureCallback,
        ) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "takePicture")
            val cameraInternal = getCamera(device)
            if (cameraInternal != null) {
                cameraInternal.takePicture(options, callback)
            } else {
                val message = "Camera not available"
                callback.onError(IImageCapture.ERROR_INVALID_CAMERA, message, IllegalStateException(message))
            }
        }

        override fun isRecording(device: UsbDevice): Boolean =
            getCamera(device, false)?.isRecording == true

        override fun startRecording(
            device: UsbDevice,
            options: VideoCapture.OutputFileOptions,
            callback: VideoCapture.OnVideoCaptureCallback,
        ) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "startRecording")
            val cameraInternal = getCamera(device)
            if (cameraInternal != null) {
                cameraInternal.startRecording(options, callback)
            } else {
                val message = "Camera not available"
                callback.onError(VideoCapture.ERROR_INVALID_CAMERA, message, IllegalStateException(message))
            }
        }

        override fun stopRecording(device: UsbDevice) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "stopRecording:")
            getCamera(device)?.stopRecording()
        }

        override fun isCameraOpened(device: UsbDevice): Boolean =
            getCamera(device, false)?.isCameraOpened == true

        /**
         * release camera
         *
         * 释放摄像头
         */
        override fun releaseCamera(device: UsbDevice) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "release:")
            val cameraKey = getCameraKey(device)
            mConnectionLock.withLock {
                mCameras.remove(cameraKey)?.release()
            }
        }

        override fun releaseAllCamera() {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "releaseAll:")
            mConnectionLock.withLock {
                for (cameraInternal in mCameras.values) {
                    cameraInternal.release()
                }
                mCameras.clear()
            }
        }

        override fun setPreviewConfig(device: UsbDevice, config: CameraPreviewConfig) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "setCameraPreviewConfig:")
            withExistingCamera(device) { it.setPreviewConfig(config) }
        }

        override fun setImageCaptureConfig(device: UsbDevice, config: ImageCaptureConfig) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "setImageCaptureConfig:")
            withExistingCamera(device) { it.setImageCaptureConfig(config) }
        }

        override fun setVideoCaptureConfig(device: UsbDevice, config: VideoCaptureConfig) {
            if (DEBUG) Log.d(TAG, LOG_PREFIX + "setVideoCaptureConfig:")
            withExistingCamera(device) { it.setVideoCaptureConfig(config) }
        }

        override fun cancelWaits() {
            mConnectionLock.withLock {
                mReleased = true
                mConnectionChanged.signalAll()
            }
        }

        override fun release() {
            cancelWaits()
            mUSBMonitor?.destroy()
            mUSBMonitor = null

            mListenerHandlerThread.quitSafely()

            mWeakStateCallback?.clear()
        }

        /**
         * The camera of [device], waiting for it to be registered if needed.
         *
         * [device] 的相机；如有需要会等待其完成注册。
         *
         * @throws IllegalArgumentException if there is no such camera / 不存在该相机时
         */
        private fun requireCamera(device: UsbDevice): CameraInternal =
            getCamera(device) ?: throw IllegalArgumentException("invalid device")

        /**
         * Runs [action] on the camera of [device] if it is registered, while holding mConnectionLock.
         *
         * 如果 [device] 的相机已注册，则在持有 mConnectionLock 时对其执行 [action]。
         */
        private inline fun withExistingCamera(device: UsbDevice, action: (CameraInternal) -> Unit) {
            val cameraKey = getCameraKey(device)
            mConnectionLock.withLock {
                mCameras[cameraKey]?.let(action)
            }
        }

        private inner class MyOnDeviceConnectListener : OnDeviceConnectListener {
            /**
             * Keys of devices whose close was already reported since they were last opened.
             * Only used on the listener thread, where USBMonitor delivers every callback.
             *
             * 自上次打开以来已报告关闭的设备 key。
             * 仅在监听线程上使用，USBMonitor 的所有回调都在该线程上分发。
             */
            private val mClosedDeviceKeys = HashSet<String>()

            override fun onAttach(device: UsbDevice?) {
                if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onAttach:")
                if (device == null) return

                notifyStateCallback { it.onAttach(device) }
            }

            override fun onDeviceOpen(device: UsbDevice?, ctrlBlock: UsbControlBlock?, createNew: Boolean) {
                if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onDeviceOpen:")
                if (device == null || ctrlBlock == null) return

                mClosedDeviceKeys.remove(getCameraKey(device))
                val camera = addCamera(device, ctrlBlock)

                // register callback that listen onCameraOpen and onCameraClose state
                // 注册回调,用于监听 onCameraOpen 和 onCameraClose 状态
                camera.registerCallback(object : ICameraInternal.StateCallback {
                    var mIsCameraOpened = false

                    override fun onCameraOpen() {
                        if (!mIsCameraOpened) {
                            notifyStateCallback { it.onCameraOpen(device) }
                            mIsCameraOpened = true
                        }
                    }

                    override fun onCameraClose() {
                        if (mIsCameraOpened) {
                            notifyStateCallback { it.onCameraClose(device) }
                            mIsCameraOpened = false
                        }
                    }

                    override fun onError(e: CameraException) {
                        notifyStateCallback { it.onError(device, e) }
                    }
                })

                notifyStateCallback { it.onDeviceOpen(device, createNew) }
            }

            override fun onDeviceClose(device: UsbDevice?, ctrlBlock: UsbControlBlock?) {
                if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onDeviceClose:")
                if (device == null) return

                // A device can be closed twice: the camera's cloned control block, then the
                // monitor's own block on detach or release. Report it to the app only once.
                // 设备可能会被关闭两次：先是相机克隆的控制块，然后是 USBMonitor 自己的控制块
                // （在设备拔出或释放时）。只向应用报告一次。
                if (mClosedDeviceKeys.add(getCameraKey(device))) {
                    notifyStateCallback { it.onDeviceClose(device) }
                }

                removeCamera(device)
            }

            override fun onDetach(device: UsbDevice?) {
                if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onDetach:")
                if (device == null) return

                notifyStateCallback { it.onDetach(device) }

                removeCamera(device)
            }

            override fun onCancel(device: UsbDevice?) {
                if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onCancel:")

                if (device != null) {
                    notifyStateCallback { it.onCancel(device) }
                }

                endPermissionWait(device)
            }

            override fun onError(device: UsbDevice?, e: USBMonitor.USBException) {
                if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onError:")

                if (device != null && e.code == USBMonitor.USB_OPEN_ERROR_UNKNOWN) {
                    val ex = CameraException(CameraException.CAMERA_OPEN_ERROR_UNKNOWN, e.message)
                    notifyStateCallback { it.onError(device, ex) }
                }

                endPermissionWait(device)
            }

            /**
             * Ends selectDevice's wait for the permission of [device]: the request was cancelled
             * or failed.
             *
             * 结束 selectDevice 对 [device] 权限的等待：请求已被取消或失败。
             */
            private fun endPermissionWait(device: UsbDevice?) {
                mConnectionLock.withLock {
                    if (device != null) {
                        mPendingPermissionKeys.remove(getCameraKey(device))
                    }
                    mConnectionChanged.signalAll()
                }
            }
        }
    }
}
