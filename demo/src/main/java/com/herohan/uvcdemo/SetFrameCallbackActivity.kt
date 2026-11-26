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
import androidx.core.graphics.createBitmap
import com.herohan.uvcdemo.ui.CameraSurfacePreview
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.OpenCloseButtons
import com.herohan.uvcdemo.ui.PreviewCellRow
import com.herohan.uvcdemo.utils.FrameRateMeter
import com.herohan.uvcsample.CameraSession
import com.herohan.uvcsample.ui.theme.UvcSampleTheme
import com.herohan.uvcsample.ui.theme.enableEdgeToEdgeBelowAppBar
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.UVCCamera
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicBoolean

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

    /**
     * Two bitmaps the frames are copied into in turn: one is on screen while the next frame is
     * written into the other, so no bitmap is allocated per frame.
     *
     * 两个轮流写入帧的位图：一个显示在屏幕上时，下一帧写入另一个，因此不会为每一帧分配位图。
     */
    private var frameBuffers: Array<Bitmap>? = null
    private var nextBuffer = 0

    /**
     * True from posting a frame to the UI until the UI shows it; frames arriving meanwhile are
     * dropped, so a bitmap is never written while it is on its way to the screen.
     *
     * 从把一帧投递给 UI 到 UI 显示它期间为 true；期间到达的帧会被丢弃，
     * 因此不会写入正在送往屏幕的位图。
     */
    private val framePending = AtomicBoolean(false)

    @Volatile
    private var frameRateMeter: FrameRateMeter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeBelowAppBar()

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

            // The frames are copied at the preview size, so it must be known.
            // 帧按预览尺寸复制，因此必须先知道预览尺寸。
            val size = helper.previewSize ?: return
            val buffers = Array(2) { createBitmap(size.width, size.height) }
            frameBuffers = buffers
            nextBuffer = 0
            framePending.set(false)

            initFPS()

            // RGBX frames have the byte layout of an ARGB_8888 bitmap, so they are copied as they are.
            // RGBX 帧的字节布局与 ARGB_8888 位图相同，因此可直接复制。
            helper.setFrameCallback(IFrameCallback { frame ->
                // Refresh FPS
                // 刷新 FPS
                frameRateMeter?.onFrame()

                if (!framePending.compareAndSet(false, true)) {
                    return@IFrameCallback
                }
                val bitmap = buffers[nextBuffer]
                if (frame.remaining() < bitmap.byteCount) {
                    framePending.set(false)
                    return@IFrameCallback
                }
                bitmap.copyPixelsFromBuffer(frame)
                nextBuffer = 1 - nextBuffer
                runOnUiThread {
                    // Ignore frames of a camera that closed meanwhile.
                    // 忽略在此期间已关闭的相机的帧。
                    if (frameBuffers === buffers) {
                        frameBitmap = bitmap
                    }
                    framePending.set(false)
                }
            }, UVCCamera.PIXEL_FORMAT_RGBX)
        }

        override fun onCameraClose(session: CameraSession, device: UsbDevice) {
            clearFPS()
            frameBuffers = null
            frameBitmap = null
        }
    }

    /**
     * Initialize the FPS display
     *
     * 初始化 FPS 显示
     */
    private fun initFPS() {
        val decimal = DecimalFormat(" #.0' fps'")

        frameRateMeter = FrameRateMeter { fps ->
            Log.v(TAG, "fps:$fps")
            runOnUiThread { fpsText = decimal.format(fps) }
        }
    }

    /**
     * End FPS display
     *
     * 结束 FPS 显示
     */
    private fun clearFPS() {
        frameRateMeter = null

        fpsText = null
    }

    private companion object {
        private val TAG = SetFrameCallbackActivity::class.java.simpleName
    }
}
