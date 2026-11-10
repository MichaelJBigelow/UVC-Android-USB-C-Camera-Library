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
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.text.TextUtils;

import java.io.IOException;

/**
 * Helper class for OpenGL|ES texture operations
 *
 * OpenGL|ES 纹理操作辅助类
 */
public class GLTexture implements ITexture {
//	private static final boolean DEBUG = false;	// FIXME 実働時はfalseにすること
//	private static final String TAG = "GLTexture";

	/* package */final int mTextureTarget;
	/* package */final int mTextureUnit ;
	/* package */int mTextureId;
	/* package */final float[] mTexMatrix = new float[16];	// Texture transform matrix / 纹理变换矩阵
	/* package */int mTexWidth, mTexHeight;
	/* package */int mImageWidth, mImageHeight;
	
	/**
	 * Constructor
	 * The texture unit is always GL_TEXTURE0, so multiple textures cannot be used at the same time
	 *
	 * 构造函数
	 * 纹理单元始终为 GL_TEXTURE0，因此无法同时使用多个纹理
	 * @param width Texture size / 纹理尺寸
	 * @param height Texture size / 纹理尺寸
	 * @param filter_param	Specifies the texture interpolation method, e.g. GL_LINEAR or GL_NEAREST / 指定纹理插值方式，例如 GL_LINEAR 或 GL_NEAREST
	 */
	public GLTexture(final int width, final int height, final int filter_param) {
		this(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE0, width, height, filter_param);
	}
	
