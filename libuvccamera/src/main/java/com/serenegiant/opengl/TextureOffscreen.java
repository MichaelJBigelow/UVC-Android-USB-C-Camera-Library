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

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import com.serenegiant.uvccamera.BuildConfig;

/**
 * Offscreen rendering class for drawing to a texture with OpenGL|ES.
 * The texture is assigned to the FBO as its color buffer.
 *
 * 用于通过 OpenGL|ES 向纹理绘制的离屏渲染类。
 * 将纹理作为颜色缓冲区分配给 FBO。
 */
public class TextureOffscreen {
	private static final boolean DEBUG = BuildConfig.DEBUG;
	private static final String TAG = "TextureOffscreen";

	private static final boolean DEFAULT_ADJUST_POWER2 = false;

	private final int TEX_TARGET;
	private final int TEX_UNIT;
	private final boolean mHasDepthBuffer, mAdjustPower2;
	/**
	 * Drawing area size
	 * 绘制区域大小
	 */
	private int mWidth, mHeight;
	/**
	 * Texture size
	 * 纹理大小
	 */
	private int mTexWidth, mTexHeight;
	/**
	 * Name of the texture used as the offscreen color buffer
	 * 用作离屏颜色缓冲区的纹理名称
	 */
	private int mFBOTextureName = -1;
	/**
	 * Buffer objects for offscreen rendering
	 * 离屏渲染用的缓冲区对象
	 */
	private int mDepthBufferObj = -1, mFrameBufferObj = -1;
	/**
	 * Texture coordinate transformation matrix
	 * 纹理坐标变换矩阵
	 */
	private final float[] mTexMatrix = new float[16];

	/**
	 * Constructor (GL_TEXTURE_2D), no depth buffer
	 * The texture unit is GL_TEXTURE0
	 *
	 * 构造函数(GL_TEXTURE_2D),无深度缓冲区
	 * 纹理单元为 GL_TEXTURE0
	 * @param width
	 * @param height
	 */
	public TextureOffscreen(final int width, final int height) {
		this(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE0, -1,
			width, height, false, DEFAULT_ADJUST_POWER2);
	}

	/**
	 * Constructor (GL_TEXTURE_2D), no depth buffer
	 * The texture unit is GL_TEXTURE0
	 *
	 * 构造函数(GL_TEXTURE_2D),无深度缓冲区
	 * 纹理单元为 GL_TEXTURE0
	 * @param tex_unit
	 * @param width
	 * @param height
	 */
	public TextureOffscreen(final int tex_unit,
		final int width, final int height) {

		this(GLES20.GL_TEXTURE_2D, tex_unit, -1,
			width, height,
			false, DEFAULT_ADJUST_POWER2);
	}

	/**
	 * Constructor (GL_TEXTURE_2D)
	 * The texture unit is GL_TEXTURE0
	 *
	 * 构造函数(GL_TEXTURE_2D)
	 * 纹理单元为 GL_TEXTURE0
	 * @param width dimension of offscreen(width) / 离屏宽度
	 * @param height dimension of offscreen(height) / 离屏高度
	 * @param use_depth_buffer set true if you use depth buffer. the depth is fixed as 16bits / 如果使用深度缓冲区请设为 true,深度固定为 16 位
	 */
	public TextureOffscreen(final int width, final int height,
		final boolean use_depth_buffer) {

		this(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE0, -1,
			width, height, use_depth_buffer, DEFAULT_ADJUST_POWER2);
	}

	/**
	 * Constructor for wrapping an existing texture (GL_TEXTURE_2D)
	 * The texture unit is GL_TEXTURE0
	 *
	 * 用于包装现有纹理(GL_TEXTURE_2D)的构造函数
	 * 纹理单元为 GL_TEXTURE0
	 * @param tex_unit
	 * @param width
	 * @param height
	 * @param use_depth_buffer
	 */
	public TextureOffscreen(final int tex_unit,
		final int width, final int height, final boolean use_depth_buffer) {

		this(GLES20.GL_TEXTURE_2D, tex_unit, -1,
			width, height,
			use_depth_buffer, DEFAULT_ADJUST_POWER2);
	}
	
