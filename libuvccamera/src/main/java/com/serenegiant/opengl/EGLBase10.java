package com.serenegiant.opengl;
/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2018 saki t_saki@serenegiant.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.GLES10;
import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * Helper class for creating and using EGL rendering context
 *
 * 用于创建和使用 EGL 渲染上下文的辅助类
 */
/*package*/ class EGLBase10 extends EGLBase {
    //	private static final boolean DEBUG = false;	// FIXME set false on release
    private static final String TAG = EGLBase10.class.getSimpleName();

    private EGL10 mEgl = null;
    private EGLDisplay mEglDisplay = null;
    private Config mEglConfig = null;
    private int mGlVersion = 2;

    private static final Context EGL_NO_CONTEXT = new Context(EGL10.EGL_NO_CONTEXT);
    @NonNull
    private Context mContext = EGL_NO_CONTEXT;

    /**
     * Holder class of the EGL rendering context
     *
     * EGL 渲染上下文的持有类
     */
    public static class Context extends IContext {
        public final EGLContext eglContext;

        private Context(final EGLContext context) {
            eglContext = context;
        }

        @Override
        public long getNativeHandle() {
            return 0L;
        }

        @Override
        public Object getEGLContext() {
            return eglContext;
        }
    }

    public static class Config extends IConfig {
        public final EGLConfig eglConfig;

        private Config(final EGLConfig eglConfig) {
            this.eglConfig = eglConfig;
        }
    }

    /**
     * On Android 4.1.2, a Surface cannot be used directly,
     * even though a Surface is internally created and used for SurfaceTexture/SurfaceHolder.
     * SurfaceHolder is an interface, so create a dummy class that implements SurfaceHolder and pass it in instead.
     *
     * Android 4.1.2 上无法直接使用 Surface,
     * 尽管 SurfaceTexture/SurfaceHolder 内部也是创建并使用 Surface 的。
     * 由于 SurfaceHolder 是接口,因此创建一个实现 SurfaceHolder 的虚拟类并传入
     */
    public static class MySurfaceHolder implements SurfaceHolder {
        private final Surface surface;

        public MySurfaceHolder(final Surface surface) {
            this.surface = surface;
        }

        @Override
        public Surface getSurface() {
            return surface;
        }

        // Nothing below this point matters
        // 此处以下的方法都无关紧要
        @Override
        public void addCallback(final Callback callback) {
        }

        @Override
        public void removeCallback(final Callback callback) {
        }

        @Override
        public boolean isCreating() {
            return false;
        }

        @Override
        public void setType(final int type) {
        }

        @Override
        public void setFixedSize(final int width, final int height) {
        }

        @Override
        public void setSizeFromLayout() {
        }

        @Override
        public void setFormat(final int format) {
        }

        @Override
        public void setKeepScreenOn(final boolean screenOn) {
        }

        @Override
        public Canvas lockCanvas() {
            return null;
        }

        @Override
        public Canvas lockCanvas(final Rect dirty) {
            return null;
        }

        @Override
        public void unlockCanvasAndPost(final Canvas canvas) {
        }

        @Override
        public Rect getSurfaceFrame() {
            return null;
        }
    }

    /**
     * Wrapper of EGLSurface that associated with EGL rendering context
     *
     * 与 EGL 渲染上下文关联的 EGLSurface 的封装类
     */
    public static class EglSurface implements IEglSurface {
        private final EGLBase10 mEglBase;
        private EGLSurface mEglSurface = EGL10.EGL_NO_SURFACE;

        /**
         * Create EglSurface(PBuffer) with a Surface
         *
         * 使用 Surface 创建 EglSurface(PBuffer)
         *
         * @param eglBase
         * @param surface Surface/SurfaceTexture/SurfaceHolder/SurfaceView
         */
        private EglSurface(final EGLBase10 eglBase, final Object surface)
                throws IllegalArgumentException {

//			if (DEBUG) Log.v(TAG, "EglSurface:");
            mEglBase = eglBase;
            if ((surface instanceof Surface)
                    || (surface instanceof SurfaceHolder)
                    || (surface instanceof SurfaceTexture)
                    || (surface instanceof SurfaceView)) {
                mEglSurface = mEglBase.createWindowSurface(surface);
            } else {
                throw new IllegalArgumentException("unsupported surface");
            }
        }

        /**
         * Create Off-screen EglSurface(PBuffer) with a specified size
         *
         * 按指定尺寸创建离屏 EglSurface(PBuffer)
         *
         * @param eglBase
         * @param width
         * @param height
         */
        private EglSurface(final EGLBase10 eglBase, final int width, final int height) {
//			if (DEBUG) Log.v(TAG, "EglSurface:");
            mEglBase = eglBase;
            if ((width <= 0) || (height <= 0)) {
                mEglSurface = mEglBase.createOffscreenSurface(1, 1);
            } else {
                mEglSurface = mEglBase.createOffscreenSurface(width, height);
            }
        }

        /**
         * Invoke eglMakeCurrent method of EGL
         * <p>
         * The first time a EGL context is made current the viewport and scissor dimensions are set to the size of the draw surface
         * (as though glViewport(0,0,w,h) and glScissor(0,0,w,h) were called, where w and h are the width and height of the surface, respectively).
         * However, the viewport and scissor dimensions are not modified when context is subsequently made current.
         * The client is responsible for resetting the viewport and scissor in this case
         * <p>
         * 调用 EGL 的 eglMakeCurrent 方法
         * <p>
         * 首次将 EGL 上下文设为当前上下文时,视口和裁剪区域的尺寸会被设置为绘制 surface 的大小
         * (相当于调用了 glViewport(0,0,w,h) 和 glScissor(0,0,w,h),其中 w 和 h 分别为 surface 的宽度和高度)。
         * 但是,之后再次将该上下文设为当前上下文时,视口和裁剪区域的尺寸不会被修改。
         * 在这种情况下,需要由调用方自行重置视口和裁剪区域
         */
        @Override
        public void makeCurrent() {
            mEglBase.makeCurrent(mEglSurface);
            if (mEglBase.getGlVersion() >= 2) {
                GLES20.glViewport(0, 0,
                        mEglBase.getSurfaceWidth(mEglSurface), mEglBase.getSurfaceHeight(mEglSurface));
            } else {
                GLES10.glViewport(0, 0,
                        mEglBase.getSurfaceWidth(mEglSurface), mEglBase.getSurfaceHeight(mEglSurface));
            }
        }

        /**
         * Post EGL surface color buffer to native window
         *
         * 将 EGL surface 的颜色缓冲区提交到原生窗口
         */
        @Override
        public void swap() {
            mEglBase.swap(mEglSurface);
        }

        /**
         * Post EGL surface color buffer to native window
         *
         * 将 EGL surface 的颜色缓冲区提交到原生窗口
         */
        @Override
        public void swap(final long presentationTimeNs) {
            mEglBase.swap(mEglSurface, presentationTimeNs);
        }

        @Override
        public IContext getContext() {
            return mEglBase.getContext();
        }

        /**
         * Check EGLSurface is valid
         *
         * 检查 EGLSurface 是否有效
         */
        @Override
        public boolean isValid() {
            return (mEglSurface != null)
                    && (mEglSurface != EGL10.EGL_NO_SURFACE)
                    && (mEglBase.getSurfaceWidth(mEglSurface) > 0)
                    && (mEglBase.getSurfaceHeight(mEglSurface) > 0);
        }

        @Override
        public void release() {
//			if (DEBUG) Log.v(TAG, "EglSurface:release:");
            mEglBase.makeDefault();
            mEglBase.destroyWindowSurface(mEglSurface);
            mEglSurface = EGL10.EGL_NO_SURFACE;
        }
    }

    /**
     * Constructor
     *
     * @param sharedContext
     * @param maxClientVersion
     * @param withDepthBuffer
     * @param stencilBits
     * @param isRecordable     true  configure with EGL_RECORDABLE_ANDROID flag, need for MediaCodec's input surface / true 表示使用 EGL_RECORDABLE_ANDROID 标志进行配置,MediaCodec 的输入 surface 需要此标志
     */
    public EGLBase10(final Context sharedContext, final int maxClientVersion,
                     final boolean withDepthBuffer, final int stencilBits, final boolean isRecordable) {

//		if (DEBUG) Log.v(TAG, "Constructor:");
        init(sharedContext, maxClientVersion,
                withDepthBuffer, stencilBits, isRecordable);
    }

    /**
     * Destroy related resources
     *
     * 销毁相关资源
     */
    @Override
    public void release() {
//		if (DEBUG) Log.v(TAG, "release:");
        destroyContext();
        mContext = EGL_NO_CONTEXT;
        if (mEgl == null) return;
//		mEgl.eglReleaseThread();	// XXX これを入れるとハングアップする機種がある
        mEgl.eglTerminate(mEglDisplay);
        mEglDisplay = null;
        mEglConfig = null;
        mEgl = null;
    }

    /**
     * Create EglSurface from the specified Surface
     *
     * 从指定的 Surface 创建 EglSurface
     *
     * @param nativeWindow Surface/SurfaceTexture/SurfaceHolder
     * @return
     */
    @Override
    public EglSurface createFromSurface(final Object nativeWindow) {
//		if (DEBUG) Log.v(TAG, "createFromSurface:");
        final EglSurface eglSurface = new EglSurface(this, nativeWindow);
        eglSurface.makeCurrent();
        return eglSurface;
    }

    /**
     * Create off-screen EglSurface from the specified size
     *
     * 按指定尺寸创建离屏 EglSurface
     *
     * @param width  size of PBuffer offscreen (no less than 0) / PBuffer 离屏缓冲区的宽度(不小于 0)
     * @param height size of PBuffer offscreen (no less than 0) / PBuffer 离屏缓冲区的高度(不小于 0)
     * @return
     */
    @Override
    public EglSurface createOffscreen(final int width, final int height) {
//		if (DEBUG) Log.v(TAG, "createOffscreen:");
        final EglSurface eglSurface = new EglSurface(this, width, height);
        eglSurface.makeCurrent();
        return eglSurface;
    }

    /**
     * Get the EGL rendering context
     *
     * 获取 EGL 渲染上下文
     */
    @Override
    public Context getContext() {
        return mContext;
    }

    /**
     * Get the EGL configuration
     *
     * 获取 EGL 配置
     */
    @Override
    public Config getConfig() {
        return mEglConfig;
    }

    /**
     * Release assignment between the EGL rendering context and thread
     *
     * 解除 EGL 渲染上下文与线程之间的绑定
     */
    @Override
    public void makeDefault() {
//		if (DEBUG) Log.v(TAG, "makeDefault:");
        if (!mEgl.eglMakeCurrent(mEglDisplay,
                EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)) {

            Log.w(TAG, "makeDefault:eglMakeCurrent:err=" + mEgl.eglGetError());
        }
    }

    /**
     * Invoke eglWaitGL and eglWaitNative
     * <p>
     * eglWaitGL: Complete GL execution prior to subsequent native rendering calls. The same result can be achieved using glFinish.
     * eglWaitNative: Complete native execution prior to subsequent GL rendering calls
     * <p>
     * 调用 eglWaitGL 和 eglWaitNative
     * <p>
     * eglWaitGL:在后续的原生渲染调用之前完成 GL 的执行。使用 glFinish 也可以达到相同的效果。
     * eglWaitNative:在后续的 GL 渲染调用之前完成原生执行
     */
    @Override
    public void sync() {
        mEgl.eglWaitGL();    // same as GLES20.glFinish() / 等同于 GLES20.glFinish()
        mEgl.eglWaitNative(EGL10.EGL_CORE_NATIVE_ENGINE, null);
    }

    /**
     * Query for string with GLES
     *
     * 使用 GLES 查询字符串
     *
     * @param what
     * @return
     */
    @Override
    public String queryString(final int what) {
        return mEgl.eglQueryString(mEglDisplay, what);
    }

    /**
     * Get GLES version
     *
     * 获取 GLES 版本
     *
     * @return 1, 2 or 3
     */
    @Override
    public int getGlVersion() {
        return mGlVersion;
    }

    private void init(@Nullable Context sharedContext, final int maxClientVersion,
                      final boolean withDepthBuffer, final int stencilBits, final boolean isRecordable) {

//		if (DEBUG) Log.v(TAG, "init:");
        sharedContext = (sharedContext != null) ? sharedContext : EGL_NO_CONTEXT;
        if (mEgl == null) {
            mEgl = (EGL10) EGLContext.getEGL();
            mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (mEglDisplay == EGL10.EGL_NO_DISPLAY) {
                throw new RuntimeException("eglGetDisplay failed");
            }
            // Get the EGL version
            // 获取 EGL 版本
            final int[] version = new int[2];
            if (!mEgl.eglInitialize(mEglDisplay, version)) {
                throw new RuntimeException("eglInitialize failed");
            }
        }
        EGLConfig config;
        if (maxClientVersion >= 3) {
            // try to get config of GLES3
            // 尝试获取 GLES3 的配置
            config = getConfig(3, withDepthBuffer, stencilBits, isRecordable);
            if (config != null) {
                final EGLContext context = createContext(sharedContext, config, 3);
                if ((mEgl.eglGetError()) == EGL10.EGL_SUCCESS) {
                    // Check manually instead of using checkEglError, since we don't want an exception to be thrown here
                    // 这里不希望抛出异常,所以不使用 checkEglError,而是自行检查
                    //Log.d(TAG, "Got GLES 3 config");
                    mEglConfig = new Config(config);
                    mContext = new Context(context);
                    mGlVersion = 3;
                }
            }
        }
        // if fail to get GLES3, try to get GLES2
        // 如果获取 GLES3 失败,则尝试获取 GLES2
        if ((maxClientVersion >= 2)
                && ((mContext == null) || (mContext.eglContext == EGL10.EGL_NO_CONTEXT))) {

            config = getConfig(2, withDepthBuffer, stencilBits, isRecordable);
            if (config == null) {
                throw new RuntimeException("chooseConfig failed");
            }
            try {
                // create EGL rendering context
                // 创建 EGL 渲染上下文
                final EGLContext context = createContext(sharedContext, config, 2);
                checkEglError("eglCreateContext");
                mEglConfig = new Config(config);
                mContext = new Context(context);
                mGlVersion = 2;
            } catch (final Exception e) {
                if (isRecordable) {
                    config = getConfig(2, withDepthBuffer, stencilBits, false);
                    if (config == null) {
                        throw new RuntimeException("chooseConfig failed");
                    }
                    // create EGL rendering context
                    // 创建 EGL 渲染上下文
                    final EGLContext context = createContext(sharedContext, config, 2);
                    checkEglError("eglCreateContext");
                    mEglConfig = new Config(config);
                    mContext = new Context(context);
                    mGlVersion = 2;
                }
            }
        }
        if ((mContext == null) || (mContext.eglContext == EGL10.EGL_NO_CONTEXT)) {
            config = getConfig(1, withDepthBuffer, stencilBits, isRecordable);
            if (config == null) {
                throw new RuntimeException("chooseConfig failed");
            }
            // create EGL rendering context
            // 创建 EGL 渲染上下文
            final EGLContext context = createContext(sharedContext, config, 1);
            checkEglError("eglCreateContext");
            mEglConfig = new Config(config);
            mContext = new Context(context);
            mGlVersion = 1;
        }
        // confirm whether the EGL rendering context is successfully created
        // 确认 EGL 渲染上下文是否创建成功
        final int[] values = new int[1];
        mEgl.eglQueryContext(mEglDisplay,
                mContext.eglContext, EGL_CONTEXT_CLIENT_VERSION, values);
        Log.d(TAG, "EGLContext created, client version " + values[0]);
        makeDefault();
    }

    /**
     * Bind EGL context to the current rendering thread and to the draw and read surface
     *
     * 将 EGL 上下文绑定到当前渲染线程以及绘制和读取 surface
     *
     * @return
     */
    private boolean makeCurrent(final EGLSurface surface) {
//		if (DEBUG) Log.v(TAG, "makeCurrent:");
/*		if (mEglDisplay == null) {
            if (DEBUG) Log.d(TAG, "makeCurrent:eglDisplay not initialized");
        } */
        if (surface == null || surface == EGL10.EGL_NO_SURFACE) {
            final int error = mEgl.eglGetError();
            if (error == EGL10.EGL_BAD_NATIVE_WINDOW) {
                Log.e(TAG, "makeCurrent:EGL_BAD_NATIVE_WINDOW");
            }
            return false;
        }
        // attach EGL rendering context to specific EGL window surface
        // 将 EGL 渲染上下文附加到指定的 EGL 窗口 surface
        if (!mEgl.eglMakeCurrent(mEglDisplay, surface, surface, mContext.eglContext)) {
            Log.w("TAG", "eglMakeCurrent" + mEgl.eglGetError());
            return false;
        }
        return true;
    }

    private int swap(final EGLSurface surface) {
//		if (DEBUG) Log.v(TAG, "swap:");
        if (!mEgl.eglSwapBuffers(mEglDisplay, surface)) {
            final int err = mEgl.eglGetError();
//        	if (DEBUG) Log.w(TAG, "swap:err=" + err);
            return err;
        }
        return EGL10.EGL_SUCCESS;
    }

    /**
     * swap rendering buffer with presentation time[ns]
     * presentationTimeNs is ignored on this method
     *
     * 使用显示时间[ns]交换渲染缓冲区
     * 此方法会忽略 presentationTimeNs
     *
     * @param surface
     * @param ignored
     * @return
     */
    private int swap(final EGLSurface surface, final long ignored) {
//		if (DEBUG) Log.v(TAG, "swap:");
//		EGLExt.eglPresentationTimeANDROID(mEglDisplay, surface, presentationTimeNs);
        if (!mEgl.eglSwapBuffers(mEglDisplay, surface)) {
            final int err = mEgl.eglGetError();
//        	if (DEBUG) Log.w(TAG, "swap:err=" + err);
            return err;
        }
        return EGL10.EGL_SUCCESS;
    }

    private EGLContext createContext(
            @NonNull final Context sharedContext,
            final EGLConfig config, final int version) {

//		if (DEBUG) Log.v(TAG, "createContext:");

        final int[] attrib_list = {
                EGL_CONTEXT_CLIENT_VERSION, version,
                EGL10.EGL_NONE
        };
        final EGLContext context = mEgl.eglCreateContext(
                mEglDisplay, config, sharedContext.eglContext, attrib_list);
//		checkEglError("eglCreateContext");
        return context;
    }

    private void destroyContext() {
//		if (DEBUG) Log.v(TAG, "destroyContext:");

        if (!mEgl.eglDestroyContext(mEglDisplay, mContext.eglContext)) {
            Log.e("destroyContext", "display:" + mEglDisplay
                    + " context: " + mContext.eglContext);
            Log.e(TAG, "eglDestroyContext:" + mEgl.eglGetError());
        }
        mContext = EGL_NO_CONTEXT;
    }

    private int getSurfaceWidth(final EGLSurface surface) {
        final int[] value = new int[1];
        final boolean ret = mEgl.eglQuerySurface(mEglDisplay,
                surface, EGL10.EGL_WIDTH, value);
        if (!ret) value[0] = 0;
        return value[0];
    }

    private int getSurfaceHeight(final EGLSurface surface) {
        final int[] value = new int[1];
        final boolean ret = mEgl.eglQuerySurface(mEglDisplay,
                surface, EGL10.EGL_HEIGHT, value);
        if (!ret) value[0] = 0;
        return value[0];
    }

    /**
     * nativeWindow should be one of the SurfaceView, Surface, SurfaceHolder and SurfaceTexture
     *
     * nativeWindow 应为 SurfaceView、Surface、SurfaceHolder 和 SurfaceTexture 之一
     *
     * @param nativeWindow
     * @return
     */
    private EGLSurface createWindowSurface(final Object nativeWindow) {
//		if (DEBUG) Log.v(TAG, "createWindowSurface:nativeWindow=" + nativeWindow);

        final int[] surfaceAttribs = {
                EGL10.EGL_NONE
        };
        EGLSurface result = null;
        try {
            result = mEgl.eglCreateWindowSurface(mEglDisplay,
                    mEglConfig.eglConfig, nativeWindow, surfaceAttribs);
            if (result == null || result == EGL10.EGL_NO_SURFACE) {
                final int error = mEgl.eglGetError();
                if (error == EGL10.EGL_BAD_NATIVE_WINDOW) {
                    Log.e(TAG, "createWindowSurface returned EGL_BAD_NATIVE_WINDOW.");
                }
                throw new RuntimeException("createWindowSurface failed error=" + error);
            }
            makeCurrent(result);
            // Get the screen size and format
            // 获取屏幕尺寸和格式
        } catch (final Exception e) {
            Log.e(TAG, "eglCreateWindowSurface", e);
            throw new IllegalArgumentException(e);
        }
        return result;
    }

    /**
     * Creates an EGL surface associated with an offscreen buffer.
     *
     * 创建与离屏缓冲区关联的 EGL surface。
     */
    private EGLSurface createOffscreenSurface(final int width, final int height) {
//		if (DEBUG) Log.v(TAG, "createOffscreenSurface:");
        final int[] surfaceAttribs = {
                EGL10.EGL_WIDTH, width,
                EGL10.EGL_HEIGHT, height,
                EGL10.EGL_NONE
        };
        mEgl.eglWaitGL();
        EGLSurface result = null;
        try {
            result = mEgl.eglCreatePbufferSurface(mEglDisplay,
                    mEglConfig.eglConfig, surfaceAttribs);
            checkEglError("eglCreatePbufferSurface");
            if (result == null) {
                throw new RuntimeException("Failed to create pixel buffer surface");
            }
        } catch (final IllegalArgumentException e) {
            Log.e(TAG, "createOffscreenSurface", e);
        } catch (final RuntimeException e) {
            Log.e(TAG, "createOffscreenSurface", e);
        }
        return result;
    }

    private void destroyWindowSurface(EGLSurface surface) {
//		if (DEBUG) Log.v(TAG, "destroySurface:");

        if (surface != EGL10.EGL_NO_SURFACE) {
            mEgl.eglMakeCurrent(mEglDisplay,
                    EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            mEgl.eglDestroySurface(mEglDisplay, surface);
        }
        surface = EGL10.EGL_NO_SURFACE;
//		if (DEBUG) Log.v(TAG, "destroySurface:finished");
    }

    private void checkEglError(final String msg) {
        int error;
        if ((error = mEgl.eglGetError()) != EGL10.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }

    private EGLConfig getConfig(final int version,
                                final boolean hasDepthBuffer, final int stencilBits, final boolean isRecordable) {

        int renderableType = EGL_OPENGL_ES2_BIT;
        if (version >= 3) {
            renderableType |= EGL_OPENGL_ES3_BIT_KHR;
        }
//		final int swapBehavior = dirtyRegions ? EGL_SWAP_BEHAVIOR_PRESERVED_BIT : 0;
        final int[] attribList = {
                EGL10.EGL_RENDERABLE_TYPE, renderableType,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
//        	EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT | swapBehavior,
                EGL10.EGL_NONE, EGL10.EGL_NONE,    //EGL10.EGL_STENCIL_SIZE, 8,
                EGL10.EGL_NONE, EGL10.EGL_NONE,    // with_depth_buffer ? EGL10.EGL_DEPTH_SIZE : EGL10.EGL_NONE,
                // this flag need to recording of MediaCodec
                // 使用 MediaCodec 录制时需要此标志
                EGL10.EGL_NONE, EGL10.EGL_NONE,    // EGL_RECORDABLE_ANDROID, 1,
                // with_depth_buffer ? 16 : 0,
                EGL10.EGL_NONE
        };
        int offset = 10;
        // size of stencil buffer(Always unused)
        // 模板缓冲区的大小(始终未使用)
        if (stencilBits > 0) {
            attribList[offset++] = EGL10.EGL_STENCIL_SIZE;
            attribList[offset++] = 8;
        }
        // size of depth buffer
        // 深度缓冲区的大小
        if (hasDepthBuffer) {
            attribList[offset++] = EGL10.EGL_DEPTH_SIZE;
            attribList[offset++] = 16;
        }
        if (isRecordable) {
            // For MediaCodec's input Surface
            // 用于 MediaCodec 的输入 Surface
            attribList[offset++] = EGL_RECORDABLE_ANDROID;
            attribList[offset++] = 1;
        }
        for (int i = attribList.length - 1; i >= offset; i--) {
            attribList[i] = EGL10.EGL_NONE;
        }
        EGLConfig config = internalGetConfig(attribList);
        if ((config == null) && (version == 2)) {
            if (isRecordable) {
                // Remove EGL_RECORDABLE_ANDROID, since some devices fail when it is specified
                // 部分机型指定 EGL_RECORDABLE_ANDROID 会失败,因此将其移除
                final int n = attribList.length;
                for (int i = 10; i < n - 1; i += 2) {
                    if (attribList[i] == EGL_RECORDABLE_ANDROID) {
                        for (int j = i; j < n; j++) {
                            attribList[j] = EGL10.EGL_NONE;
                        }
                        break;
                    }
                }
                config = internalGetConfig(attribList);
            }
        }
        if (config == null) {
            Log.w(TAG, "try to fallback to RGB565");
            attribList[3] = 5;
            attribList[5] = 6;
            attribList[7] = 5;
            config = internalGetConfig(attribList);
        }
        return config;
    }

    private EGLConfig internalGetConfig(final int[] attribList) {
        final EGLConfig[] configs = new EGLConfig[1];
        final int[] numConfigs = new int[1];
        if (!mEgl.eglChooseConfig(mEglDisplay,
                attribList, configs, configs.length, numConfigs)) {
            return null;
        }
        if (numConfigs[0] < 0) {
            return null;
        }
        return configs[0];
    }
}