	/**
	 * Constructor
	 *
	 * 构造函数
	 * @param texTarget GL_TEXTURE_EXTERNAL_OES is not allowed / 不能使用 GL_TEXTURE_EXTERNAL_OES
	 * @param texUnit
	 * @param width Texture size / 纹理尺寸
	 * @param height Texture size / 纹理尺寸
	 * @param filter_param	Specifies the texture interpolation method, e.g. GL_LINEAR or GL_NEAREST / 指定纹理插值方式，例如 GL_LINEAR 或 GL_NEAREST
	 */
	public GLTexture(final int texTarget, final int texUnit,
		final int width, final int height, final int filter_param) {
//		if (DEBUG) Log.v(TAG, String.format("コンストラクタ(%d,%d)", width, height));
		mTextureTarget = texTarget;
		mTextureUnit = texUnit;
		// The bitmap used for the texture must have power-of-two width and height.
		// Furthermore, if mipmapping is used, it must be square.
		// Round up to a power of two that is equal to or larger than the specified width/height
		// 用于纹理的位图，其宽高必须是 2 的幂。
		// 此外，如果要使用 mipmap，则必须是正方形。
		// 取大于或等于指定 width/height 的 2 的幂
		int w = 32;
		for (; w < width; w <<= 1);
		int h = 32;
		for (; h < height; h <<= 1);
		if (mTexWidth != w || mTexHeight != h) {
			mTexWidth = w;
			mTexHeight = h;
		}
//		if (DEBUG) Log.v(TAG, String.format("texSize(%d,%d)", mTexWidth, mTexHeight));
		mTextureId = GLHelper.initTex(mTextureTarget, filter_param);
		// Allocate memory for the texture
		// 为纹理分配内存
		GLES20.glTexImage2D(mTextureTarget,
			0,							// Mipmap level 0 (no mipmap) / Mipmap 级别 0（不使用 mipmap）
			GLES20.GL_RGBA,				// Internal format / 内部格式
			mTexWidth, mTexHeight,		// Size / 尺寸
			0,							// Border width / 边框宽度
			GLES20.GL_RGBA,				// Format of the supplied data / 传入数据的格式
			GLES20.GL_UNSIGNED_BYTE,	// Data type / 数据类型
			null);						// No pixel data / 无像素数据
		// Initialize the texture transform matrix
		// 初始化纹理变换矩阵
		Matrix.setIdentityM(mTexMatrix, 0);
		mTexMatrix[0] = width / (float)mTexWidth;
		mTexMatrix[5] = height / (float)mTexHeight;
//		if (DEBUG) Log.v(TAG, "GLTexture:id=" + mTextureId);
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			release();	// Not ideal, since we may not be inside a GL context / 不太理想，因为此时可能不在 GL 上下文中
		} finally {
			super.finalize();
		}
	}

	/**
	 * Destroys the texture
	 * Must be called inside a GL context / EGL rendering context
	 *
	 * 销毁纹理
	 * 必须在 GL 上下文/EGL 渲染上下文中调用
	 */
	@Override
	public void release() {
//		if (DEBUG) Log.v(TAG, "release:");
		if (mTextureId > 0) {
			GLHelper.deleteTex(mTextureId);
			mTextureId = 0;
		}
	}

	/**
	 * Enables (binds) the texture managed by this instance
	 *
	 * 启用（绑定）此实例所管理的纹理
	 */
	@Override
	public void bind() {
//		if (DEBUG) Log.v(TAG, "bind:");
		GLES20.glActiveTexture(mTextureUnit);	// Select the texture unit / 选择纹理单元
		GLES20.glBindTexture(mTextureTarget, mTextureId);
	}

	/**
	 * Disables (unbinds) the texture managed by this instance
	 *
	 * 禁用（解绑）此实例所管理的纹理
	 */
	@Override
	public void unbind() {
//		if (DEBUG) Log.v(TAG, "unbind:");
		GLES20.glActiveTexture(mTextureUnit);	// Select the texture unit / 选择纹理单元
		GLES20.glBindTexture(mTextureTarget, 0);
	}

	/**
	 * Gets the texture target (GL_TEXTURE_2D)
	 *
	 * 获取纹理目标（GL_TEXTURE_2D）
	 * @return
	 */
	@Override
	public int getTexTarget() { return mTextureTarget; }
	/**
	 * Gets the texture name
	 *
	 * 获取纹理名
	 * @return
	 */
	@Override
	public int getTexture() { return mTextureId; }
	/**
	 * Gets the texture coordinate transform matrix (the internal array is returned as is, so be careful when modifying it)
	 *
	 * 获取纹理坐标变换矩阵（直接返回内部数组，修改时需格外小心）
	 * @return
	 */
	@Override
	public float[] getTexMatrix() { return mTexMatrix; }
	/**
	 * Gets a copy of the texture coordinate transform matrix
	 *
	 * 获取纹理坐标变换矩阵的副本
	 * @param matrix The size is not checked, so reserve at least 16 elements from the offset position / 不会检查大小，因此需从 offset 位置起预留至少 16 个元素
	 * @param offset
	 */
	@Override
	public void getTexMatrix(final float[] matrix, final int offset) {
		System.arraycopy(mTexMatrix, 0, matrix, offset, mTexMatrix.length);
	}
	/**
	 * Gets the texture width
	 *
	 * 获取纹理宽度
	 * @return
	 */
	@Override
	public int getTexWidth() { return mTexWidth; }
	/**
	 * Gets the texture height
	 *
	 * 获取纹理高度
	 * @return
	 */
	@Override
	public int getTexHeight() { return mTexHeight; }

	/**
	 * Loads an image from the specified file into the texture
	 * Throws IOException/NullPointerException if the file does not exist or cannot be read
	 *
	 * 将指定文件中的图像加载到纹理中
	 * 如果文件不存在或无法读取，则抛出 IOException/NullPointerException
	 * @param filePath
	 */
	@Override
	public void loadTexture(final String filePath) throws NullPointerException, IOException {
//		if (DEBUG) Log.v(TAG, "loadTexture:path=" + filePath);
		if (TextUtils.isEmpty(filePath))
			throw new NullPointerException("image file path should not be a null");
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;	// Get only information such as size without creating a Bitmap / 不生成 Bitmap，仅获取尺寸等信息
		BitmapFactory.decodeFile(filePath, options);
		// Calculate the subsampling value so that the specified image fits within the texture size
		// 计算子采样值，使指定图像能放入纹理尺寸内
		final int imageWidth = options.outWidth;
		final int imageHeight = options.outHeight;
		int inSampleSize = 1;	// Subsampling size / 子采样大小
		if ((imageHeight > mTexHeight) || (imageWidth > mTexWidth)) {
			if (imageWidth > imageHeight) {
				inSampleSize = (int)Math.ceil(imageHeight / (float)mTexHeight);
			} else {
				inSampleSize = (int)Math.ceil(imageWidth / (float)mTexWidth);
			}
		}
//		if (DEBUG) Log.v(TAG, String.format("image(%d,%d),tex(%d,%d),inSampleSize=%d",
// 			imageWidth, imageHeight, mTexWidth, mTexHeight, inSampleSize));
		// The actual loading process
		// 实际的加载处理
		options.inSampleSize = inSampleSize;
		options.inJustDecodeBounds = false;
		loadTexture(BitmapFactory.decodeFile(filePath, options));
	}
	
	/**
	 * Loads the specified bitmap into the texture
	 *
	 * 将指定的位图加载到纹理中
 	 * @param bitmap
	 */
	public void loadTexture(final Bitmap bitmap) throws NullPointerException {
		mImageWidth = bitmap.getWidth();	// Get the size of the loaded image / 获取已加载图像的尺寸
		mImageHeight = bitmap.getHeight();
		Bitmap texture = Bitmap.createBitmap(mTexWidth, mTexHeight, Bitmap.Config.ARGB_8888);
		final Canvas canvas = new Canvas(texture);
		canvas.drawBitmap(bitmap, 0, 0, null);
		bitmap.recycle();
		// Set the texture coordinate transform matrix (scaled so that the loaded image size fits the texture size)
		// 设置纹理坐标变换矩阵（进行缩放变换，使已加载图像的尺寸适配纹理尺寸）
		Matrix.setIdentityM(mTexMatrix, 0);
		mTexMatrix[0] = mImageWidth / (float)mTexWidth;
		mTexMatrix[5] = mImageHeight / (float)mTexHeight;
//		if (DEBUG) Log.v(TAG, String.format("image(%d,%d),scale(%f,%f)",
// 			mImageWidth, mImageHeight, mMvpMatrix[0], mMvpMatrix[5]));
		bind();
		GLUtils.texImage2D(mTextureTarget, 0, texture, 0);
		unbind();
		texture.recycle();
	}
}
