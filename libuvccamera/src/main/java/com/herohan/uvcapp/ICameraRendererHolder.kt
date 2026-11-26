package com.herohan.uvcapp

import com.serenegiant.opengl.renderer.IRendererHolder

internal interface ICameraRendererHolder : IRendererHolder {

    /**
     * capture still picture
     * blocking for capture to complete
     *
     * 拍摄静态图片
     * 阻塞直到拍摄完成
     */
    fun captureImage(callback: OnImageCapturedCallback)

    interface OnImageCapturedCallback {
        fun onCaptureSuccess(image: ImageRawData)

        fun onError(e: Exception)
    }
}
