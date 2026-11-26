package com.herohan.uvcsample

import android.hardware.usb.UsbDevice
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.Size

/**
 * One USB camera tied to the lifecycle of a screen.
 *
 * The session creates its [CameraHelper] when [owner] reaches [activeState] and releases it when
 * [owner] leaves that state. It keeps the preview surfaces, starts the preview and attaches the
 * surfaces when the camera opens, detaches them when it closes, and exposes the preview size and
 * open state as Compose state. Screens change the default behavior through [Callback].
 *
 * Create it during `onCreate` of the activity.
 *
 * 与界面生命周期绑定的一台 USB 相机。
 *
 * 当 [owner] 进入 [activeState] 时，会话创建其 [CameraHelper]；离开该状态时将其释放。
 * 它保存预览 surface，相机打开时启动预览并挂接这些 surface，相机关闭时将其移除，
 * 并以 Compose state 的形式提供预览尺寸和打开状态。界面可通过 [Callback] 改变默认行为。
 *
 * 请在 Activity 的 `onCreate` 中创建它。
 *
 * @param owner the screen whose lifecycle the camera follows / 相机所跟随生命周期的界面
 * @param callback hooks for the camera's state changes / 相机状态变化的回调
 * @param activeState [Lifecycle.State.STARTED] keeps the camera from onStart to onStop,
 *   [Lifecycle.State.CREATED] from onCreate to onDestroy /
 *   [Lifecycle.State.STARTED] 在 onStart 到 onStop 期间保留相机，
 *   [Lifecycle.State.CREATED] 在 onCreate 到 onDestroy 期间保留相机
 * @param logTag tag of the log messages / 日志消息的标签
 * @param configure called on each new [ICameraHelper] before it is used / 每个新的 [ICameraHelper] 使用前调用
 */
