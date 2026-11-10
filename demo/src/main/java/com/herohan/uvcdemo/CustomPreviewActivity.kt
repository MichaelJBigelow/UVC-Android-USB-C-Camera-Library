package com.herohan.uvcdemo

import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.util.Log
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.herohan.uvcdemo.ui.CameraSurfacePreview
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.OpenCloseButtons
import com.herohan.uvcdemo.ui.dialog.CameraControlsDialog
import com.herohan.uvcdemo.ui.dialog.VideoFormatDialog
import com.herohan.uvcdemo.ui.theme.UvcDemoTheme
import com.herohan.uvcdemo.ui.theme.enableEdgeToEdgeBelowAppBar
import com.serenegiant.opengl.renderer.MirrorMode
import com.serenegiant.usb.Format
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCControl

/**
 * Preview with the camera's controls, video format, rotation and mirroring in the top app bar.
 *
 * 在顶部应用栏中提供相机控制、视频格式、旋转和镜像功能的预览。
 */
class CustomPreviewActivity : ComponentActivity() {

    private var cameraHelper: ICameraHelper? = null

    private var previewSurface: Surface? = null

    /** Camera preview width / 摄像头预览宽度 */
    private var previewWidth by mutableIntStateOf(DEFAULT_WIDTH)

    /** Camera preview height / 摄像头预览高度 */
    private var previewHeight by mutableIntStateOf(DEFAULT_HEIGHT)

    private var previewRotation = 0

    private var isCameraOpened by mutableStateOf(false)

    private var openDialog by mutableStateOf<PreviewDialog?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeBelowAppBar()

        setContent {
            UvcDemoTheme {
                DemoScaffold(
                    title = stringResource(R.string.entry_custom_preview),
                    actions = { if (isCameraOpened) PreviewMenu() },
                ) {
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
                    OpenCloseButtons(onOpen = ::openCamera, onClose = ::closeCamera)
                }
                PreviewDialogs()
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

    /**
     * Camera actions, offered while the camera is open.
     *
     * 相机操作，仅在相机打开时提供。
     */
    @Composable
    private fun PreviewMenu() {
        IconButton(onClick = ::showCameraControlsDialog) {
            Icon(
                painterResource(R.drawable.ic_action_control),
                contentDescription = stringResource(R.string.action_control),
            )
        }
        var expanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf<Pair<Int, () -> Unit>>(
                    R.string.action_video_format to ::showVideoFormatDialog,
                    R.string.action_rotate_90_CW to { rotateBy(90) },
                    R.string.action_rotate_90_CCW to { rotateBy(-90) },
                    R.string.action_flip_horizontally to { setMirror(MirrorMode.MIRROR_HORIZONTAL) },
                    R.string.action_flip_vertically to { setMirror(MirrorMode.MIRROR_VERTICAL) },
                ).forEach { (title, action) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(title)) },
                        onClick = {
                            expanded = false
                            action()
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun PreviewDialogs() {
        val dismiss = { openDialog = null }
        when (val dialog = openDialog) {
            is PreviewDialog.CameraControls -> CameraControlsDialog(dialog.control, onDismiss = dismiss)
            is PreviewDialog.VideoFormat -> VideoFormatDialog(
                formats = dialog.formats,
                currentSize = dialog.size,
                onFormatSelected = ::onVideoFormatSelected,
                onDismiss = dismiss,
            )
            null -> Unit
        }
    }

    private fun showCameraControlsDialog() {
        val control = cameraHelper?.uvcControl ?: return
        openDialog = PreviewDialog.CameraControls(control)
    }

    private fun showVideoFormatDialog() {
        val helper = cameraHelper ?: return
        val formats = helper.supportedFormatList ?: return
        val size = helper.previewSize ?: return
        openDialog = PreviewDialog.VideoFormat(formats, size)
    }

    private fun onVideoFormatSelected(size: Size) {
        val helper = cameraHelper ?: return
        if (helper.isCameraOpened) {
            helper.stopPreview()
            helper.previewSize = size
            helper.startPreview()

            resizePreviewView(size)
        }
    }

    private fun resizePreviewView(size: Size) {
        // Update the preview size; the preview keeps the camera's aspect ratio
        // 更新预览尺寸；预览会保持与摄像头一致的宽高比
        previewWidth = size.width
        previewHeight = size.height
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
        isCameraOpened = false
        openDialog = null
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

            helper.previewSize?.let { resizePreviewView(it) }

            previewSurface?.let { helper.addSurface(it, false) }

            isCameraOpened = true
        }

        override fun onCameraClose(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onCameraClose:")

            previewSurface?.let { cameraHelper?.removeSurface(it) }

            isCameraOpened = false
            openDialog = null
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

    private fun rotateBy(angle: Int) {
        previewRotation = ((previewRotation + angle) % 360 + 360) % 360

        cameraHelper?.let { it.previewConfig = it.previewConfig.setRotation(previewRotation) }
    }

    private fun setMirror(mirrorMode: Int) {
        cameraHelper?.let { it.previewConfig = it.previewConfig.setMirror(mirrorMode) }
    }

    /**
     * The dialog shown over the preview, if any.
     *
     * 预览上方显示的对话框(如有)。
     */
    private sealed interface PreviewDialog {
        data class CameraControls(val control: UVCControl) : PreviewDialog
        data class VideoFormat(val formats: List<Format>, val size: Size) : PreviewDialog
    }

    private companion object {
        private const val DEBUG = true
        private val TAG = CustomPreviewActivity::class.java.simpleName

        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480
    }
}
