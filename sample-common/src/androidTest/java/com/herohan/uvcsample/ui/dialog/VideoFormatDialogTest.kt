package com.herohan.uvcsample.ui.dialog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.herohan.uvcsample.R
import com.herohan.uvcsample.ui.theme.UvcSampleTheme
import com.serenegiant.usb.Format
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks choosing a preview format, resolution and frame rate in the dialog, with made-up formats.
 *
 * 用构造的格式检查在对话框中选择预览格式、分辨率和帧率。
 */
@RunWith(AndroidJUnit4::class)
class VideoFormatDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun descriptor(type: Int, width: Int, height: Int, vararg fps: Int) =
        Format.Descriptor(0, type, width, height, fps.first(), 0, fps.map { Format.Interval(0, 0, it) })

    private val formats = listOf(
        Format(
            1,
            UVCCamera.UVC_VS_FORMAT_MJPEG,
            listOf(
                descriptor(UVCCamera.UVC_VS_FRAME_MJPEG, 1920, 1080, 30, 15),
                descriptor(UVCCamera.UVC_VS_FRAME_MJPEG, 640, 480, 30, 25, 15),
            ),
        ),
        Format(
            2,
            UVCCamera.UVC_VS_FORMAT_UNCOMPRESSED,
            listOf(descriptor(UVCCamera.UVC_VS_FRAME_UNCOMPRESSED, 640, 480, 10, 5)),
        ),
    )

    private val currentSize = Size(UVCCamera.UVC_VS_FRAME_MJPEG, 640, 480, 25, listOf(30, 25, 15))

    private var chosen: Size? = null
    private var dismissals = 0

    private fun setDialog(formats: List<Format> = this.formats) {
        composeRule.setContent {
            UvcSampleTheme {
                VideoFormatDialog(
                    formats = formats,
                    currentSize = currentSize,
                    onFormatSelected = { chosen = it },
                    onDismiss = { dismissals++ },
                )
            }
        }
    }

    private fun string(id: Int): String = context.getString(id)

    /** Opens the drop-down showing [current] and picks [option]. / 打开显示 [current] 的下拉菜单并选择 [option]。 */
    private fun choose(current: String, option: String) {
        composeRule.onNodeWithText(current).performClick()
        composeRule.onNodeWithText(option).performClick()
    }

    private fun pressOk() {
        composeRule.onNodeWithText(string(R.string.video_format_ok_button)).performClick()
    }

    @Test
    fun showsTheCurrentSize() {
        setDialog()

        composeRule.onNodeWithText(string(R.string.video_format_format_mjped)).assertIsDisplayed()
        composeRule.onNodeWithText("640x480").assertIsDisplayed()
        composeRule.onNodeWithText("25").assertIsDisplayed()
    }

    @Test
    fun okWithoutChangesKeepsTheCurrentSize() {
        setDialog()

        pressOk()

        val size = chosen!!
        assertEquals(UVCCamera.UVC_VS_FRAME_MJPEG, size.type)
        assertEquals(640, size.width)
        assertEquals(480, size.height)
        assertEquals(25, size.fps)
        assertEquals(1, dismissals)
    }

    @Test
    fun choosingAResolutionWithoutTheFrameRateFallsBackToItsFirst() {
        setDialog()

        choose("640x480", "1920x1080")
        pressOk()

        val size = chosen!!
        assertEquals(1920, size.width)
        assertEquals(1080, size.height)
        // 25 fps isn't offered at 1920x1080 / 1920x1080 不提供 25 fps
        assertEquals(30, size.fps)
    }

    @Test
    fun choosingAFormatKeepsTheResolution() {
        setDialog()

        choose(string(R.string.video_format_format_mjped), string(R.string.video_format_format_yuv))
        pressOk()

        val size = chosen!!
        assertEquals(UVCCamera.UVC_VS_FRAME_UNCOMPRESSED, size.type)
        assertEquals(640, size.width)
        assertEquals(480, size.height)
        assertEquals(10, size.fps)
    }

    @Test
    fun choosingAFrameRate() {
        setDialog()

        choose("25", "15")
        pressOk()

        assertEquals(15, chosen!!.fps)
    }

    @Test
    fun cancelChoosesNothing() {
        setDialog()

        choose("640x480", "1920x1080")
        composeRule.onNodeWithText(string(R.string.video_format_cancel_button)).performClick()

        assertNull(chosen)
        assertEquals(1, dismissals)
    }

    @Test
    fun withoutUsableFormats_closesStraightAway() {
        // An H.264 frame-based format, which the library can't preview
        // 基于帧的 H.264 格式，本库无法预览
        setDialog(listOf(Format(3, 0x10, listOf(descriptor(0x11, 1280, 720, 30)))))

        composeRule.waitForIdle()

        assertEquals(1, dismissals)
        assertNull(chosen)
        composeRule.onNodeWithText(string(R.string.video_format_title)).assertDoesNotExist()
    }
}
