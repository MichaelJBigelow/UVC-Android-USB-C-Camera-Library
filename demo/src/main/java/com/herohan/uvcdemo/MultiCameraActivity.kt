package com.herohan.uvcdemo

import android.hardware.usb.UsbDevice
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.herohan.uvcdemo.ui.CameraCell
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.PreviewCellRow
import com.herohan.uvcsample.CameraSession
import com.herohan.uvcsample.ui.dialog.DeviceListDialog
import com.herohan.uvcsample.ui.theme.UvcSampleTheme
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.UVCParam

/**
 * Shows two cameras side by side. [MultiCameraNewActivity] is the newer version, which asks for
 * the USB permission of one camera at a time.
 *
 * 并排显示两台相机。[MultiCameraNewActivity] 是其新版本，一次只为一台相机请求 USB 权限。
 */
class MultiCameraActivity : ComponentActivity() {

    private val left = CameraSlot("Left")
    private val right = CameraSlot("Right")

    private val sync = Any()

    /**
     * The camera whose device list is showing, if any.
     *
     * 正在显示设备列表的相机(如有)。
     */
    private var deviceListRequest by mutableStateOf<DeviceListRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            UvcSampleTheme {
                DemoScaffold(title = stringResource(R.string.entry_multi_camera)) {
                    PreviewCellRow(cellCount = 2, cellFraction = 0.49f) { index ->
                        val slot = if (index == 0) left else right
                        CameraCell(
                            previewWidth = slot.camera.previewWidth,
                            previewHeight = slot.camera.previewHeight,
                            onSurfaceCreated = slot.camera::addPreviewSurface,
                            onSurfaceDestroyed = slot.camera::removePreviewSurface,
                            onOpen = { showDeviceListDialog(slot) },
                            onClose = slot::closeCamera,
                        )
                    }
                }
                deviceListRequest?.let { request ->
                    DeviceListDialog(
                        devices = request.devices,
                        selectedDevice = request.selectedDevice,
                        onDeviceSelected = { device ->
                            request.slot.closeCamera()
                            request.slot.selectDevice(device)
                        },
                        onDismiss = { deviceListRequest = null },
                    )
                }
            }
        }
    }

    private fun showDeviceListDialog(slot: CameraSlot) {
        val helper = slot.camera.cameraHelper ?: return
        deviceListRequest = DeviceListRequest(
            slot = slot,
            devices = helper.deviceList.orEmpty(),
            selectedDevice = if (slot.camera.isCameraOpened) slot.camera.usbDevice else null,
        )
    }

    private class DeviceListRequest(
        val slot: CameraSlot,
        val devices: List<UsbDevice>,
        val selectedDevice: UsbDevice?,
    )

    /**
     * One of the two cameras: its session and how it picks and opens its device.
     *
     * 两台相机之一：其相机会话，以及它如何选择和打开自己的设备。
     */
    private inner class CameraSlot(name: String) {
        val camera = CameraSession(this@MultiCameraActivity, SlotCallback(), logTag = "$TAG#$name")

        private val other: CameraSlot get() = if (this === left) right else left

        fun selectDevice(device: UsbDevice) {
            camera.selectDevice(device)
        }

        fun closeCamera() {
            if (camera.isCameraOpened) {
                camera.closeCamera()
            }
        }

        private inner class SlotCallback : CameraSession.Callback {
            override fun onAttach(session: CameraSession, device: UsbDevice) {
                synchronized(sync) {
                    if (session.usbDevice == null && device != other.camera.usbDevice) {
                        selectDevice(device)
                    }
                }
            }

            override fun onDeviceOpen(session: CameraSession, device: UsbDevice, isFirstOpen: Boolean) {
                if (device == session.usbDevice) {
                    val param = UVCParam()
                    param.setQuirks(UVCCamera.UVC_QUIRK_FIX_BANDWIDTH)
                    session.cameraHelper?.openCamera(param)
                }
            }

            override fun handlesDevice(session: CameraSession, device: UsbDevice): Boolean =
                device == session.usbDevice
        }
    }

    private companion object {
        private val TAG = MultiCameraActivity::class.java.simpleName
    }
}
