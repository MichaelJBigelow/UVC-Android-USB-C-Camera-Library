package com.herohan.uvcapp

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import com.serenegiant.opengl.EGLBase
import com.serenegiant.opengl.EGLTask
import com.serenegiant.opengl.GLDrawer2D
import com.serenegiant.opengl.renderer.MirrorMode
import com.serenegiant.opengl.renderer.RendererHolder
import com.serenegiant.opengl.renderer.RendererHolderCallback
import com.serenegiant.uvccamera.BuildConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

private val DEBUG = BuildConfig.DEBUG
private const val TAG = "CameraRendererHolder"

internal class CameraRendererHolder(
    width: Int,
    height: Int,
    sharedContext: EGLBase.IContext?,
    flags: Int,
    maxClientVersion: Int,
    callback: RendererHolderCallback?,
) : RendererHolder(width, height, sharedContext, flags, maxClientVersion, callback), ICameraRendererHolder {

    /**
     * Only used on the render thread.
     *
     * 仅在渲染线程上使用。
     */
    private var mCaptureHolder: CaptureHolder? = null

    constructor(width: Int, height: Int, callback: RendererHolderCallback?) :
        this(width, height, null, EGLTask.EGL_FLAG_RECORDABLE, 3, callback)

    override fun onPrimarySurfaceCreate(surface: Surface) {
        super.onPrimarySurfaceCreate(surface)
        mRendererHandler.post { mCaptureHolder = CaptureHolder() }
    }

    override fun onPrimarySurfaceDestroy() {
        super.onPrimarySurfaceDestroy()
        mRendererHandler.post {
            mCaptureHolder?.release()
            mCaptureHolder = null
        }
    }

    override fun captureImage(callback: ICameraRendererHolder.OnImageCapturedCallback) {
        mRendererHandler.post {
            // Capture still image
            // 拍摄静态图片
            try {
                val holder = checkNotNull(mCaptureHolder) { "The preview surface is not ready" }
                val data = checkNotNull(holder.captureImageRawData()) { "The preview has no size" }
                callback.onCaptureSuccess(data)
            } catch (e: Exception) {
                callback.onError(e)
            }
        }
    }

    /**
     * Copies the renderer's current matrix, flipped up-side down, since glReadPixels reads the
     * picture bottom row first.
     *
     * 复制渲染器当前的矩阵并上下翻转，因为 glReadPixels 先读取图像的最底行。
     */
    private fun captureMvpMatrix(): FloatArray {
        val mvpMatrix = mMvpMatrix.copyOf(16)
        val mirrorMatrix = FloatArray(16)
        Matrix.setIdentityM(mirrorMatrix, 0)
        // Must flip up-side down otherwise our output will look upside down relative to what appears on screen
        // 必须上下翻转，否则输出图像相对于屏幕上显示的内容会是上下颠倒的
        setMirrorMode(mirrorMatrix, MirrorMode.MIRROR_VERTICAL)
        Matrix.multiplyMM(mvpMatrix, 0, mirrorMatrix, 0, mvpMatrix, 0)
        return mvpMatrix
    }

    private inner class CaptureHolder {
        private var mCaptureEglBase: EGLBase? = EGLBase.createFrom(context, 3, false, 0, false)
        private var mCaptureSurface: EGLBase.IEglSurface? = mCaptureEglBase?.createOffscreen(mVideoWidth, mVideoHeight)
        private var mCaptureDrawer: GLDrawer2D? = GLDrawer2D(true)

        private var mWidth = -1
        private var mHeight = -1
        private var mBuf: ByteBuffer? = null

        /**
         * Reads the current frame back as RGBA pixels.
         *
         * 将当前帧读回为 RGBA 像素。
         *
         * @return the pixels, or null if the preview has no size / 像素；预览没有尺寸时返回 null
         */
        fun captureImageRawData(): ImageRawData? {
            if (DEBUG) Log.v(TAG, "#captureImageData:start")
            val eglBase = checkNotNull(mCaptureEglBase) { "The capture holder is released" }
            val drawer = checkNotNull(mCaptureDrawer) { "The capture holder is released" }
            var buf = mBuf
            if (buf == null || mWidth != mVideoWidth || mHeight != mVideoHeight) {
                mWidth = mVideoWidth
                mHeight = mVideoHeight
                buf = ByteBuffer.allocateDirect(mWidth * mHeight * 4).order(ByteOrder.LITTLE_ENDIAN)
                mBuf = buf
                mCaptureSurface?.release()
                mCaptureSurface = eglBase.createOffscreen(mWidth, mHeight)
            }
            var data: ImageRawData? = null
            val surface = mCaptureSurface
            if (mWidth > 0 && mHeight > 0 && surface != null && buf != null) {
                drawer.setMvpMatrix(captureMvpMatrix(), 0)

                surface.makeCurrent()
                drawer.draw(mTexId, mTexMatrix, 0)
                surface.swap()
                buf.clear()
                GLES20.glReadPixels(0, 0, mWidth, mHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)

                makeCurrent()

                val bytes = ByteArray(buf.capacity())
                buf.rewind()
                buf.get(bytes)

                data = ImageRawData(bytes, mWidth, mHeight)
            } else {
                Log.w(TAG, "#captureImageData:unexpectedly width/height is zero")
            }
            if (DEBUG) Log.i(TAG, "#captureImageData:end")
            return data
        }

        fun release() {
            mCaptureDrawer?.release()
            mCaptureDrawer = null
            mCaptureSurface?.let {
                it.makeCurrent()
                it.release()
            }
            mCaptureSurface = null
            mCaptureEglBase?.release()
            mCaptureEglBase = null
        }
    }
}