	/**
	 * Constructor (GL_TEXTURE_2D)
	 * The texture unit is GL_TEXTURE0
	 *
	 * 构造函数(GL_TEXTURE_2D)
	 * 纹理单元为 GL_TEXTURE0
	 * @param width
	 * @param height
	 * @param use_depth_buffer
	 * @param adjust_power2
	 */
	public TextureOffscreen(final int width, final int height,
		final boolean use_depth_buffer, final boolean adjust_power2) {

		this(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE0, -1,
			width, height, use_depth_buffer, adjust_power2);
	}
	
	/**
	 * Constructor (GL_TEXTURE_2D)
	 *
	 * 构造函数(GL_TEXTURE_2D)
	 * @param tex_unit
	 * @param width
	 * @param height
	 * @param use_depth_buffer
	 * @param adjust_power2
	 */
	public TextureOffscreen(final int tex_unit,
		final int width, final int height,
		final boolean use_depth_buffer, final boolean adjust_power2) {

		this(GLES20.GL_TEXTURE_2D, tex_unit, -1,
			width, height, use_depth_buffer, adjust_power2);
	}

	/**
	 * Constructor for wrapping an existing texture (GL_TEXTURE_2D), no depth buffer
	 *
	 * 用于包装现有纹理(GL_TEXTURE_2D)的构造函数,无深度缓冲区
	 * @param tex_id
	 * @param tex_unit
	 * @param width
	 * @param height
	 */
	public TextureOffscreen(final int tex_unit, final int tex_id,
		final int width, final int height) {

		this(GLES20.GL_TEXTURE_2D, tex_unit, tex_id,
			width, height,
			false, DEFAULT_ADJUST_POWER2);
	}

	/**
	 * Constructor for wrapping an existing texture (GL_TEXTURE_2D)
	 *
	 * 用于包装现有纹理(GL_TEXTURE_2D)的构造函数
	 * @param tex_unit
	 * @param tex_id
	 * @param width
	 * @param height
	 * @param use_depth_buffer
	 */
	public TextureOffscreen(final int tex_unit, final int tex_id,
		final int width, final int height, final boolean use_depth_buffer) {

		this(GLES20.GL_TEXTURE_2D, tex_unit, tex_id,
			width, height,
			use_depth_buffer, DEFAULT_ADJUST_POWER2);
	}

	/**
	 * Constructor for wrapping an existing texture
	 *
	 * 用于包装现有纹理的构造函数
	 * @param tex_target GL_TEXTURE_2D
	 * @param tex_id
	 * @param width
	 * @param height
	 * @param use_depth_buffer
	 * @param adjust_power2
	 */
	public TextureOffscreen(final int tex_target, final int tex_unit, final int tex_id,
		final int width, final int height,
		final boolean use_depth_buffer, final boolean adjust_power2) {

		if (DEBUG) Log.v(TAG, "Constructor");
		TEX_TARGET = tex_target;
		TEX_UNIT = tex_unit;
		mWidth = width;
		mHeight = height;
		mHasDepthBuffer = use_depth_buffer;
		mAdjustPower2 = adjust_power2;

		createFrameBuffer(width, height);
		int tex = tex_id;
		if (tex < 0) {
			tex = genTexture(tex_target, tex_unit, mTexWidth, mTexHeight);
		}
		assignTexture(tex, width, height);
	}

	/**
	 * Release (destroy) this offscreen
	 * 释放(销毁)此离屏
	 */
	public void release() {
		if (DEBUG) Log.v(TAG, "release");
		releaseFrameBuffer();
	}

