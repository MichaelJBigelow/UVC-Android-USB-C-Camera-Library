package com.herohan.uvcapp.activity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.herohan.uvcapp.R
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.herohan.uvcsample.R as SampleR

/**
 * Starts the app with no USB camera attached, as on an emulator, and checks it asks for one.
 * Skipped when a camera is attached, since the app then opens it.
 *
 * 在未连接 USB 相机时(如在模拟器上)启动应用，检查其提示连接相机。连接了相机时跳过，因为应用会打开它。
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int): String = composeRule.activity.getString(id)

    @Before
    fun requireNoCamera() {
        assumeFalse("needs no UVC camera attached", isUvcCameraAttached(composeRule.activity))
    }

    @Test
    fun withoutCamera_asksToConnectOne() {
        composeRule.onNodeWithText(string(R.string.main_connect_usb_tip)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(SampleR.string.take_picture)).assertDoesNotExist()
    }

    @Test
    fun usbButton_listsNoDevicesUntilOneIsConnected() {
        composeRule.onNodeWithContentDescription(string(R.string.action_usb)).performClick()
        composeRule.onNodeWithText(string(SampleR.string.device_list_empty_tip)).assertIsDisplayed()

        composeRule.onNodeWithText(string(SampleR.string.device_list_cancel_button)).performClick()

        composeRule.onNodeWithText(string(SampleR.string.device_list_empty_tip)).assertDoesNotExist()
    }
}
