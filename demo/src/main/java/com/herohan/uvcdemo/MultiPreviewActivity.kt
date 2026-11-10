package com.herohan.uvcdemo

import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.util.Log
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.herohan.uvcdemo.ui.CameraSurfacePreview
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.OpenCloseButtons
import com.herohan.uvcdemo.ui.PreviewCellRow
import com.herohan.uvcdemo.ui.theme.UvcDemoTheme
import com.herohan.uvcdemo.ui.theme.enableEdgeToEdgeBelowAppBar

/**
 * Shows one camera in four previews at once.
 *
 * 同时在四个预览中显示同一台相机。
 */
class MultiPreviewActivity : ComponentActivity() {

    private var cameraHelper: ICameraHelper? = null

    /**
     * Surfaces of the four previews, while they exist.
     *
     * 四个预览的 surface(存在期间有效)。
     */
    private val previewSurfaces = arrayOfNulls<Surface>(PREVIEW_COUNT)

    private var previewWidth by mutableIntStateOf(DEFAULT_WIDTH)
    private var previewHeight by mutableIntStateOf(DEFAULT_HEIGHT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeBelowAppBar()

        setContent {
            UvcDemoTheme {
                DemoScaffold(title = stringResource(R.string.entry_multi_preview)) {
                    Column {
                        for (row in 0 until PREVIEW_COUNT / 2) {
                            PreviewCellRow(cellCount = 2, cellFraction = 0.4f) { column ->
                                PreviewCell(index = row * 2 + column)
                            }
                        }
                    }
                    OpenCloseButtons(
                        onOpen = ::openCamera,
                        onClose = ::closeCamera,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun BoxScope.PreviewCell(index: Int) {
        CameraSurfacePreview(
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            onSurfaceCreated = { surface ->
                previewSurfaces[index] = surface
                cameraHelper?.addSurface(surface, false)
            },
            onSurfaceDestroyed = { surface ->
                cameraHelper?.removeSurface(surface)
                previewSurfaces[index] = null
            },
            modifier = Modifier.align(Alignment.Center),
        )
    }

    override fun onStart() {
        super.onStart()
        initCameraHelper()
    }

    override fun onStop() {
        super.onStop()
        clearCameraHelper()
    }

    private fun initCameraHelper() {
        if (DEBUG) Log.d(TAG, "initCameraHelper:")
        if (cameraHelper == null) {
            cameraHelper = CameraHelper().apply { setStateCallback(stateListener) }
        }
    }

    private fun clearCameraHelper() {
        if (DEBUG) Log.d(TAG, "clearCameraHelper:")
        cameraHelper?.release()
        cameraHelper = null
    }

    private fun selectDevice(device: UsbDevice) {
        if (DEBUG) Log.v(TAG, "selectDevice:device=" + device.deviceName)
        cameraHelper?.selectDevice(device)
    }

    /**
     * Select the first UVC device.
     *
     * 选择第一个 UVC 设备。
     */
    private fun openCamera() {
        cameraHelper?.deviceList?.firstOrNull()?.let { cameraHelper?.selectDevice(it) }
    }

    private fun closeCamera() {
        cameraHelper?.closeCamera()
    }

    private val stateListener = object : ICameraHelper.StateCallback {
        override fun onAttach(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onAttach:")
            selectDevice(device)
        }

        override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
            if (DEBUG) Log.v(TAG, "onDeviceOpen:")
            cameraHelper?.openCamera()
        }

        override fun onCameraOpen(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onCameraOpen:")
            val helper = cameraHelper ?: return

            helper.startPreview()

            // auto aspect ratio
            // 自动宽高比
            helper.previewSize?.let {
                previewWidth = it.width
                previewHeight = it.height
            }

            previewSurfaces.forEach { surface -> surface?.let { helper.addSurface(it, false) } }
        }

        override fun onCameraClose(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onCameraClose:")
            val helper = cameraHelper ?: return
            previewSurfaces.forEach { surface -> surface?.let { helper.removeSurface(it) } }
        }

        override fun onDeviceClose(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onDeviceClose:")
        }

        override fun onDetach(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onDetach:")
        }

        override fun onCancel(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onCancel:")
        }
    }

    private companion object {
        private const val DEBUG = true
        private val TAG = MultiPreviewActivity::class.java.simpleName

        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480

        private const val PREVIEW_COUNT = 4
    }
}
