package com.serenegiant.usb

import android.os.Parcel
import android.os.Parcelable
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that sizes and formats survive a trip through a [Parcel], as they do when they cross a
 * process or are saved in an activity's state.
 *
 * 检查尺寸和格式经过 [Parcel] 往返后保持不变，就像它们跨进程传递或保存在 Activity 状态中时那样。
 */
@RunWith(AndroidJUnit4::class)
class ParcelableTest {

    private fun <T : Parcelable> roundTrip(value: T, creator: Parcelable.Creator<T>): T {
        val parcel = Parcel.obtain()
        try {
            value.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return creator.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun size_keepsTypeDimensionsAndFrameRate() {
        val size = Size(UVCCamera.UVC_VS_FRAME_MJPEG, 1920, 1080, 30, listOf(30, 15))

        val copy = roundTrip(size, Size.CREATOR)

        assertEquals(UVCCamera.UVC_VS_FRAME_MJPEG, copy.type)
        assertEquals(1920, copy.width)
        assertEquals(1080, copy.height)
        assertEquals(30, copy.fps)
        // A copy read from a parcel can still be cloned.
        // 从 parcel 读取的副本仍可克隆。
        assertEquals(size.toString(), copy.clone().toString())
    }

    @Test
    fun format_keepsDescriptorsAndIntervals() {
        val format = Format(
            1,
            UVCCamera.UVC_VS_FORMAT_MJPEG,
            listOf(
                Format.Descriptor(
                    1, UVCCamera.UVC_VS_FRAME_MJPEG, 1920, 1080, 30, 333_333,
                    listOf(Format.Interval(1, 333_333, 30), Format.Interval(2, 666_666, 15)),
                ),
                Format.Descriptor(
                    2, UVCCamera.UVC_VS_FRAME_MJPEG, 640, 480, 30, 333_333,
                    listOf(Format.Interval(1, 333_333, 30)),
                ),
            ),
        )

        val copy = roundTrip(format, Format.CREATOR)

        assertEquals(1, copy.index)
        assertEquals(UVCCamera.UVC_VS_FORMAT_MJPEG, copy.type)
        assertEquals(listOf("1920x1080", "640x480"), copy.frameDescriptors.map { "${it.width}x${it.height}" })
        val first = copy.frameDescriptors[0]
        assertEquals(1, first.index)
        assertEquals(UVCCamera.UVC_VS_FRAME_MJPEG, first.type)
        assertEquals(30, first.fps)
        assertEquals(333_333, first.frameInterval)
        assertEquals(listOf(1, 2), first.intervals.map { it.index })
        assertEquals(listOf(333_333, 666_666), first.intervals.map { it.value })
        assertEquals(listOf(30, 15), first.intervals.map { it.fps })
    }
}
