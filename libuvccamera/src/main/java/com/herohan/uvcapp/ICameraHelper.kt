package com.herohan.uvcapp

import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.serenegiant.usb.Format
import com.serenegiant.usb.IButtonCallback
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCControl
import com.serenegiant.usb.UVCParam
import com.serenegiant.utils.UVCUtils
import com.serenegiant.uvccamera.R

interface ICameraHelper {

    fun setStateCallback(callback: StateCallback?)

    /**
     * The attached USB devices that match the device filter, or null if they can't be listed.
     *
     * 符合设备过滤器的已连接 USB 设备；无法列出时为 null。
     */
    val deviceList: List<UsbDevice>?

    fun selectDevice(device: UsbDevice)

    /**
     * The camera's formats, or null when no camera is open. Doesn't wait for the camera to open.
     *
     * 相机支持的格式；没有打开的相机时为 null。不会等待相机打开。
     */
    val supportedFormatList: List<Format>?

    /**
     * The camera's sizes, or null when no camera is open. Doesn't wait for the camera to open.
     *
     * 相机支持的尺寸；没有打开的相机时为 null。不会等待相机打开。
     */
    val supportedSizeList: List<Size>?

    /**
     * The current preview size, or null when no camera is open. Reading it doesn't wait for the
     * camera to open.
     *
     * Setting it changes the preview size, asynchronously; null is ignored. The change is ignored
     * while recording; if the camera rejects the size, the camera is closed and
     * [StateCallback.onCameraClose] is called.
     *
     * 当前的预览尺寸；没有打开的相机时为 null。读取时不会等待相机打开。
     *
     * 设置时会异步更改预览尺寸；忽略 null。录像期间的更改会被忽略；如果相机不接受该尺寸，
     * 则关闭相机并调用 [StateCallback.onCameraClose]。
     */
    var previewSize: Size?

    /**
     * Adds a preview surface.
     *
     * 添加预览 surface。
     *
     * @param surface a Surface, SurfaceTexture, SurfaceHolder or SurfaceView / Surface、SurfaceTexture、SurfaceHolder 或 SurfaceView
     * @throws IllegalArgumentException if surface is none of those / surface 不属于上述类型时
     */
    fun addSurface(surface: Any, isRecordable: Boolean)

    fun removeSurface(surface: Any)

    /**
     * Removes a preview surface, then runs [onRemoved] on the main thread once the camera no
     * longer draws on it, for example to release a SurfaceTexture.
     *
     * The default implementation, kept for other implementations of this interface, only posts
     * [onRemoved] after asking for the removal; [CameraHelper] waits for it.
     *
     * 移除预览 surface，并在相机不再向其绘制后于主线程上运行 [onRemoved]，
     * 例如用于释放 SurfaceTexture。
     *
     * 默认实现(为本接口的其他实现保留)只在请求移除后投递 [onRemoved]；
     * [CameraHelper] 会等待移除完成。
     */
    fun removeSurface(surface: Any, onRemoved: Runnable?) {
        removeSurface(surface)
        if (onRemoved != null) {
            Handler(Looper.getMainLooper()).post(onRemoved)
        }
    }

    fun setButtonCallback(callback: IButtonCallback?)

    fun setFrameCallback(callback: IFrameCallback?, pixelFormat: Int)

    fun openCamera()

    fun openCamera(size: Size?)

    fun openCamera(param: UVCParam)

    fun closeCamera()

    fun startPreview()

    fun stopPreview()

    /**
     * The camera's controls, or null when no camera is open. Doesn't wait for the camera to open.
     * Each get or set on the returned object is a USB control transfer, so call them off the main
     * thread.
     *
     * A function rather than a property, so Java callers keep calling getUVCControl().
     *
     * 相机的控制接口；没有打开的相机时为 null。不会等待相机打开。
     * 对返回对象的每次读取或设置都是一次 USB 控制传输，因此请在主线程之外调用。
     *
     * 使用函数而不是属性，使 Java 调用方仍可调用 getUVCControl()。
     */
    fun getUVCControl(): UVCControl?

    fun takePicture(
        options: IImageCapture.OutputFileOptions,
        callback: IImageCapture.OnImageCaptureCallback,
    )

    val isRecording: Boolean

    fun startRecording(
        options: VideoCapture.OutputFileOptions,
        callback: VideoCapture.OnVideoCaptureCallback,
    )

    fun stopRecording()

    val isCameraOpened: Boolean

    fun release()

    fun releaseAll()

    /**
     * The current preview settings for this Camera.
     * If modifications are made to the returned Config, they must be set
     * back to this property to take effect.
     *
     * 此 Camera 当前的预览设置。
     * 如果对返回的 Config 进行了修改,必须将其重新设置给此属性才能生效。
     */
    var previewConfig: CameraPreviewConfig

    /**
     * The current ImageCapture settings for this Camera.
     * If modifications are made to the returned Config, they must be set
     * back to this property to take effect.
     *
     * 此 Camera 当前的 ImageCapture 设置。
     * 如果对返回的 Config 进行了修改,必须将其重新设置给此属性才能生效。
     */
    var imageCaptureConfig: ImageCaptureConfig

    /**
     * The current VideoCapture settings for this Camera.
     * If modifications are made to the returned Config, they must be set
     * back to this property to take effect.
     *
     * 此 Camera 当前的 VideoCapture 设置。
     * 如果对返回的 Config 进行了修改,必须将其重新设置给此属性才能生效。
     */
    var videoCaptureConfig: VideoCaptureConfig

    interface StateCallback {
        fun onAttach(device: UsbDevice)

        fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean)

        fun onCameraOpen(device: UsbDevice)

        fun onCameraClose(device: UsbDevice)

        fun onDeviceClose(device: UsbDevice)

        fun onDetach(device: UsbDevice)

        fun onCancel(device: UsbDevice)

        fun onError(device: UsbDevice, e: CameraException) {
            // show tip according to error
            // 根据错误显示提示
            val context = UVCUtils.getApplication()
            val tip = if (e.code == CameraException.CAMERA_OPEN_ERROR_BUSY) {
                context.getString(R.string.error_busy_need_replug)
            } else {
                context.getString(R.string.error_unknown_need_replug)
            }
            Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
        }
    }
}
