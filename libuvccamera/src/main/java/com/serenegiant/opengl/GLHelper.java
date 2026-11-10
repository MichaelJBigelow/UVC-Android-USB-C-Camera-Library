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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLUtils;

import androidx.annotation.NonNull;

import android.util.Log;

import com.serenegiant.utils.AssetsHelper;

import java.io.IOException;

/**
 * Helper class for OpenGL|ES2/3
 *
 * OpenGL|ES2/3 辅助类
 */
public final class GLHelper {
    //	private static final boolean DEBUG = false;	// FIXME 実働時はfalseにすること
    private static final String TAG = "GLHelper";

    /**
     * Checks for OpenGL|ES errors and outputs them to logCat
     *
     * 检查 OpenGL|ES 错误并输出到 logCat
     *
     * @param op
     */
    public static void checkGlError(final String op) {
        final int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            final String msg = op + ": glError 0x" + Integer.toHexString(error);
            Log.e(TAG, msg);
            new Throwable(msg).printStackTrace();
//         	if (DEBUG) {
//	            throw new RuntimeException(msg);
//       	}
        }
    }

    /**
     * Generates a texture name; the texture unit is GL_TEXTURE0 and the clamp mode is GL_CLAMP_TO_EDGE
     *
     * 生成纹理名，纹理单元为 GL_TEXTURE0，钳制方式为 GL_CLAMP_TO_EDGE
     *
     * @param texTarget
     * @param filter_param Specifies the texture interpolation method; min/mag get the same value, e.g. GL_LINEAR or GL_NEAREST / 指定纹理插值方式，min/mag 使用相同的值，例如 GL_LINEAR 或 GL_NEAREST
     * @return
     */
    public static int initTex(final int texTarget, final int filter_param) {
        return initTex(texTarget, GLES20.GL_TEXTURE0,
                filter_param, filter_param, GLES20.GL_CLAMP_TO_EDGE);
    }

    /**
     * Generates a texture name (GL_TEXTURE0 only)
     *
     * 生成纹理名（仅限 GL_TEXTURE0）
     *
     * @param texTarget
     * @param texUnit    Texture unit, GL_TEXTURE0...GL_TEXTURE31 / 纹理单元，GL_TEXTURE0...GL_TEXTURE31
     * @param min_filter Specifies the texture interpolation method, e.g. GL_LINEAR or GL_NEAREST / 指定纹理插值方式，例如 GL_LINEAR 或 GL_NEAREST
     * @param mag_filter Specifies the texture interpolation method, e.g. GL_LINEAR or GL_NEAREST / 指定纹理插值方式，例如 GL_LINEAR 或 GL_NEAREST
     * @param wrap       Texture clamp (wrap) mode, GL_CLAMP_TO_EDGE / 纹理钳制方式，GL_CLAMP_TO_EDGE
     * @return
     */
    public static int initTex(final int texTarget, final int texUnit,
                              final int min_filter, final int mag_filter, final int wrap) {

//		if (DEBUG) Log.v(TAG, "initTex:target=" + texTarget);
        final int[] tex = new int[1];
        GLES20.glActiveTexture(texUnit);
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(texTarget, tex[0]);
        GLES20.glTexParameteri(texTarget, GLES20.GL_TEXTURE_WRAP_S, wrap);
        GLES20.glTexParameteri(texTarget, GLES20.GL_TEXTURE_WRAP_T, wrap);
        GLES20.glTexParameteri(texTarget, GLES20.GL_TEXTURE_MIN_FILTER, min_filter);
        GLES20.glTexParameteri(texTarget, GLES20.GL_TEXTURE_MAG_FILTER, mag_filter);
        return tex[0];
    }

    /**
     * Generates an array of texture names (GL_TEXTURE0, GL_TEXTURE1, ... in order from the front)
     *
     * 生成纹理名数组（从前往后依次为 GL_TEXTURE0、GL_TEXTURE1、...）
     *
     * @param n            Number of texture names to generate, up to 32 (no more than GL_MAX_TEXTURE_IMAGE_UNITS) / 要生成的纹理名数量，最多 32 个（不超过 GL_MAX_TEXTURE_IMAGE_UNITS）
     * @param texTarget
     * @param filter_param
     * @return
     */
    public static int[] initTexes(final int n,
                                  final int texTarget, final int filter_param) {

        return initTexes(new int[n], texTarget,
                filter_param, filter_param, GLES20.GL_CLAMP_TO_EDGE);
    }

    /**
     * Generates an array of texture names (GL_TEXTURE0, GL_TEXTURE1, ... in order from the front)
     *
     * 生成纹理名数组（从前往后依次为 GL_TEXTURE0、GL_TEXTURE1、...）
     *
     * @param texIds       Array of texture names, up to 32 (no more than GL_MAX_TEXTURE_IMAGE_UNITS) / 纹理名数组，最多 32 个（不超过 GL_MAX_TEXTURE_IMAGE_UNITS）
     * @param texTarget
     * @param filter_param
     * @return
     */
    public static int[] initTexes(@NonNull final int[] texIds,
                                  final int texTarget, final int filter_param) {

        return initTexes(texIds, texTarget,
                filter_param, filter_param, GLES20.GL_CLAMP_TO_EDGE);
    }

    /**
     * Generates an array of texture names (GL_TEXTURE0, GL_TEXTURE1, ... in order from the front)
     *
     * 生成纹理名数组（从前往后依次为 GL_TEXTURE0、GL_TEXTURE1、...）
     *
     * @param n          Number of texture names to generate, up to 32 / 要生成的纹理名数量，最多 32 个
     * @param texTarget
     * @param min_filter
     * @param mag_filter
     * @param wrap
     * @return
     */
    public static int[] initTexes(final int n,
                                  final int texTarget, final int min_filter, final int mag_filter, final int wrap) {

        return initTexes(new int[n], texTarget, min_filter, mag_filter, wrap);
    }

    /**
     * Generates an array of texture names (GL_TEXTURE0, GL_TEXTURE1, ... in order from the front)
     *
     * 生成纹理名数组（从前往后依次为 GL_TEXTURE0、GL_TEXTURE1、...）
     *
     * @param texIds     Array of texture names, up to 32 (no more than GL_MAX_TEXTURE_IMAGE_UNITS) / 纹理名数组，最多 32 个（不超过 GL_MAX_TEXTURE_IMAGE_UNITS）
     * @param texTarget
     * @param min_filter
     * @param mag_filter
     * @param wrap
     * @return
     */
    public static int[] initTexes(@NonNull final int[] texIds,
                                  final int texTarget, final int min_filter, final int mag_filter, final int wrap) {

        int[] textureUnits = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_IMAGE_UNITS, textureUnits, 0);
        Log.v(TAG, "GL_MAX_TEXTURE_IMAGE_UNITS=" + textureUnits[0]);
        final int n = texIds.length > textureUnits[0]
                ? textureUnits[0] : texIds.length;
        for (int i = 0; i < n; i++) {
            texIds[i] = GLHelper.initTex(texTarget, ShaderConst.TEX_NUMBERS[i],
                    min_filter, mag_filter, wrap);
        }
        return texIds;
    }

    /**
     * Generates an array of texture names (this variant uses the same texture unit for all of them)
     *
     * 生成纹理名数组（此版本所有纹理使用同一个纹理单元）
     *
     * @param n          Up to 32 (no more than GL_MAX_TEXTURE_IMAGE_UNITS) / 最多 32 个（不超过 GL_MAX_TEXTURE_IMAGE_UNITS）
     * @param texTarget
     * @param texUnit
     * @param min_filter
     * @param mag_filter
     * @param wrap
     * @return
     */
    public static int[] initTexes(final int n,
                                  final int texTarget, final int texUnit,
                                  final int min_filter, final int mag_filter, final int wrap) {

        return initTexes(new int[n], texTarget, texUnit,
                min_filter, mag_filter, wrap);
    }

    /**
     * Generates an array of texture names (this variant uses the same texture unit for all of them)
     *
     * 生成纹理名数组（此版本所有纹理使用同一个纹理单元）
     *
     * @param texIds       Up to 32 (no more than GL_MAX_TEXTURE_IMAGE_UNITS) / 最多 32 个（不超过 GL_MAX_TEXTURE_IMAGE_UNITS）
     * @param texTarget
     * @param texUnit
     * @param filter_param
     * @return
     */
    public static int[] initTexes(@NonNull final int[] texIds,
                                  final int texTarget, final int texUnit, final int filter_param) {

        return initTexes(texIds, texTarget, texUnit,
                filter_param, filter_param, GLES20.GL_CLAMP_TO_EDGE);
    }

    /**
     * Generates an array of texture names (this variant uses the same texture unit for all of them)
     *
     * 生成纹理名数组（此版本所有纹理使用同一个纹理单元）
     *
     * @param texIds
     * @param texTarget
     * @param texUnit
     * @param min_filter
     * @param mag_filter
     * @param wrap
     * @return
     */
    public static int[] initTexes(@NonNull final int[] texIds,
                                  final int texTarget, final int texUnit,
                                  final int min_filter, final int mag_filter, final int wrap) {

        int[] textureUnits = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_IMAGE_UNITS, textureUnits, 0);
        final int n = texIds.length > textureUnits[0]
                ? textureUnits[0] : texIds.length;
        for (int i = 0; i < n; i++) {
            texIds[i] = GLHelper.initTex(texTarget, texUnit,
                    min_filter, mag_filter, wrap);
        }
        return texIds;
    }

    /**
     * delete specific texture
     *
     * 删除指定的纹理
     */
    public static void deleteTex(final int hTex) {
//		if (DEBUG) Log.v(TAG, "deleteTex:");
        final int[] tex = new int[]{hTex};
        GLES20.glDeleteTextures(1, tex, 0);
    }

    /**
     * delete specific texture
     *
     * 删除指定的纹理
     */
    public static void deleteTex(@NonNull final int[] tex) {
//		if (DEBUG) Log.v(TAG, "deleteTex:");
        GLES20.glDeleteTextures(tex.length, tex, 0);
    }

    public static int loadTextureFromResource(final Context context, final int resId) {
        return loadTextureFromResource(context, resId, null);
    }

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    public static int loadTextureFromResource(final Context context, final int resId, final Resources.Theme theme) {
        // Create an empty, mutable bitmap
        // 创建一个空的、可变的 bitmap
        final Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        // get a canvas to paint over the bitmap
        // 获取用于在 bitmap 上绘制的 canvas
        final Canvas canvas = new Canvas(bitmap);
        canvas.drawARGB(0, 0, 255, 0);

        // get a background image from resources
        // note the image format must match the bitmap format
        // 从资源中获取背景图像
        // 注意图像格式必须与 bitmap 格式一致
        final Drawable background = context.getResources().getDrawable(resId, theme);
        background.setBounds(0, 0, 256, 256);
        background.draw(canvas); // draw the background to our bitmap / 将背景绘制到我们的 bitmap 上

        final int[] textures = new int[1];

        //Generate one texture pointer...
        // 生成一个纹理指针...
        GLES20.glGenTextures(1, textures, 0);
        //...and bind it to our array
        // ...并将其绑定到我们的数组
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

        //Create Nearest Filtered Texture
        // 创建最近邻过滤的纹理
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        //Different possible texture parameters, e.g. GLES20.GL_CLAMP_TO_EDGE
        // 其他可选的纹理参数，例如 GLES20.GL_CLAMP_TO_EDGE
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

        //Use the Android GLUtils to specify a two-dimensional texture image from our bitmap
        // 使用 Android GLUtils 通过我们的 bitmap 指定二维纹理图像
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        //Clean up
        // 清理
        bitmap.recycle();

        return textures[0];
    }

    public static int createTextureWithTextContent(final String text) {
        // Create an empty, mutable bitmap
        // 创建一个空的、可变的 bitmap
        final Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        // get a canvas to paint over the bitmap
        // 获取用于在 bitmap 上绘制的 canvas
        final Canvas canvas = new Canvas(bitmap);
        canvas.drawARGB(0, 0, 255, 0);

        // Draw the text
        // 绘制文本
        final Paint textPaint = new Paint();
        textPaint.setTextSize(32);
        textPaint.setAntiAlias(true);
        textPaint.setARGB(0xff, 0xff, 0xff, 0xff);
        // draw the text centered
        // 将文本居中绘制
        canvas.drawText(text, 16, 112, textPaint);

        final int texture = initTex(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE0, GLES20.GL_NEAREST, GLES20.GL_LINEAR, GLES20.GL_REPEAT);

        // Alpha blending
        // Alpha 混合
        // GLES20.glEnable(GLES20.GL_BLEND);
        // GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Use the Android GLUtils to specify a two-dimensional texture image from our bitmap
        // 使用 Android GLUtils 通过我们的 bitmap 指定二维纹理图像
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        // Clean up
        // 清理
        bitmap.recycle();

        return texture;
    }

    /**
     * load, compile and link shader from Assets files
     *
     * 从 Assets 文件加载、编译并链接着色器
     *
     * @param context
     * @param vss_asset source file name in Assets of vertex shader / 顶点着色器在 Assets 中的源文件名
     * @param fss_asset source file name in Assets of fragment shader / 片段着色器在 Assets 中的源文件名
     * @return
     */
    public static int loadShader(@NonNull final Context context,
                                 final String vss_asset, final String fss_asset) {

        int program = 0;
        try {
            final String vss = AssetsHelper.loadString(context.getAssets(), vss_asset);
            final String fss = AssetsHelper.loadString(context.getAssets(), vss_asset);
            program = loadShader(vss, fss);
        } catch (IOException e) {
        }
        return program;
    }

    /**
     * load, compile and link shader
     *
     * 加载、编译并链接着色器
     *
     * @param vss source of vertex shader / 顶点着色器源码
     * @param fss source of fragment shader / 片段着色器源码
     * @return
     */
    public static int loadShader(final String vss, final String fss) {
//		if (DEBUG) Log.v(TAG, "loadShader:");
        final int[] compiled = new int[1];
        // Compile the vertex shader
        // 编译顶点着色器
        final int vs = loadShader(GLES20.GL_VERTEX_SHADER, vss);
        if (vs == 0) {
            return 0;
        }
        // Compile the fragment shader
        // 编译片段着色器
        int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fss);
        if (fs == 0) {
            return 0;
        }
        // Link
        // 链接
        final int program = GLES20.glCreateProgram();
        checkGlError("glCreateProgram");
        if (program == 0) {
            Log.e(TAG, "Could not create program");
        }
        GLES20.glAttachShader(program, vs);
        checkGlError("glAttachShader");
        GLES20.glAttachShader(program, fs);
        checkGlError("glAttachShader");
        GLES20.glLinkProgram(program);
        final int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    /**
     * Compiles the provided shader source.
     *
     * 编译所提供的着色器源码。
     *
     * @return A handle to the shader, or 0 on failure. / 着色器句柄，失败时返回 0。
     */
    public static int loadShader(final int shaderType, final String source) {
        int shader = GLES20.glCreateShader(shaderType);
        checkGlError("glCreateShader type=" + shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        final int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + shaderType + ":");
            Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    /**
     * Checks to see if the location we obtained is valid.  GLES returns -1 if a label
     * could not be found, but does not set the GL error.
     * <p>
     * Throws a RuntimeException if the location is invalid.
     * <p>
     * 检查我们获取到的 location 是否有效。如果找不到标签，GLES 返回 -1，但不会设置 GL 错误。
     * <p>
     * 如果 location 无效，则抛出 RuntimeException。
     */
    public static void checkLocation(final int location, final String label) {
        if (location < 0) {
            throw new RuntimeException("Unable to locate '" + label + "' in program");
        }
    }

    /**
     * Writes GL version info to the log.
     *
     * 将 GL 版本信息写入日志。
     */
    @SuppressLint("InlinedApi")
    public static void logVersionInfo() {
        Log.i(TAG, "vendor  : " + GLES20.glGetString(GLES20.GL_VENDOR));
        Log.i(TAG, "renderer: " + GLES20.glGetString(GLES20.GL_RENDERER));
        Log.i(TAG, "version : " + GLES20.glGetString(GLES20.GL_VERSION));

        final int[] values = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_MAJOR_VERSION, values, 0);
        final int majorVersion = values[0];
        GLES30.glGetIntegerv(GLES30.GL_MINOR_VERSION, values, 0);
        final int minorVersion = values[0];
        if (GLES30.glGetError() == GLES30.GL_NO_ERROR) {
            Log.i(TAG, "version: " + majorVersion + "." + minorVersion);
        }
    }
}
