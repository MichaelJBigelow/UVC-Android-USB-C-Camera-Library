package com.herohan.uvcapp

import android.os.Bundle
import com.serenegiant.opengl.renderer.MirrorMode

class CameraPreviewConfig internal constructor() : Cloneable {

    private var mMutableConfig = Bundle()

    /**
     * Sets the clockwise rotation angle in degrees relative to the
     * orientation of the camera. This affects the pictures returned from
     * ImageCapture and mp4 returned from VideoCapture.
     *
     * 设置相对于相机方向的顺时针旋转角度（单位：度）。
     * 这会影响 ImageCapture 返回的图片以及 VideoCapture 返回的 mp4。
     *
     * @param rotation The rotation angle in degrees relative to the
     *                 orientation of the camera. Rotation can only be 0,
     *                 90, 180 or 270. /
     *                 相对于相机方向的旋转角度（单位：度），只能是 0、90、180 或 270。
     * @throws IllegalArgumentException if rotation value is invalid. / 旋转值无效时抛出。
     */
    fun setRotation(rotation: Int): CameraPreviewConfig {
        require(rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270) {
            "Invalid rotation=$rotation"
        }
        mMutableConfig.putInt(OPTION_ROTATION, rotation)
        return this
    }

    val rotation: Int
        get() = mMutableConfig.getInt(OPTION_ROTATION, DEFAULT_ROTATION)

    /**
     * Sets the mirror mode of the camera. This affects the pictures returned from
     * ImageCapture and mp4 returned from VideoCapture.
     *
     * 设置相机的镜像模式。
     * 这会影响 ImageCapture 返回的图片以及 VideoCapture 返回的 mp4。
     */
    fun setMirror(@MirrorMode mirror: Int): CameraPreviewConfig {
        mMutableConfig.putInt(OPTION_MIRROR, mirror)
        return this
    }

    @get:MirrorMode
    val mirror: Int
        get() = mMutableConfig.getInt(OPTION_MIRROR, DEFAULT_MIRROR)

    public override fun clone(): CameraPreviewConfig =
        (super.clone() as CameraPreviewConfig).also {
            it.mMutableConfig = mMutableConfig.clone() as Bundle
        }

    companion object {
        private const val OPTION_ROTATION = "cameraPreview.rotation"
        private const val OPTION_MIRROR = "cameraPreview.mirror"

        const val DEFAULT_ROTATION = 0
        const val DEFAULT_MIRROR = MirrorMode.MIRROR_NORMAL
    }
}
