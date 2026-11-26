package com.herohan.uvcsample.ui.dialog

import android.hardware.usb.UsbDevice
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.herohan.uvcsample.R
import com.herohan.uvcsample.ui.theme.UvcSampleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the device list dialog without devices. A [UsbDevice] can't be made in a test, so the
 * list with devices is left to a manual check with a camera.
 *
 * 检查没有设备时的设备列表对话框。测试中无法构造 [UsbDevice]，因此有设备时的列表留待连接相机后手动检查。
 */
@RunWith(AndroidJUnit4::class)
class DeviceListDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private var dismissals = 0

    private fun setDialog(devices: List<UsbDevice>?) {
        composeRule.setContent {
            UvcSampleTheme {
                DeviceListDialog(
                    devices = devices,
                    selectedDevice = null,
                    onDeviceSelected = {},
                    onDismiss = { dismissals++ },
                )
            }
        }
    }

    @Test
    fun withoutDevices_asksToConnectACamera() {
        setDialog(emptyList())

        composeRule.onNodeWithText(context.getString(R.string.device_list_dialog_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.device_list_empty_tip)).assertIsDisplayed()
    }

    @Test
    fun noDeviceListIsTreatedAsEmpty() {
        setDialog(null)

        composeRule.onNodeWithText(context.getString(R.string.device_list_empty_tip)).assertIsDisplayed()
    }

    @Test
    fun cancelDismisses() {
        setDialog(emptyList())

        composeRule.onNodeWithText(context.getString(R.string.device_list_cancel_button)).performClick()

        assertEquals(1, dismissals)
    }
}
