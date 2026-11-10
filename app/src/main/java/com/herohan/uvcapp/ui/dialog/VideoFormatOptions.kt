package com.herohan.uvcapp.ui.dialog

import com.serenegiant.usb.Format
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCCamera

/**
 * A preview resolution, in pixels.
 *
 * 预览分辨率(像素)。
 */
data class Resolution(val width: Int, val height: Int) {
    override fun toString(): String = "${width}x$height"
}

/**
 * The format, resolution and frame rate picked in the video format dialog.
 *
 * 在视频格式对话框中选择的格式、分辨率和帧率。
 *
 * @param frameType UVC frame type, [UVCCamera.UVC_VS_FRAME_MJPEG] or [UVCCamera.UVC_VS_FRAME_UNCOMPRESSED] / UVC 帧类型
 */
data class VideoFormatSelection(val frameType: Int, val resolution: Resolution, val fps: Int) {
    /**
     * A copy of [size] set to this format, resolution and frame rate; [size] itself is not changed.
     *
     * 将 [size] 复制一份并设为本选择的格式、分辨率和帧率；[size] 本身不会改变。
     */
    fun applyTo(size: Size): Size = size.clone().also {
        it.type = frameType
        it.width = resolution.width
        it.height = resolution.height
        it.fps = fps
    }
}

/**
 * The formats, resolutions and frame rates a camera supports, as offered by the video format dialog.
 *
 * Only MJPEG and uncompressed (YUV) formats are offered, the two the library can preview.
 *
 * 相机支持的格式、分辨率和帧率，供视频格式对话框选择。
 *
 * 仅提供 MJPEG 和未压缩(YUV)格式，即本库能够预览的两种格式。
 */
class VideoFormatOptions(formats: List<Format>) {

    /**
     * Frame type → resolution → supported frame rates, in the order the camera reports them.
     *
     * 帧类型 → 分辨率 → 支持的帧率，顺序与相机上报的一致。
     */
    private val options: Map<Int, Map<Resolution, List<Int>>>

    init {
        val byType = LinkedHashMap<Int, LinkedHashMap<Resolution, List<Int>>>()
        for (format in formats) {
            val frameType = frameTypeOf(format.type) ?: continue
            val resolutions = byType.getOrPut(frameType) { LinkedHashMap() }
            for (descriptor in format.frameDescriptors.orEmpty()) {
                val fpsList = descriptor.intervals.orEmpty().map { it.fps }
                if (fpsList.isNotEmpty()) {
                    resolutions[Resolution(descriptor.width, descriptor.height)] = fpsList
                }
            }
        }
        // Drop formats that ended up with no usable resolution.
        // 去除最终没有可用分辨率的格式。
        options = byType.filterValues { it.isNotEmpty() }
    }

    /** Frame types on offer. / 可选的帧类型。 */
    val frameTypes: List<Int> get() = options.keys.toList()

    /** Whether the camera offers nothing to choose from. / 相机是否没有任何可选项。 */
    val isEmpty: Boolean get() = options.isEmpty()

    /** Resolutions on offer for [frameType]. / [frameType] 可选的分辨率。 */
    fun resolutions(frameType: Int): List<Resolution> = options[frameType]?.keys?.toList().orEmpty()

    /** Frame rates on offer for [frameType] at [resolution]. / [frameType] 在 [resolution] 下可选的帧率。 */
    fun frameRates(frameType: Int, resolution: Resolution): List<Int> =
        options[frameType]?.get(resolution).orEmpty()

    /**
     * Turns any requested combination into one the camera supports, keeping as much of it as it can.
     *
     * An unsupported frame type falls back to the first one, an unsupported resolution to the first
     * resolution of the frame type, and an unsupported frame rate to the first rate of the resolution.
     * Must not be called when [isEmpty].
     *
     * 将任意请求的组合转换为相机支持的组合，并尽量保留原有选项。
     *
     * 不支持的帧类型回退为第一个帧类型，不支持的分辨率回退为该帧类型的第一个分辨率，
     * 不支持的帧率回退为该分辨率的第一个帧率。[isEmpty] 为 true 时不得调用。
     */
    fun coerce(frameType: Int, resolution: Resolution, fps: Int): VideoFormatSelection {
        val type = if (frameType in options) frameType else options.keys.first()
        val resolutions = options.getValue(type)
        val res = if (resolution in resolutions) resolution else resolutions.keys.first()
        val rates = resolutions.getValue(res)
        val rate = if (fps in rates) fps else rates.first()
        return VideoFormatSelection(type, res, rate)
    }

    /** [coerce] applied to a camera [Size]. / 对相机 [Size] 应用 [coerce]。 */
    fun coerce(size: Size): VideoFormatSelection =
        coerce(size.type, Resolution(size.width, size.height), size.fps)

    companion object {
        /**
         * Maps a UVC format type to the frame type its frame descriptors use, or null if unsupported.
         *
         * 将 UVC 格式类型映射为其帧描述符使用的帧类型；不支持时返回 null。
         */
        private fun frameTypeOf(formatType: Int): Int? = when (formatType) {
            UVCCamera.UVC_VS_FORMAT_UNCOMPRESSED -> UVCCamera.UVC_VS_FRAME_UNCOMPRESSED
            UVCCamera.UVC_VS_FORMAT_MJPEG -> UVCCamera.UVC_VS_FRAME_MJPEG
            else -> null
        }
    }
}
