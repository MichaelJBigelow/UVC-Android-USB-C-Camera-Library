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

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static com.serenegiant.opengl.ShaderConst.FRAGMENT_SHADER_SIMPLE;
import static com.serenegiant.opengl.ShaderConst.FRAGMENT_SHADER_SIMPLE_OES;
import static com.serenegiant.opengl.ShaderConst.GL_TEXTURE_2D;
import static com.serenegiant.opengl.ShaderConst.GL_TEXTURE_EXTERNAL_OES;
import static com.serenegiant.opengl.ShaderConst.VERTEX_SHADER;

/**
 * Helper class for drawing a texture in 2D over the entire drawing area
 *
 * 用于将纹理以 2D 方式绘制到整个绘制区域的辅助类
 */
public class GLDrawer2D implements IDrawer2D {
//	private static final boolean DEBUG = false; // FIXME set false on release
//	private static final String TAG = "GLDrawer2D";

	private static final float[] VERTICES = { 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f };
	private static final float[] TEXCOORD = { 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f };
	private static final int FLOAT_SZ = Float.SIZE / 8;

	private final int VERTEX_NUM;
	private final int VERTEX_SZ;
	private final FloatBuffer pVertex;
	private final FloatBuffer pTexCoord;
	private final int mTexTarget;
	private int hProgram;
    int maPositionLoc;
    int maTextureCoordLoc;
    int muMVPMatrixLoc;
    int muTexMatrixLoc;
	private final float[] mMvpMatrix = new float[16];

	/**
	 * Constructor
	 * Must be called while a GL context/EGL rendering context is valid
	 *
	 * 构造函数
	 * 必须在 GL 上下文/EGL 渲染上下文有效的状态下调用
	 * @param isOES true to use an external texture (GL_TEXTURE_EXTERNAL_OES), false for a normal 2D texture / 使用外部纹理(GL_TEXTURE_EXTERNAL_OES)时为 true，普通 2D 纹理为 false
	 */
	public GLDrawer2D(final boolean isOES) {
		this(VERTICES, TEXCOORD, isOES);
	}

	/**
	 * Constructor
	 * Must be called while a GL context/EGL rendering context is valid
	 *
	 * 构造函数
	 * 必须在 GL 上下文/EGL 渲染上下文有效的状态下调用
	 * @param vertices vertex coordinates, 8 floats = (x,y) x 4 pairs / 顶点坐标，8 个 float = (x,y) x 4 对
	 * @param texcoord texture coordinates, 8 floats = (s,t) x 4 pairs / 纹理坐标，8 个 float = (s,t) x 4 对
	 * @param isOES true to use an external texture (GL_TEXTURE_EXTERNAL_OES), false for a normal 2D texture / 使用外部纹理(GL_TEXTURE_EXTERNAL_OES)时为 true，普通 2D 纹理为 false
	 */
	public GLDrawer2D(final float[] vertices,
		final float[] texcoord, final boolean isOES) {

		VERTEX_NUM = Math.min(
			vertices != null ? vertices.length : 0,
			texcoord != null ? texcoord.length : 0) / 2;
		VERTEX_SZ = VERTEX_NUM * 2;

		mTexTarget = isOES ? GL_TEXTURE_EXTERNAL_OES : GL_TEXTURE_2D;
		pVertex = ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		pVertex.put(vertices);
		pVertex.flip();
		pTexCoord = ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		pTexCoord.put(texcoord);
		pTexCoord.flip();

		if (isOES) {
			hProgram = GLHelper.loadShader(VERTEX_SHADER, FRAGMENT_SHADER_SIMPLE_OES);
		} else {
			hProgram = GLHelper.loadShader(VERTEX_SHADER, FRAGMENT_SHADER_SIMPLE);
		}
		// initialize the model-view matrix
		// 初始化模型视图变换矩阵
		Matrix.setIdentityM(mMvpMatrix, 0);
		init();
	}

	/**
	 * Release resources. Must be called within the GL context/EGL rendering context
	 *
	 * 释放资源。必须在 GL 上下文/EGL 渲染上下文内调用
	 */
	@Override
	public void release() {
		if (hProgram >= 0) {
			GLES20.glDeleteProgram(hProgram);
		}
		hProgram = -1;
	}

