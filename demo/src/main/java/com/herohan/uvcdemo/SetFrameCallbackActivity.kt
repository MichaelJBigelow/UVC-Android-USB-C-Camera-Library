package com.herohan.uvcdemo

import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.util.Log
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.herohan.uvcdemo.ui.CameraSurfacePreview
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.OpenCloseButtons
import com.herohan.uvcdemo.ui.PreviewCellRow
import com.herohan.uvcdemo.ui.theme.UvcDemoTheme
import com.herohan.uvcdemo.ui.theme.enableEdgeToEdgeBelowAppBar
import com.herohan.uvcdemo.utils.CustomFPS
import com.herohan.uvcdemo.utils.NV21ToBitmap
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.UVCCamera
import java.text.DecimalFormat

/**
 * Shows the camera preview next to the frames delivered by a frame callback, with the callback's
 * frame rate in the title.
 *
 * 将相机预览与帧回调送来的帧并排显示，并在标题中显示回调的帧率。
 */
class SetFrameCallbackActivity : ComponentActivity() {

    private var cameraHelper: ICameraHelper? = null

    private var previewSurface: Surface? = null

    private var previewWidth by mutableIntStateOf(DEFAULT_WIDTH)
    private var previewHeight by mutableIntStateOf(DEFAULT_HEIGHT)

    /**
     * Latest frame from the frame callback.
     *
     * 帧回调送来的最新一帧。
     */
    private var frameBitmap by mutableStateOf<Bitmap?>(null)

    /**
     * Frame rate shown in the title, or null to show the demo's name.
     *
     * 标题中显示的帧率；为 null 时显示演示名称。
     */
    private var fpsText by mutableStateOf<String?>(null)

    private lateinit var nv21ToBitmap: NV21ToBitmap

    private var customFPS: CustomFPS? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeBelowAppBar()

        nv21ToBitmap = NV21ToBitmap(this)

        setContent {
            UvcDemoTheme {
                DemoScaffold(title = fpsText ?: stringResource(R.string.entry_set_frame_callback)) {
                    PreviewCellRow(cellCount = 2, cellFraction = 0.4f) { index ->
                        if (index == 0) {
                            CameraSurfacePreview(
                                previewWidth = previewWidth,
                                previewHeight = previewHeight,
                                onSurfaceCreated = { surface ->
                                    previewSurface = surface
                                    cameraHelper?.addSurface(surface, false)
                                },
                                onSurfaceDestroyed = { surface ->
                                    cameraHelper?.removeSurface(surface)
                                    previewSurface = null
                                },
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            frameBitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    OpenCloseButtons(onOpen = ::openCamera, onClose = ::closeCamera)
                }
            }
        }
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

            val size = helper.previewSize
            if (size != null) {
                // auto aspect ratio
                // 自动调整宽高比
                previewWidth = size.width
                previewHeight = size.height
            }

            previewSurface?.let { helper.addSurface(it, false) }

            // The frames are converted at the preview size, so it must be known.
            // 帧按预览尺寸转换，因此必须先知道预览尺寸。
            if (size == null) return

            helper.setFrameCallback(IFrameCallback { frame ->
                // Refresh FPS
                // 刷新 FPS
                customFPS?.doFrame()

                val nv21 = ByteArray(frame.remaining())
                frame.get(nv21, 0, nv21.size)

                val bitmap = nv21ToBitmap.nv21ToBitmap(nv21, size.width, size.height)
                runOnUiThread { frameBitmap = bitmap }
            }, UVCCamera.PIXEL_FORMAT_NV21)

            initFPS()
        }

        override fun onCameraClose(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onCameraClose:")

            previewSurface?.let { cameraHelper?.removeSurface(it) }

            clearFPS()
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

    /**
     * Initialize the FPS display
     *
     * 初始化 FPS 显示
     */
    private fun initFPS() {
        val decimal = DecimalFormat(" #.0' fps'")

        customFPS = CustomFPS().apply {
            addListener { fps ->
                if (DEBUG) Log.v(TAG, "fps:$fps")
                runOnUiThread { fpsText = decimal.format(fps) }
            }
        }
    }

    /**
     * End FPS display
     *
     * 结束 FPS 显示
     */
    private fun clearFPS() {
        customFPS?.release()
        customFPS = null

        fpsText = null
    }

    private companion object {
        private const val DEBUG = true
        private val TAG = SetFrameCallbackActivity::class.java.simpleName

        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480
    }
}
