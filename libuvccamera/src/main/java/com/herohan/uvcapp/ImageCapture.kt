package com.herohan.uvcapp

import java.lang.ref.WeakReference

/**
 * Takes pictures by reading the frame back from the renderer (the default capture strategy).
 *
 * 通过从渲染器读回画面来拍照(默认的拍照策略)。
 */
class ImageCapture internal constructor(
    rendererHolder: ICameraRendererHolder,
    config: ImageCaptureConfig,
) : BaseImageCapture(config, TAG) {

    private val mRendererHolderWeak = WeakReference(rendererHolder)

    /**
     * Captures a new still image and saves to a file.
     *
     * The callback will be called only once for every invocation of this method.
     *
     * 拍摄一张新的静态图像并保存到文件。
     *
     * 每次调用此方法,回调只会被调用一次。
     *
     * @param options  Options to store the newly captured image. / 用于保存新拍摄图像的选项。
     * @param callback Callback to be called for the newly captured image. / 新拍摄图像保存后调用的回调。
     */
    override fun takePicture(
        options: IImageCapture.OutputFileOptions,
        callback: IImageCapture.OnImageCaptureCallback,
    ) {
        val imageSavedCallbackWrapper = wrapCallback(callback)

        val cameraRendererHolder = mRendererHolderWeak.get()
        if (cameraRendererHolder == null) {
            postError(callback, IImageCapture.ERROR_INVALID_CAMERA, "Not bound to a Camera", null)
            return
        }

        val outputJpegQuality = getJpegQualityInternal()
        cameraRendererHolder.captureImage(object : ICameraRendererHolder.OnImageCapturedCallback {
            override fun onCaptureSuccess(image: ImageRawData) {
                mExecutor.execute(ImageSaver(image, outputJpegQuality, options, imageSavedCallbackWrapper))
            }

            override fun onError(e: Exception) {
                val message = e.message ?: "captureImage failed with an unknown exception"
                // Called on the render thread; report on the main thread.
                // 在渲染线程上调用；在主线程上报告。
                postError(callback, IImageCapture.ERROR_UNKNOWN, message, e)
            }
        })
    }

    private companion object {
        const val TAG = "ImageCapture"
    }
}
