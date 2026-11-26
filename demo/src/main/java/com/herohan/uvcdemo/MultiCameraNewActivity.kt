package com.herohan.uvcdemo

import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.os.ConditionVariable
import android.os.Handler
import android.os.HandlerThread
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.herohan.uvcsample.ui.theme.enableEdgeToEdgeBelowAppBar
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.UVCParam
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Shows two cameras side by side. Unlike [MultiCameraActivity], it asks for the USB permission
 * of one camera at a time, so two cameras attached together both get opened.
 *
 * 并排显示两台相机。与 [MultiCameraActivity] 不同，它一次只为一台相机请求 USB 权限，
 * 因此同时接入的两台相机都能被打开。
 */
class MultiCameraNewActivity : ComponentActivity() {

    private val left = CameraSlot("Left")
    private val right = CameraSlot("Right")

    private val readyUsbDeviceList = ConcurrentLinkedQueue<UsbDevice>()
    private val readyDeviceConditionVariable = ConditionVariable()

    private val sync = Any()

    private lateinit var handlerThread: HandlerThread
    private lateinit var asyncHandler: Handler

    /**
     * The camera whose device list is showing, if any.
     *
     * 正在显示设备列表的相机(如有)。
     */
    private var deviceListRequest by mutableStateOf<DeviceListRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeBelowAppBar()

        handlerThread = HandlerThread(TAG).apply { start() }
        asyncHandler = Handler(handlerThread.looper)

        setContent {
            UvcSampleTheme {
                DemoScaffold(title = stringResource(R.string.entry_multi_camera_new)) {
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

    override fun onDestroy() {
        super.onDestroy()

        handlerThread.quitSafely()
        asyncHandler.removeCallbacksAndMessages(null)
    }

    private fun showDeviceListDialog(slot: CameraSlot) {
        val helper = slot.camera.cameraHelper ?: return
        deviceListRequest = DeviceListRequest(
            slot = slot,
            devices = helper.deviceList.orEmpty(),
            selectedDevice = if (slot.camera.isCameraOpened) slot.camera.usbDevice else null,
        )
    }

    /**
     * wait for only one camera need request permission
     *
     * 等待，确保同一时间只有一个相机在请求权限
     */
    private fun waitCanSelectDevice(device: UsbDevice) {
        readyUsbDeviceList.add(device)
        while (readyUsbDeviceList.size > 1) {
            readyDeviceConditionVariable.block()
            readyDeviceConditionVariable.close()
        }
    }

    /**
     * remove ready camera that wait  for select
     *
     * 移除正在等待选择的就绪相机
     */
    private fun removeSelectedDevice(device: UsbDevice) {
        readyUsbDeviceList.remove(device)
        readyDeviceConditionVariable.open()
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
        val camera = CameraSession(this@MultiCameraNewActivity, SlotCallback(), logTag = "$TAG#$name")

        private val other: CameraSlot get() = if (this === left) right else left

        /**
         * Selects [device] once no other camera is waiting for its USB permission.
         *
         * 等到没有其他相机在等待 USB 权限时，再选择 [device]。
         */
        fun selectDevice(device: UsbDevice) {
            camera.usbDevice = device

            asyncHandler.post {
                waitCanSelectDevice(device)

                camera.cameraHelper?.selectDevice(device)
            }
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

                removeSelectedDevice(device)
            }

            override fun handlesDevice(session: CameraSession, device: UsbDevice): Boolean =
                device == session.usbDevice

            override fun onDetach(session: CameraSession, device: UsbDevice) {
                removeSelectedDevice(device)
            }

            override fun onCancel(session: CameraSession, device: UsbDevice) {
                removeSelectedDevice(device)
            }
        }
    }

    private companion object {
        private val TAG = MultiCameraNewActivity::class.java.simpleName
    }
}
