package com.herohan.uvcapp

import android.hardware.usb.UsbDevice
import com.serenegiant.usb.Format
import com.serenegiant.usb.IButtonCallback
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCControl
import com.serenegiant.usb.UVCParam

/**
 * <selectDevice						check usb permission, open device
 * <openCamera				open device once again, open camera and start streaming
 * closeCamera>				stop streaming and close device
 * release>					release camera
 *
 * <selectDevice						检查 USB 权限，打开设备
 * <openCamera				再次打开设备，打开相机并开始推流
 * closeCamera>				停止推流并关闭设备
 * release>					释放相机
 */
internal interface ICameraConnection {
    fun register(callback: ICameraHelper.StateCallback?)

    fun unregister(callback: ICameraHelper.StateCallback?)

    val deviceList: List<UsbDevice>?

    @Throws(Exception::class)
    fun selectDevice(device: UsbDevice)

    fun getSupportedFormatList(device: UsbDevice): List<Format>?

    fun getSupportedSizeList(device: UsbDevice): List<Size>?

    fun getPreviewSize(device: UsbDevice): Size?

    fun setPreviewSize(device: UsbDevice, size: Size)

    fun addSurface(device: UsbDevice, surface: Any, isRecordable: Boolean)

    fun removeSurface(device: UsbDevice, surface: Any)

    fun setButtonCallback(device: UsbDevice, callback: IButtonCallback?)

    fun setFrameCallback(device: UsbDevice, callback: IFrameCallback?, pixelFormat: Int)

    fun openCamera(
        device: UsbDevice,
        param: UVCParam,
        previewConfig: CameraPreviewConfig,
        imageCaptureConfig: ImageCaptureConfig,
        videoCaptureConfig: VideoCaptureConfig,
    )

    fun closeCamera(device: UsbDevice)

    fun startPreview(device: UsbDevice)

    fun stopPreview(device: UsbDevice)

    fun getUVCControl(device: UsbDevice): UVCControl?

    fun takePicture(
        device: UsbDevice,
        options: IImageCapture.OutputFileOptions,
        callback: IImageCapture.OnImageCaptureCallback,
    )

    fun isRecording(device: UsbDevice): Boolean

    fun startRecording(
        device: UsbDevice,
        options: VideoCapture.OutputFileOptions,
        callback: VideoCapture.OnVideoCaptureCallback,
    )

    fun stopRecording(device: UsbDevice)

    fun isCameraOpened(device: UsbDevice): Boolean

    fun releaseCamera(device: UsbDevice)

    fun releaseAllCamera()

    fun setPreviewConfig(device: UsbDevice, config: CameraPreviewConfig)

    fun setImageCaptureConfig(device: UsbDevice, config: ImageCaptureConfig)

    fun setVideoCaptureConfig(device: UsbDevice, config: VideoCaptureConfig)

    fun release()

    /**
     * Ends any wait for a camera or a permission result now. Safe to call from any thread; called
     * before release(), which runs on the thread that may be waiting.
     *
     * 立即结束对相机或权限结果的所有等待。可在任意线程调用；在 release() 之前调用，
     * 因为 release() 运行在可能正在等待的线程上。
     */
    fun cancelWaits()
}
