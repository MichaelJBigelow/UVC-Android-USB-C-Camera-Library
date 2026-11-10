package com.herohan.uvcdemo

import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.util.Log
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.herohan.uvcdemo.ui.CameraCell
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.PreviewCellRow
import com.herohan.uvcdemo.ui.dialog.DeviceListDialog
import com.herohan.uvcdemo.ui.theme.UvcDemoTheme
import com.herohan.uvcdemo.ui.theme.enableEdgeToEdgeBelowAppBar
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.UVCParam

/**
 * Shows two cameras side by side. [MultiCameraNewActivity] is the newer version, which asks for
 * the USB permission of one camera at a time.
 *
 * 并排显示两台相机。[MultiCameraNewActivity] 是其新版本，一次只为一台相机请求 USB 权限。
 */
class MultiCameraActivity : ComponentActivity() {

    private val left = CameraSlot("ListenerLeft#")
    private val right = CameraSlot("ListenerRight#")

    private val sync = Any()

    /**
     * The camera whose device list is showing, if any.
     *
     * 正在显示设备列表的相机(如有)。
     */
    private var deviceListRequest by mutableStateOf<DeviceListRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeBelowAppBar()

        setContent {
            UvcDemoTheme {
                DemoScaffold(title = stringResource(R.string.entry_multi_camera)) {
                    PreviewCellRow(cellCount = 2, cellFraction = 0.49f) { index ->
                        val slot = if (index == 0) left else right
                        CameraCell(
                            previewWidth = slot.previewWidth,
                            previewHeight = slot.previewHeight,
                            onSurfaceCreated = slot::onSurfaceCreated,
                            onSurfaceDestroyed = slot::onSurfaceDestroyed,
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

    override fun onStart() {
        if (DEBUG) Log.d(TAG, "onStart:")
        super.onStart()
        left.initCameraHelper()
        right.initCameraHelper()
    }

    override fun onStop() {
        if (DEBUG) Log.d(TAG, "onStop:")
        super.onStop()
        left.clearCameraHelper()
        right.clearCameraHelper()
    }

    private fun showDeviceListDialog(slot: CameraSlot) {
        val helper = slot.cameraHelper ?: return
        deviceListRequest = DeviceListRequest(
            slot = slot,
            devices = helper.deviceList.orEmpty(),
            selectedDevice = if (slot.isCameraConnected) slot.usbDevice else null,
        )
    }

    private class DeviceListRequest(
        val slot: CameraSlot,
        val devices: List<UsbDevice>,
        val selectedDevice: UsbDevice?,
    )

    /**
     * One of the two cameras: its helper, device, preview surface and state callback.
     *
     * 两台相机之一：其相机助手、设备、预览 surface 和状态回调。
     */
    private inner class CameraSlot(private val logPrefix: String) {
        var cameraHelper: ICameraHelper? = null
        var usbDevice: UsbDevice? = null
        var isCameraConnected = false

        private var previewSurface: Surface? = null

        var previewWidth by mutableIntStateOf(DEFAULT_WIDTH)
        var previewHeight by mutableIntStateOf(DEFAULT_HEIGHT)

        private val other: CameraSlot get() = if (this === left) right else left

        fun initCameraHelper() {
            if (DEBUG) Log.d(TAG, logPrefix + "initCameraHelper:")
            if (cameraHelper == null) {
                cameraHelper = CameraHelper().apply { setStateCallback(stateListener) }
            }
        }

        fun clearCameraHelper() {
            if (DEBUG) Log.d(TAG, logPrefix + "clearCameraHelper:")
            cameraHelper?.release()
            cameraHelper = null
        }

        fun onSurfaceCreated(surface: Surface) {
            previewSurface = surface
            cameraHelper?.addSurface(surface, false)
        }

        fun onSurfaceDestroyed(surface: Surface) {
            cameraHelper?.removeSurface(surface)
            previewSurface = null
        }

        fun selectDevice(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, logPrefix + "selectDevice:device=" + device.deviceName)
            usbDevice = device

            cameraHelper?.selectDevice(device)
        }

        fun closeCamera() {
            if (isCameraConnected) {
                cameraHelper?.closeCamera()
            }
        }

        private val stateListener = object : ICameraHelper.StateCallback {
            override fun onAttach(device: UsbDevice) {
                if (DEBUG) Log.v(TAG, logPrefix + "onAttach:")
                synchronized(sync) {
                    if (usbDevice == null && device != other.usbDevice) {
                        selectDevice(device)
                    }
                }
            }

            override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
                if (DEBUG) Log.v(TAG, logPrefix + "onDeviceOpen:")
                if (device == usbDevice) {
                    val param = UVCParam()
                    param.setQuirks(UVCCamera.UVC_QUIRK_FIX_BANDWIDTH)
                    cameraHelper?.openCamera(param)
                }
            }

            override fun onCameraOpen(device: UsbDevice) {
                if (DEBUG) Log.v(TAG, logPrefix + "onCameraOpen:")
                val helper = cameraHelper ?: return
                if (device != usbDevice) return

                helper.startPreview()

                // auto aspect ratio
                // 自动宽高比
                helper.previewSize?.let {
                    previewWidth = it.width
                    previewHeight = it.height
                }

                previewSurface?.let { helper.addSurface(it, false) }

                isCameraConnected = true
            }

            override fun onCameraClose(device: UsbDevice) {
                if (DEBUG) Log.v(TAG, logPrefix + "onCameraClose:")
                if (device == usbDevice) {
                    previewSurface?.let { cameraHelper?.removeSurface(it) }

                    isCameraConnected = false
                }
            }

            override fun onDeviceClose(device: UsbDevice) {
                if (DEBUG) Log.v(TAG, logPrefix + "onDeviceClose:")
            }

            override fun onDetach(device: UsbDevice) {
                if (DEBUG) Log.v(TAG, logPrefix + "onDetach:")
                if (device == usbDevice) {
                    usbDevice = null
                }
            }

            override fun onCancel(device: UsbDevice) {
                if (DEBUG) Log.v(TAG, logPrefix + "onCancel:")
                if (device == usbDevice) {
                    usbDevice = null
                }
            }
        }
    }

    private companion object {
        private const val DEBUG = true
        private val TAG = MultiCameraActivity::class.java.simpleName

        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480
    }
}