	/**
	 * Whether an external texture is used
	 *
	 * 是否使用外部纹理
	 * @return
	 */
	public boolean isOES() {
		return mTexTarget == GL_TEXTURE_EXTERNAL_OES;
	}

	/**
	 * Get the model-view matrix (the internal array is returned directly, so be careful when modifying it)
	 *
	 * 获取模型视图变换矩阵（直接返回内部数组，修改时需要注意）
	 * @return
	 */
	@Override
	public float[] getMvpMatrix() {
		return mMvpMatrix;
	}

	/**
	 * Assign a matrix to the model-view matrix
	 *
	 * 将矩阵赋值给模型视图变换矩阵
	 * @param matrix bounds are not checked, so at least 16 elements from offset are required / 不做范围检查，因此从 offset 起必须至少有 16 个元素
	 * @param offset
	 * @return
	 */
	@Override
	public IDrawer2D setMvpMatrix(final float[] matrix, final int offset) {
		System.arraycopy(matrix, offset, mMvpMatrix, 0, 16);
		return this;
	}

	/**
	 * Get a copy of the model-view matrix
	 *
	 * 获取模型视图变换矩阵的副本
	 * @param matrix bounds are not checked, so at least 16 elements from offset are required / 不做范围检查，因此从 offset 起必须至少有 16 个元素
	 * @param offset
	 */
	@Override
	public void getMvpMatrix(final float[] matrix, final int offset) {
		System.arraycopy(mMvpMatrix, 0, matrix, offset, 16);
	}

