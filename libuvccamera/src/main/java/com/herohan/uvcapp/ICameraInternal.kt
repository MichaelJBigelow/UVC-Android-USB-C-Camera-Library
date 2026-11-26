package com.herohan.uvcapp

import com.serenegiant.usb.Format
import com.serenegiant.usb.IButtonCallback
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCControl
import com.serenegiant.usb.UVCParam

internal interface ICameraInternal {

    fun registerCallback(callback: StateCallback)

    fun unregisterCallback(callback: StateCallback)

    fun clearCallbacks()

    val supportedFormatList: List<Format>?

    val supportedSizeList: List<Size>?

    val previewSize: Size?

    fun setPreviewSize(size: Size)

    fun addSurface(surface: Any, isRecordable: Boolean)

    fun removeSurface(surface: Any)

    fun setButtonCallback(callback: IButtonCallback?)

    fun setFrameCallback(callback: IFrameCallback?, pixelFormat: Int)

    fun openCamera(
        param: UVCParam,
        previewConfig: CameraPreviewConfig,
        imageCaptureConfig: ImageCaptureConfig,
        videoCaptureConfig: VideoCaptureConfig,
    )

    fun closeCamera()

    fun startPreview()

    fun stopPreview()

    val uvcControl: UVCControl?

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

    fun setPreviewConfig(config: CameraPreviewConfig)

    fun setImageCaptureConfig(config: ImageCaptureConfig)

    fun setVideoCaptureConfig(config: VideoCaptureConfig)

    interface StateCallback {
        fun onCameraOpen()

        fun onCameraClose()

        fun onError(e: CameraException)
    }
}