class CameraSession(
    private val owner: LifecycleOwner,
    private val callback: Callback = object : Callback {},
    private val activeState: Lifecycle.State = Lifecycle.State.STARTED,
    private val logTag: String = TAG,
    private val configure: (ICameraHelper) -> Unit = {},
) {

    /**
     * Hooks for the camera's state changes. Every method has a default, so override only what the
     * screen needs. They are called on the main thread.
     *
     * 相机状态变化的回调。每个方法都有默认实现，界面只需重写所需的方法。它们在主线程上调用。
     */
    interface Callback {
        /**
         * A USB camera was attached. By default the session selects it, which asks for its USB
         * permission.
         *
         * 接入了一台 USB 相机。默认情况下会话会选择它，从而请求其 USB 权限。
         */
        fun onAttach(session: CameraSession, device: UsbDevice) {
            session.selectDevice(device)
        }

        /**
         * The USB permission of [device] was granted. By default the session opens the camera.
         *
         * 已获得 [device] 的 USB 权限。默认情况下会话会打开相机。
         */
        fun onDeviceOpen(session: CameraSession, device: UsbDevice, isFirstOpen: Boolean) {
            session.cameraHelper?.openCamera()
        }

        /**
         * Whether this session's open and close handling applies to [device]. Sessions that share
         * the USB bus with other sessions return false for the other sessions' cameras.
         *
         * 本会话的打开和关闭处理是否适用于 [device]。与其他会话共用 USB 总线的会话，
         * 对其他会话的相机应返回 false。
         */
        fun handlesDevice(session: CameraSession, device: UsbDevice): Boolean = true

        /**
         * The camera opened; the preview is running and the preview surfaces are attached.
         *
         * 相机已打开；预览正在运行，预览 surface 已挂接。
         */
        fun onCameraOpen(session: CameraSession, device: UsbDevice) {}

        /**
         * The camera is closing; called before the preview surfaces are detached.
         *
         * 相机正在关闭；在预览 surface 被移除之前调用。
         */
        fun onCameraClose(session: CameraSession, device: UsbDevice) {}

        fun onDeviceClose(session: CameraSession, device: UsbDevice) {}

        fun onDetach(session: CameraSession, device: UsbDevice) {}

        fun onCancel(session: CameraSession, device: UsbDevice) {}
    }

    /**
     * The camera helper, while the screen is in [activeState].
     *
     * 相机助手(界面处于 [activeState] 期间有效)。
     */
    var cameraHelper: ICameraHelper? = null
        private set

    /**
     * The last device selected through [selectDevice], until it is detached or its permission is
     * refused.
     *
     * 最近一次通过 [selectDevice] 选择的设备，直到它被拔出或其权限被拒绝。
     */
    var usbDevice: UsbDevice? = null

    /** Camera preview width, in pixels / 相机预览宽度(像素) */
    var previewWidth by mutableIntStateOf(DEFAULT_WIDTH)
        private set

    /** Camera preview height, in pixels / 相机预览高度(像素) */
    var previewHeight by mutableIntStateOf(DEFAULT_HEIGHT)
        private set

    /** Whether the camera is open / 相机是否已打开 */
    var isCameraOpened by mutableStateOf(false)
        private set

    /**
     * Preview surfaces (Surface, SurfaceTexture or SurfaceHolder) that currently exist.
     *
     * 当前存在的预览 surface(Surface、SurfaceTexture 或 SurfaceHolder)。
     */
    private val previewSurfaces = LinkedHashSet<Any>()

    init {
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                if (activeState == Lifecycle.State.CREATED) open()
            }

            override fun onStart(owner: LifecycleOwner) {
                if (activeState == Lifecycle.State.STARTED) open()
            }

            override fun onStop(owner: LifecycleOwner) {
                if (activeState == Lifecycle.State.STARTED) release()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                if (activeState == Lifecycle.State.CREATED) release()
            }
        })
    }

    private fun open() {
        Log.d(logTag, "initCameraHelper:")
        if (cameraHelper == null) {
            cameraHelper = CameraHelper().also {
                it.setStateCallback(stateCallback)
                configure(it)
            }
        }
    }

    private fun release() {
        Log.d(logTag, "clearCameraHelper:")
        cameraHelper?.release()
        cameraHelper = null
        isCameraOpened = false
        // The next helper starts with no device selected, so attached cameras are picked up again.
        // 下一个 helper 开始时没有选中的设备，因此会重新识别已接入的相机。
        usbDevice = null
        isPreviewPaused = false
    }

    /**
     * Adds a preview surface; it is attached to the camera now and every time the camera opens.
     *
     * 添加一个预览 surface；它会立即挂接到相机上，并在每次相机打开时重新挂接。
     */
    fun addPreviewSurface(surface: Any) {
        previewSurfaces.add(surface)
        cameraHelper?.addSurface(surface, false)
    }

    /**
     * Removes a preview surface that is about to be destroyed.
     *
     * 移除即将被销毁的预览 surface。
     */
    fun removePreviewSurface(surface: Any) {
        removePreviewSurface(surface) {}
    }

    /**
     * Removes a preview surface, then calls [onRemoved] on the main thread once the camera no
     * longer draws on it, for example to release a SurfaceTexture.
     *
     * 移除预览 surface，并在相机不再向其绘制后于主线程上调用 [onRemoved]，
     * 例如用于释放 SurfaceTexture。
     */
    fun removePreviewSurface(surface: Any, onRemoved: () -> Unit) {
        val helper = cameraHelper
        if (previewSurfaces.remove(surface) && helper != null) {
            helper.removeSurface(surface, Runnable { onRemoved() })
        } else {
            onRemoved()
        }
    }

    /**
     * Whether [pausePreview] stopped the preview; [resumePreview] restarts it.
     *
     * [pausePreview] 是否停止了预览；[resumePreview] 会重新启动它。
     */
    private var isPreviewPaused = false

    /**
     * Stops streaming while the screen is not visible, keeping the camera open. For sessions kept
     * from onCreate to onDestroy; call it from onStop.
     *
     * 在界面不可见时停止推流，但保持相机打开。适用于从 onCreate 保留到 onDestroy 的会话；
     * 请在 onStop 中调用。
     */
    fun pausePreview() {
        if (isCameraOpened) {
            cameraHelper?.stopPreview()
            isPreviewPaused = true
        }
    }

    /**
     * Restarts the preview that [pausePreview] stopped; call it from onStart.
     *
     * 重新启动被 [pausePreview] 停止的预览；请在 onStart 中调用。
     */
    fun resumePreview() {
        if (isPreviewPaused) {
            isPreviewPaused = false
            if (isCameraOpened) {
                cameraHelper?.startPreview()
            }
        }
    }

    /**
     * Asks for the USB permission of [device]; [Callback.onDeviceOpen] follows once it is granted.
     *
     * 请求 [device] 的 USB 权限；获得权限后会调用 [Callback.onDeviceOpen]。
     */
    fun selectDevice(device: UsbDevice) {
        Log.v(logTag, "selectDevice:device=" + device.deviceName)
        usbDevice = device
        cameraHelper?.selectDevice(device)
    }

    /**
     * Selects the first attached UVC device.
     *
     * 选择第一个已接入的 UVC 设备。
     */
    fun selectFirstDevice() {
        cameraHelper?.deviceList?.firstOrNull()?.let { selectDevice(it) }
    }

    fun closeCamera() {
        cameraHelper?.closeCamera()
    }

    /**
     * Changes the size the preview views are laid out at, for example after a new video format.
     *
     * 更改预览视图的布局尺寸，例如在选择新的视频格式之后。
     */
    fun resizePreview(size: Size) {
        previewWidth = size.width
        previewHeight = size.height
    }

    private val stateCallback = object : ICameraHelper.StateCallback {
        override fun onAttach(device: UsbDevice) {
            Log.v(logTag, "onAttach:device=" + device.deviceName)
            callback.onAttach(this@CameraSession, device)
        }

        override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
            Log.v(logTag, "onDeviceOpen:device=" + device.deviceName)
            callback.onDeviceOpen(this@CameraSession, device, isFirstOpen)
        }

        override fun onCameraOpen(device: UsbDevice) {
            Log.v(logTag, "onCameraOpen:device=" + device.deviceName)
            val helper = cameraHelper ?: return
            if (!callback.handlesDevice(this@CameraSession, device)) return
            // A camera that opens while the screen is hidden waits for resumePreview to stream.
            // 在界面隐藏时打开的相机，等到 resumePreview 时才开始推流。
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                isPreviewPaused = false
                helper.startPreview()
            } else {
                isPreviewPaused = true
            }

            // After the camera opens, its preview size is known: keep the views at its aspect ratio.
            // 相机打开后即可获知其预览尺寸：让视图保持该宽高比。
            helper.previewSize?.let { resizePreview(it) }

            previewSurfaces.forEach { helper.addSurface(it, false) }

            isCameraOpened = true
            callback.onCameraOpen(this@CameraSession, device)
        }

        override fun onCameraClose(device: UsbDevice) {
            Log.v(logTag, "onCameraClose:device=" + device.deviceName)
            if (!callback.handlesDevice(this@CameraSession, device)) return

            callback.onCameraClose(this@CameraSession, device)

            // Detach the previews but keep them: they are attached again on the next onCameraOpen.
            // 将预览从相机上移除但保留它们：下次 onCameraOpen 时会重新挂接。
            cameraHelper?.let { helper -> previewSurfaces.forEach { helper.removeSurface(it) } }

            isCameraOpened = false
        }

        override fun onDeviceClose(device: UsbDevice) {
            Log.v(logTag, "onDeviceClose:device=" + device.deviceName)
            callback.onDeviceClose(this@CameraSession, device)
        }

        override fun onDetach(device: UsbDevice) {
            Log.v(logTag, "onDetach:device=" + device.deviceName)
            if (device == usbDevice) {
                usbDevice = null
            }
            callback.onDetach(this@CameraSession, device)
        }

        override fun onCancel(device: UsbDevice) {
            Log.v(logTag, "onCancel:device=" + device.deviceName)
            if (device == usbDevice) {
                usbDevice = null
            }
            callback.onCancel(this@CameraSession, device)
        }
    }

    private companion object {
        private const val TAG = "CameraSession"

        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480
    }
}
