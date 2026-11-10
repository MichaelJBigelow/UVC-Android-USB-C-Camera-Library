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


/**
 * Helper class for creating and using EGL rendering context
 *
 * 用于创建和使用 EGL 渲染上下文的辅助类
 */
public abstract class EGLBase {
    public static final Object EGL_LOCK = new Object();

    public static final int EGL_RECORDABLE_ANDROID = 0x3142;
    public static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    public static final int EGL_OPENGL_ES2_BIT = 4;
    public static final int EGL_OPENGL_ES3_BIT_KHR = 0x0040;
//	public static final int EGL_SWAP_BEHAVIOR_PRESERVED_BIT = 0x0400;

    /**
     * Egl helper method, create object of EGLBase10 or EGLBase14 depending on your environment
     * maxClientVersion=3, No stencil buffer.
     *
     * Egl 辅助方法,根据运行环境创建 EGLBase10 或 EGLBase14 对象
     * maxClientVersion=3,无模板缓冲区。
     *
     * @param sharedContext
     * @param withDepthBuffer
     * @param isRecordable
     * @return
     */
    public static EGLBase createFrom(final IContext sharedContext,
                                     final boolean withDepthBuffer, final boolean isRecordable) {

        return createFrom(sharedContext, 3, withDepthBuffer, 0, isRecordable);
    }

    /**
     * Egl helper method, create object of EGLBase10 or EGLBase14 depending on your environment
     * maxClientVersion=3
     *
     * Egl 辅助方法,根据运行环境创建 EGLBase10 或 EGLBase14 对象
     * maxClientVersion=3
     *
     * @param sharedContext
     * @param withDepthBuffer
     * @param stencilBits
     * @param isRecordable
     * @return
     */
    public static EGLBase createFrom(final IContext sharedContext,
                                     final boolean withDepthBuffer, final int stencilBits, final boolean isRecordable) {

        return createFrom(sharedContext, 3,
                withDepthBuffer, stencilBits, isRecordable);
    }

    /**
     * Egl helper method, create object of EGLBase10 or EGLBase14 depending on your environment
     *
     * Egl 辅助方法,根据运行环境创建 EGLBase10 或 EGLBase14 对象
     *
     * @param sharedContext
     * @param maxClientVersion
     * @param withDepthBuffer  If true, use 16-bit depth buffer, if false, no depth buffer / 为 true 时使用 16 位深度缓冲区,为 false 时不使用深度缓冲区
     * @param stencilBits      If it is less than or equal to 0, no stencil buffer / 小于或等于 0 时不使用模板缓冲区
     * @param isRecordable
     * @return
     */
    public static EGLBase createFrom(final IContext sharedContext, final int maxClientVersion,
                                     final boolean withDepthBuffer,
                                     final int stencilBits, final boolean isRecordable) {

        if (isEGL14Supported() && ((sharedContext == null)
                || (sharedContext instanceof EGLBase14.Context))) {

            return new EGLBase14((EGLBase14.Context) sharedContext, maxClientVersion,
                    withDepthBuffer, stencilBits, isRecordable);
        } else {
            return new EGLBase10((EGLBase10.Context) sharedContext, maxClientVersion,
                    withDepthBuffer, stencilBits, isRecordable);
        }
    }

    /**
     * Holder class of the EGL rendering context
     *
     * EGL 渲染上下文的持有类
     */
    public static abstract class IContext {
        public abstract long getNativeHandle();

        public abstract Object getEGLContext();
    }

    /**
     * Holder class of EGL configuration
     *
     * EGL 配置的持有类
     */
    public static abstract class IConfig {
    }

    /**
     * Drawing object that associated with EGL rendering context
     *
     * 与 EGL 渲染上下文关联的绘制对象
     */
    public interface IEglSurface {
        void makeCurrent();

        void swap();

        IContext getContext();

        /**
         * swap with presentation time[ns]
         * only works well now when using EGLBase14
         *
         * 以呈现时间[ns]进行交换
         * 目前仅在使用 EGLBase14 时才能正常工作
         *
         * @param presentationTimeNs
         */
        void swap(final long presentationTimeNs);

        void release();

        boolean isValid();
    }

    public static boolean isEGL14Supported() {
        return true;
    }

    /**
     * Destroy related resources
     *
     * 销毁相关资源
     */
    public abstract void release();

    /**
     * Query for string with GLES
     *
     * 通过 GLES 查询字符串
     *
     * @param what
     * @return
     */
    public abstract String queryString(final int what);

    /**
     * Get version of GLES
     *
     * 获取 GLES 的版本
     *
     * @return 1, 2, or 3
     */
    public abstract int getGlVersion();

    /**
     * Get the EGL rendering context
     *
     * 获取 EGL 渲染上下文
     *
     * @return
     */
    public abstract IContext getContext();

    /**
     * Get the EGL configuration
     *
     * 获取 EGL 配置
     *
     * @return
     */
    public abstract IConfig getConfig();

    /**
     * Create EglSurface from the specified Surface.
     * Return EglSurface that eglMakeCurrent method is already invoked
     *
     * 从指定的 Surface 创建 EglSurface。
     * 返回已调用 eglMakeCurrent 方法的 EglSurface
     *
     * @param nativeWindow Surface/SurfaceTexture/SurfaceHolder
     * @return
     */
    public abstract IEglSurface createFromSurface(final Object nativeWindow);

    /**
     * Create off-screen EglSurface from the specified size
     * Return EglSurface that eglMakeCurrent method is already invoked
     *
     * 根据指定的尺寸创建离屏 EglSurface
     * 返回已调用 eglMakeCurrent 方法的 EglSurface
     *
     * @param width  size of PBuffer offscreen (no less than 0) / PBuffer 离屏的尺寸(不小于 0)
     * @param height size of PBuffer offscreen (no less than 0) / PBuffer 离屏的尺寸(不小于 0)
     * @return
     */
    public abstract IEglSurface createOffscreen(final int width, final int height);

    /**
     * Release assignment between the EGL rendering context and thread
     *
     * 解除 EGL 渲染上下文与线程之间的绑定
     */
    public abstract void makeDefault();

    /**
     * Invoke eglWaitGL and eglWaitNative
     * <p>
     * eglWaitGL: Complete GL execution prior to subsequent native rendering calls. The same result can be achieved using glFinish.
     * eglWaitNative: Complete native execution prior to subsequent GL rendering calls
     *
     * 调用 eglWaitGL 和 eglWaitNative
     * <p>
     * eglWaitGL: 在后续的原生渲染调用之前完成 GL 的执行。使用 glFinish 也可以达到相同的效果。
     * eglWaitNative: 在后续的 GL 渲染调用之前完成原生的执行
     */
    public abstract void sync();
}
