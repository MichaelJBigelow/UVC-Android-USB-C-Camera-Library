package com.herohan.uvcapp;

import com.serenegiant.opengl.renderer.IRendererHolder;

interface ICameraRendererHolder extends IRendererHolder {

    /**
     * capture still picture
     * blocking for capture to complete
     *
     * 拍摄静态图片
     * 阻塞直到拍摄完成
     */
    void captureImage(OnImageCapturedCallback callback);

    interface OnImageCapturedCallback{
        void onCaptureSuccess(ImageRawData image);

        void onError(Exception e);
    }
}