	/**
	 * Switch to the rendering buffer for offscreen drawing.
	 * The viewport is also changed, so set the viewport after unbind if necessary.
	 *
	 * 切换到离屏绘制用的渲染缓冲区。
	 * 视口也会随之改变,如有需要请在 unbind 之后重新设置视口。
	 */
	public void bind() {
//		if (DEBUG) Log.v(TAG, "bind:");
		GLES20.glActiveTexture(TEX_UNIT);
		GLES20.glBindTexture(TEX_TARGET, mFBOTextureName);
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBufferObj);
		GLES20.glViewport(0, 0, mWidth, mHeight);
	}

	/**
	 * Return to the default rendering buffer
	 *
	 * 返回默认的渲染缓冲区
	 */
	public void unbind() {
//		if (DEBUG) Log.v(TAG, "unbind:");
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
		GLES20.glActiveTexture(TEX_UNIT);
		GLES20.glBindTexture(TEX_TARGET, 0);
	}

	private final float[] mResultMatrix = new float[16];
	/**
	 * get copy of texture matrix
	 *
	 * 获取纹理矩阵的副本
	 * @return
	 */
	public float[] getTexMatrix() {
		System.arraycopy(mTexMatrix, 0, mResultMatrix, 0, 16);
		return mResultMatrix;
	}

	/**
	 * Get the texture coordinate transformation matrix (the internal array is returned directly, so be careful when modifying it)
	 *
	 * 获取纹理坐标变换矩阵(直接返回内部数组,修改时需要注意)
	 * @return
	 */
	public float[] getRawTexMatrix() {
		return mTexMatrix;
	}

	/**
	 * Copy the texture transformation matrix to the given array.
	 * The range is not checked, so reserve at least 16 elements from the offset position.
	 *
	 * 将纹理变换矩阵复制到指定数组。
	 * 不检查范围,请确保从 offset 位置起至少预留 16 个元素。
	 * @param matrix
	 */
	public void getTexMatrix(final float[] matrix, final int offset) {
		System.arraycopy(mTexMatrix, 0, matrix, offset, mTexMatrix.length);
	}

	/**
	 * Get the offscreen texture name.
	 * Can be used when drawing something else by using the image written to this offscreen as a texture.
	 *
	 * 获取离屏纹理名称。
	 * 可在把写入此离屏的图像当作纹理用于其他绘制时使用。
	 * @return
	 */
	public int getTexture() {
		return mFBOTextureName;
	}

	/**
	 * Assign the specified texture to this offscreen
	 * 将指定的纹理分配给此离屏
	 */
	public void assignTexture(final int texture_name,
		final int width, final int height) {

		if ((width > mTexWidth) || (height > mTexHeight)) {
			mWidth = width;
			mHeight = height;
			releaseFrameBuffer();
			createFrameBuffer(width, height);
		}
		mFBOTextureName = texture_name;
		GLES20.glActiveTexture(TEX_UNIT);
		 // Bind the framebuffer object
		 // 绑定帧缓冲区对象
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBufferObj);
		GLHelper.checkGlError("glBindFramebuffer " + mFrameBufferObj);
		// Attach the color buffer (texture) to the framebuffer
		// 将颜色缓冲区(纹理)连接到帧缓冲区
		GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
			TEX_TARGET, mFBOTextureName, 0);
		GLHelper.checkGlError("glFramebufferTexture2D");

		if (mHasDepthBuffer) {
			// Attach the depth buffer to the framebuffer
			// 将深度缓冲区连接到帧缓冲区
			GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER,
				GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, mDepthBufferObj);
			GLHelper.checkGlError("glFramebufferRenderbuffer");
		}

		// Check whether it finished normally
		// 检查是否正常完成
		final int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
		if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
			throw new RuntimeException("Framebuffer not complete, status=" + status);
		}

		 // Return to the default framebuffer
		 // 返回默认的帧缓冲区
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

		// Initialize the texture coordinate transformation matrix
		// 初始化纹理坐标变换矩阵
		Matrix.setIdentityM(mTexMatrix, 0);
		mTexMatrix[0] = width / (float)mTexWidth;
		mTexMatrix[5] = height / (float)mTexHeight;
	}

	/**
	 * Load a texture from a Bitmap
	 * 从 Bitmap 加载纹理
	 */
	public void loadBitmap(final Bitmap bitmap) {
		final int width = bitmap.getWidth();
		final int height = bitmap.getHeight();
		if ((width > mTexWidth) || (height > mTexHeight)) {
			mWidth = width;
			mHeight = height;
			releaseFrameBuffer();
			createFrameBuffer(width, height);
		}
		GLES20.glActiveTexture(TEX_UNIT);
		GLES20.glBindTexture(TEX_TARGET, mFBOTextureName);
		GLUtils.texImage2D(TEX_TARGET, 0, bitmap, 0);
		GLES20.glBindTexture(TEX_TARGET, 0);
		// initialize texture matrix
		// 初始化纹理矩阵
		Matrix.setIdentityM(mTexMatrix, 0);
		mTexMatrix[0] = width / (float)mTexWidth;
		mTexMatrix[5] = height / (float)mTexHeight;
	}
	
	/**
	 * Generate a texture for the color buffer
	 *
	 * 为颜色缓冲区生成纹理
	 * @param tex_target
	 * @param tex_unit
	 * @param tex_width
	 * @param tex_height
	 * @return
	 */
	private static int genTexture(final int tex_target, final int tex_unit,
		final int tex_width, final int tex_height) {
		// Generate a texture for the color buffer
		// 为颜色缓冲区生成纹理
		final int tex_name = GLHelper.initTex(tex_target, tex_unit,
			GLES20.GL_LINEAR, GLES20.GL_LINEAR, GLES20.GL_CLAMP_TO_EDGE);
		// Allocate the memory area for the texture
		// 为纹理分配内存区域
		GLES20.glTexImage2D(tex_target, 0, GLES20.GL_RGBA, tex_width, tex_height, 0,
			GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
		GLHelper.checkGlError("glTexImage2D");
		return tex_name;
	}
	
	/**
	 * Generate the framebuffer object for offscreen rendering
	 * 生成离屏绘制用的帧缓冲区对象
	 */
	private final void createFrameBuffer(final int width, final int height) {
		final int[] ids = new int[1];

		if (mAdjustPower2) {
			// Make the texture size a power of 2
			// 将纹理大小设为 2 的幂
			int w = 1;
			for (; w < width; w <<= 1) ;
			int h = 1;
			for (; h < height; h <<= 1) ;
			if (mTexWidth != w || mTexHeight != h) {
				mTexWidth = w;
				mTexHeight = h;
			}
		} else {
			mTexWidth = width;
			mTexHeight = height;
		}

		if (mHasDepthBuffer) {
			// If a depth buffer is needed, generate and initialize a renderbuffer object
			// 如果需要深度缓冲区,则生成并初始化渲染缓冲区对象
			GLES20.glGenRenderbuffers(1, ids, 0);
			mDepthBufferObj = ids[0];
			GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, mDepthBufferObj);
			// The depth buffer is 16 bits
			// 深度缓冲区为 16 位
			GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER,
				GLES20.GL_DEPTH_COMPONENT16, mTexWidth, mTexHeight);
		}
		// Generate the framebuffer object and bind it
		// 生成帧缓冲区对象并绑定
		GLES20.glGenFramebuffers(1, ids, 0);
		GLHelper.checkGlError("glGenFramebuffers");
		mFrameBufferObj = ids[0];
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBufferObj);
		GLHelper.checkGlError("glBindFramebuffer " + mFrameBufferObj);

		// Return to the default framebuffer
		// 返回默认的帧缓冲区
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

	}

	/**
	 * Destroy the offscreen framebuffer
	 * 销毁离屏帧缓冲区
	 */
    private final void releaseFrameBuffer() {
        final int[] names = new int[1];
		// If there is a depth buffer, destroy it
		// 如果存在深度缓冲区,则将其销毁
		if (mDepthBufferObj >= 0) {
			names[0] = mDepthBufferObj;
			GLES20.glDeleteRenderbuffers(1, names, 0);
			mDepthBufferObj = -1;
		}
		// Destroy the texture for the offscreen color buffer
		// 销毁离屏颜色缓冲区用的纹理
		if (mFBOTextureName >= 0) {
			names[0] = mFBOTextureName;
			GLES20.glDeleteTextures(1, names, 0);
			mFBOTextureName = -1;
		}
		// Destroy the offscreen framebuffer object
		// 销毁离屏帧缓冲区对象
		if (mFrameBufferObj >= 0) {
			names[0] = mFrameBufferObj;
			GLES20.glDeleteFramebuffers(1, names, 0);
			mFrameBufferObj = -1;
		}
    }

	/**
	 * get dimension(width) of this offscreen
	 *
	 * 获取此离屏的宽度
	 * @return
	 */
	public int getWidth() {
		return mWidth;
	}

	/**
	 * get dimension(height) of this offscreen
	 *
	 * 获取此离屏的高度
	 * @return
	 */
	public int getHeight() {
		return mHeight;
	}

	/**
	 * get backing texture dimension(width) of this offscreen
	 *
	 * 获取此离屏底层纹理的宽度
	 * @return
	 */
	public int getTexWidth() {
		return mTexWidth;
	}

	/**
	 * get backing texture dimension(height) of this offscreen
	 *
	 * 获取此离屏底层纹理的高度
	 * @return
	 */
	public int getTexHeight() {
		return mTexHeight;
	}
	
	public int getTexTarget() {
		return TEX_TARGET;
	}
	
	public int getTexUnit() {
		return TEX_UNIT;
	}
}
