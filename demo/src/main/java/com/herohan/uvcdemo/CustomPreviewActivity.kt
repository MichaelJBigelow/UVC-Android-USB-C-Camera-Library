package com.herohan.uvcdemo

import android.hardware.usb.UsbDevice
import android.os.Bundle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.herohan.uvcapp.ICameraHelper
import com.herohan.uvcdemo.ui.CameraSurfacePreview
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.OpenCloseButtons
import com.herohan.uvcsample.CameraSession
import com.herohan.uvcsample.ui.dialog.CameraControlsDialog
import com.herohan.uvcsample.ui.dialog.VideoFormatDialog
import com.herohan.uvcsample.ui.theme.UvcSampleTheme
import com.herohan.uvcsample.ui.theme.enableEdgeToEdgeBelowAppBar
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

    private val camera = CameraSession(this, cameraCallback(), logTag = TAG)

    private val cameraHelper: ICameraHelper? get() = camera.cameraHelper

    private var previewRotation = 0

    private var openDialog by mutableStateOf<PreviewDialog?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeBelowAppBar()

        setContent {
            UvcSampleTheme {
                DemoScaffold(
                    title = stringResource(R.string.entry_custom_preview),
                    actions = { if (camera.isCameraOpened) PreviewMenu() },
                ) {
                    CameraSurfacePreview(
                        previewWidth = camera.previewWidth,
                        previewHeight = camera.previewHeight,
                        onSurfaceCreated = camera::addPreviewSurface,
                        onSurfaceDestroyed = camera::removePreviewSurface,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    OpenCloseButtons(onOpen = camera::selectFirstDevice, onClose = camera::closeCamera)
                }
                PreviewDialogs()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        openDialog = null
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

            camera.resizePreview(size)
        }
    }

    private fun cameraCallback() = object : CameraSession.Callback {
        override fun onCameraClose(session: CameraSession, device: UsbDevice) {
            openDialog = null
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
        private val TAG = CustomPreviewActivity::class.java.simpleName
    }
}
