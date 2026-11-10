package com.serenegiant.opengl.renderer;

import android.graphics.SurfaceTexture;

import androidx.annotation.NonNull;

import android.net.Uri;
import android.view.Surface;

/**
 * Hold shared texture that receive camera frame and draw them to registered surface if needs
 *
 * 持有接收相机帧的共享纹理,并在需要时将其绘制到已注册的 surface
 */
public interface IRendererHolder {

    boolean isRunning();

    void release();

    /**
     * Get Surface that receive camera frame.
     *
     * 获取接收相机帧的 Surface。
     *
     * @return The Surface must be used in UVCCamera.setPreviewDisplay() method / 该 Surface 必须用于 UVCCamera.setPreviewDisplay() 方法
     */
    Surface getPrimarySurface();

    /**
     * Get SurfaceTexture that receive camera frame.
     *
     * 获取接收相机帧的 SurfaceTexture。
     *
     * @return The SurfaceTexture must be used in UVCCamera.setPreviewDisplay() method / 该 SurfaceTexture 必须用于 UVCCamera.setPreviewDisplay() 方法
     */
    SurfaceTexture getPrimarySurfaceTexture();

    /**
     * Check whether Primary Surface is valid, if invalid recreate Primary Surface
     *
     * 检查 Primary Surface 是否有效,如果无效则重新创建 Primary Surface
     */
    void checkPrimarySurface();

    /**
     * Change size of Primary Surface
     *
     * 更改 Primary Surface 的尺寸
     *
     * @param width
     * @param height
     */
    void updatePrimarySize(final int width, final int height)
            throws IllegalStateException;

    /**
     * Add slave surface that is a mirror of primary surface
     *
     * 添加作为 primary surface 镜像的 slave surface
     *
     * @param id           often use #hashCode. / 通常使用 #hashCode。
     * @param surface,     should be one of Surface, SurfaceTexture or SurfaceHolder / 应为 Surface、SurfaceTexture 或 SurfaceHolder 之一
     * @param isRecordable
     */
    void addSlaveSurface(final int id, final Object surface,
                         final boolean isRecordable)
            throws IllegalStateException, IllegalArgumentException;

    /**
     * Add slave surface that is a mirror of primary surface
     *
     * 添加作为 primary surface 镜像的 slave surface
     *
     * @param id           often use #hashCode. / 通常使用 #hashCode。
     * @param surface,     should be one of Surface, SurfaceTexture or SurfaceHolder / 应为 Surface、SurfaceTexture 或 SurfaceHolder 之一
     * @param isRecordable
     * @param maxFps       no limit if it is less than zero / 小于零时不限制
     */
    void addSlaveSurface(final int id, final Object surface,
                         final boolean isRecordable, final int maxFps)
            throws IllegalStateException, IllegalArgumentException;

    /**
     * Remove specific slave surface
     *
     * 移除指定的 slave surface
     *
     * @param id
     */
    void removeSlaveSurface(final int id);

    /**
     * Remove all slave surface
     *
     * 移除所有 slave surface
     */
    void removeSlaveSurfaceAll();

    /**
     * Fill specific slave Surface with specific color
     *
     * 用指定颜色填充指定的 slave Surface
     *
     * @param id
     * @param color
     */
    void clearSlaveSurface(final int id, final int color);

    /**
     * Fill all slave Surface with specific color
     *
     * 用指定颜色填充所有 slave Surface
     *
     * @param color
     */
    void clearSlaveSurfaceAll(final int color);

    /**
     * Get state of slave surface
     *
     * 获取 slave surface 的状态
     *
     * @param id
     * @return
     */
    boolean isSlaveSurfaceEnable(final int id);

    /**
     * Set state of slave surface
     *
     * 设置 slave surface 的状态
     *
     * @param id
     * @param enable
     */
    void setSlaveSurfaceEnable(final int id, final boolean enable);

    void rotateTo(final int angle);

    void rotateBy(final int angle);

    /**
     * Set slave surface's Mirror Mode that flip image horizontally, or flip image vertically.
     *
     * 设置 slave surface 的镜像模式,即水平翻转图像或垂直翻转图像。
     *
     * @param mode 0:normal, 1:flip horizontally, 2:flip vertically, 3:flip horizontal direction and vertical direction / 0:正常,1:水平翻转,2:垂直翻转,3:水平和垂直方向同时翻转
     */
    void setMirrorMode(@MirrorMode final int mode);

    /**
     * Get slave surface's Mirror Mode that flip image horizontally, or flip image vertically.
     *
     * 获取 slave surface 的镜像模式,即水平翻转图像或垂直翻转图像。
     *
     * @return mode 0:normal, 1:flip horizontally, 2:flip vertically, 3:flip horizontal direction and vertical direction / 模式 0:正常,1:水平翻转,2:垂直翻转,3:水平和垂直方向同时翻转
     */
    @MirrorMode
    int getMirrorMode();

    /**
     * Update all slave surface based on master surface immediately
     *
     * 立即根据 master surface 更新所有 slave surface
     */
    void requestFrame();
}