	/**
	 * Helper method that draws the specified texture over the entire drawing area using the specified texture transform matrix
	 * If the model-view matrix of this class instance has been set, it is applied as well when drawing
	 *
	 * 用于使用指定的纹理变换矩阵将指定纹理绘制到整个绘制区域的辅助方法
	 * 如果已设置此类实例的模型视图变换矩阵，则绘制时也会应用该矩阵
	 * @param texId texture ID
	 * @param tex_matrix texture transform matrix; if null, the previously applied one is reused. Bounds are not checked, so allocate at least 16 elements from offset / 纹理变换矩阵，为 null 则复用之前应用的矩阵。不做范围检查，因此从 offset 起须至少保留 16 个元素
	 */
	@Override
	public synchronized void draw(final int texId,
		final float[] tex_matrix, final int offset) {

//		if (DEBUG) Log.v(TAG, "draw");
		if (hProgram < 0) return;
		GLES20.glUseProgram(hProgram);
		if (tex_matrix != null) {
			// when a texture transform matrix is specified
			// 指定了纹理变换矩阵时
			GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, tex_matrix, offset);
		}
		// set the model-view matrix
		// 设置模型视图变换矩阵
		GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMvpMatrix, 0);
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(mTexTarget, texId);
		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_NUM);
		GLES20.glBindTexture(mTexTarget, 0);
        GLES20.glUseProgram(0);
	}

	/**
	 * Helper method for drawing a Texture object
	 * Draws using the texture name and the texture coordinate transform matrix managed by the Texture object
	 *
	 * 用于绘制 Texture 对象的辅助方法
	 * 使用 Texture 对象所管理的纹理名和纹理坐标变换矩阵进行绘制
	 * @param texture
	 */
	@Override
	public void draw(final ITexture texture) {
		draw(texture.getTexture(), texture.getTexMatrix(), 0);
	}

	/**
	 * Helper method for drawing a TextureOffscreen object
	 *
	 * 用于绘制 TextureOffscreen 对象的辅助方法
	 * @param offscreen
	 */
	@Override
	public void draw(final TextureOffscreen offscreen) {
		draw(offscreen.getTexture(), offscreen.getTexMatrix(), 0);
	}

	/**
	 * Helper method for generating a texture name
	 * Simply calls GLHelper#initTex
	 *
	 * 生成纹理名的辅助方法
	 * 仅调用 GLHelper#initTex
	 * @return texture ID
	 */
	public int initTex() {
		return GLHelper.initTex(mTexTarget, GLES20.GL_NEAREST);
	}

	/**
	 * Helper method for deleting a texture name
	 * Simply calls GLHelper.deleteTex
	 *
	 * 删除纹理名的辅助方法
	 * 仅调用 GLHelper.deleteTex
	 * @param hTex
	 */
	public void deleteTex(final int hTex) {
		GLHelper.deleteTex(hTex);
	}

	/**
	 * Change the vertex shader and fragment shader
	 * Must be called within the GL context/EGL rendering context
	 * Returns with glUseProgram having been called
	 *
	 * 更改顶点着色器和片段着色器
	 * 必须在 GL 上下文/EGL 渲染上下文内调用
	 * 返回时已调用 glUseProgram
	 * @param vs vertex shader string / 顶点着色器字符串
	 * @param fs fragment shader string / 片段着色器字符串
	 */
	public synchronized void updateShader(final String vs, final String fs) {
		release();
		hProgram = GLHelper.loadShader(vs, fs);
		init();
	}

	/**
	 * Change the fragment shader
	 * Must be called within the GL context/EGL rendering context
	 * Returns with glUseProgram having been called
	 *
	 * 更改片段着色器
	 * 必须在 GL 上下文/EGL 渲染上下文内调用
	 * 返回时已调用 glUseProgram
	 * @param fs fragment shader string / 片段着色器字符串
	 */
	public void updateShader(final String fs) {
		updateShader(VERTEX_SHADER, fs);
	}

	/**
	 * Reset the vertex shader and fragment shader to the defaults
	 *
	 * 将顶点着色器和片段着色器恢复为默认值
	 */
	public void resetShader() {
		release();
		if (isOES()) {
			hProgram = GLHelper.loadShader(VERTEX_SHADER, FRAGMENT_SHADER_SIMPLE_OES);
		} else {
			hProgram = GLHelper.loadShader(VERTEX_SHADER, FRAGMENT_SHADER_SIMPLE);
		}
		init();
	}

	/**
	 * Get the location of an attribute variable
	 * Returns with glUseProgram having been called
	 *
	 * 获取 attribute 变量的位置
	 * 返回时已调用 glUseProgram
	 * @param name
	 * @return
	 */
	@Override
	public int glGetAttribLocation(final String name) {
		GLES20.glUseProgram(hProgram);
		return GLES20.glGetAttribLocation(hProgram, name);
	}

	/**
	 * Get the location of a uniform variable
	 * Returns with glUseProgram having been called
	 *
	 * 获取 uniform 变量的位置
	 * 返回时已调用 glUseProgram
	 * @param name
	 * @return
	 */
	@Override
	public int glGetUniformLocation(final String name) {
		GLES20.glUseProgram(hProgram);
		return GLES20.glGetUniformLocation(hProgram, name);
	}

	/**
	 * Returns with glUseProgram having been called
	 *
	 * 返回时已调用 glUseProgram
	 */
	@Override
	public void glUseProgram() {
		GLES20.glUseProgram(hProgram);
	}

	/**
	 * Initialization performed when the shader program is changed
	 * Returns with glUseProgram having been called
	 *
	 * 更改着色器程序时的初始化处理
	 * 返回时已调用 glUseProgram
	 */
	private void init() {
		GLES20.glUseProgram(hProgram);
		maPositionLoc = GLES20.glGetAttribLocation(hProgram, "aPosition");
		maTextureCoordLoc = GLES20.glGetAttribLocation(hProgram, "aTextureCoord");
		muMVPMatrixLoc = GLES20.glGetUniformLocation(hProgram, "uMVPMatrix");
		muTexMatrixLoc = GLES20.glGetUniformLocation(hProgram, "uTexMatrix");
		//
		GLES20.glUniformMatrix4fv(muMVPMatrixLoc,
			1, false, mMvpMatrix, 0);
		GLES20.glUniformMatrix4fv(muTexMatrixLoc,
			1, false, mMvpMatrix, 0);
		GLES20.glVertexAttribPointer(maPositionLoc,
			2, GLES20.GL_FLOAT, false, VERTEX_SZ, pVertex);
		GLES20.glVertexAttribPointer(maTextureCoordLoc,
			2, GLES20.GL_FLOAT, false, VERTEX_SZ, pTexCoord);
		GLES20.glEnableVertexAttribArray(maPositionLoc);
		GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
	}
}
