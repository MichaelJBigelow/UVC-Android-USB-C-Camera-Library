package com.herohan.uvcdemo

import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.os.ConditionVariable
import android.os.Handler
import android.os.HandlerThread
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
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Shows two cameras side by side. Unlike [MultiCameraActivity], it asks for the USB permission
 * of one camera at a time, so two cameras attached together both get opened.
 *
 * 并排显示两台相机。与 [MultiCameraActivity] 不同，它一次只为一台相机请求 USB 权限，
 * 因此同时接入的两台相机都能被打开。
 */
class MultiCameraNewActivity : ComponentActivity() {

    private val left = CameraSlot("ListenerLeft#")
    private val right = CameraSlot("ListenerRight#")

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
            UvcDemoTheme {
                DemoScaffold(title = stringResource(R.string.entry_multi_camera_new)) {
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

    override fun onDestroy() {
        super.onDestroy()

        handlerThread.quitSafely()
        asyncHandler.removeCallbacksAndMessages(null)
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

            asyncHandler.post {
                waitCanSelectDevice(device)

                cameraHelper?.selectDevice(device)
            }
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

                removeSelectedDevice(device)
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

                removeSelectedDevice(device)
            }

            override fun onCancel(device: UsbDevice) {
                if (DEBUG) Log.v(TAG, logPrefix + "onCancel:")
                if (device == usbDevice) {
                    usbDevice = null
                }

                removeSelectedDevice(device)
            }
        }
    }

    private companion object {
        private const val DEBUG = true
        private val TAG = MultiCameraNewActivity::class.java.simpleName

        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480
    }
}
