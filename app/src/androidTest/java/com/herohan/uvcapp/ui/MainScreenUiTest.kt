package com.herohan.uvcapp.ui

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.herohan.uvcapp.R
import com.herohan.uvcsample.ui.theme.UvcSampleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.herohan.uvcsample.R as SampleR

/**
 * Checks what the main screen shows with and without a camera, and while recording. Runs without
 * a USB camera: the screen only gets the camera's state.
 *
 * 检查主界面在有无相机以及录像期间显示的内容。无需 USB 相机即可运行：界面只接收相机的状态。
 */
@RunWith(AndroidJUnit4::class)
class MainScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int): String = context.getString(id)

    private fun setScreen(
        isCameraConnected: Boolean,
        isRecording: Boolean = false,
        onTakePicture: () -> Unit = {},
        onToggleRecord: () -> Unit = {},
    ) {
        composeRule.setContent {
            UvcSampleTheme {
                MainScreen(
                    isCameraConnected = isCameraConnected,
                    isRecording = isRecording,
                    previewWidth = 640,
                    previewHeight = 480,
                    recordStartTime = if (isRecording) SystemClock.elapsedRealtime() else null,
                    onSurfaceAvailable = {},
                    onSurfaceDestroyed = {},
                    onMenuAction = {},
                    onTakePicture = onTakePicture,
                    onToggleRecord = onToggleRecord,
                )
            }
        }
    }

    @Test
    fun withoutCamera_asksToConnectOne() {
        setScreen(isCameraConnected = false)

        composeRule.onNodeWithText(string(R.string.main_connect_usb_tip)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.action_usb)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(SampleR.string.take_picture)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.action_control)).assertDoesNotExist()
    }

    @Test
    fun withCamera_offersPictureAndRecording() {
        var pictures = 0
        var toggles = 0
        setScreen(isCameraConnected = true, onTakePicture = { pictures++ }, onToggleRecord = { toggles++ })

        composeRule.onNodeWithContentDescription(string(SampleR.string.take_picture)).performClick()
        composeRule.onNodeWithContentDescription(string(SampleR.string.start_recording)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.action_control)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.main_connect_usb_tip)).assertDoesNotExist()

        assertEquals(1, pictures)
        assertEquals(1, toggles)
    }

    @Test
    fun whileRecording_showsStopButtonAndElapsedTime() {
        setScreen(isCameraConnected = true, isRecording = true)

        composeRule.onNodeWithContentDescription(string(SampleR.string.stop_recording)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(SampleR.string.start_recording)).assertDoesNotExist()
        composeRule.onNodeWithText("00:00:00").assertIsDisplayed()
    }
}
