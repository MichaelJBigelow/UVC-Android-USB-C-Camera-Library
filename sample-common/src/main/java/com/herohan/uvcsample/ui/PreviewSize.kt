package com.herohan.uvcsample.ui

/**
 * Width-to-height ratio of a preview size, falling back to 4:3 for an invalid size.
 *
 * 预览尺寸的宽高比；尺寸无效时回退为 4:3。
 */
fun aspectRatioOf(width: Int, height: Int): Float =
    if (width > 0 && height > 0) width.toFloat() / height else 4f / 3f
