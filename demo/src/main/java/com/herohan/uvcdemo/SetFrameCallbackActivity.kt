package com.herohan.uvcdemo

import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.herohan.uvcdemo.ui.CameraSurfacePreview
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.OpenCloseButtons
import com.herohan.uvcdemo.ui.PreviewCellRow
import com.herohan.uvcdemo.utils.CustomFPS
import com.herohan.uvcdemo.utils.NV21ToBitmap
import com.herohan.uvcsample.CameraSession
import com.herohan.uvcsample.ui.theme.UvcSampleTheme
import com.herohan.uvcsample.ui.theme.enableEdgeToEdgeBelowAppBar
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

    private val camera = CameraSession(this, cameraCallback(), logTag = TAG)

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
            UvcSampleTheme {
                DemoScaffold(title = fpsText ?: stringResource(R.string.entry_set_frame_callback)) {
                    PreviewCellRow(cellCount = 2, cellFraction = 0.4f) { index ->
                        if (index == 0) {
                            CameraSurfacePreview(
                                previewWidth = camera.previewWidth,
                                previewHeight = camera.previewHeight,
                                onSurfaceCreated = camera::addPreviewSurface,
                                onSurfaceDestroyed = camera::removePreviewSurface,
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
                    OpenCloseButtons(onOpen = camera::selectFirstDevice, onClose = camera::closeCamera)
                }
            }
        }
    }

    private fun cameraCallback() = object : CameraSession.Callback {
        override fun onCameraOpen(session: CameraSession, device: UsbDevice) {
            val helper = session.cameraHelper ?: return

            // The frames are converted at the preview size, so it must be known.
            // 帧按预览尺寸转换，因此必须先知道预览尺寸。
            val size = helper.previewSize ?: return

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

        override fun onCameraClose(session: CameraSession, device: UsbDevice) {
            clearFPS()
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
                Log.v(TAG, "fps:$fps")
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
        private val TAG = SetFrameCallbackActivity::class.java.simpleName
    }
}
