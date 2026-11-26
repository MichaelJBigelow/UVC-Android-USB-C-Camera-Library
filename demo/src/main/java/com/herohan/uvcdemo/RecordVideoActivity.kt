package com.herohan.uvcdemo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.herohan.uvcapp.ICameraHelper
import com.herohan.uvcapp.VideoCapture
import com.herohan.uvcdemo.ui.CameraSurfacePreview
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.TestTags
import com.herohan.uvcsample.CameraSession
import com.herohan.uvcsample.media.MediaStoreOutput
import com.herohan.uvcsample.ui.theme.UvcSampleTheme
import com.herohan.uvcsample.ui.theme.enableEdgeToEdgeBelowAppBar
import com.herohan.uvcsample.R as SampleR
import com.serenegiant.uvccamera.R as UvcR

class RecordVideoActivity : ComponentActivity() {

    private val camera = CameraSession(this, logTag = TAG)

    private var isRecording by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeBelowAppBar()

        setContent {
            UvcSampleTheme {
                DemoScaffold(title = stringResource(R.string.entry_record_video)) {
                    CameraSurfacePreview(
                        previewWidth = camera.previewWidth,
                        previewHeight = camera.previewHeight,
                        onSurfaceCreated = camera::addPreviewSurface,
                        onSurfaceDestroyed = camera::removePreviewSurface,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    IconButton(
                        onClick = ::toggleRecord,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = dimensionResource(UvcR.dimen.vertical_margin))
                            .testTag(TestTags.CAPTURE_VIDEO),
                    ) {
                        // Tint the button red while recording.
                        // 录制期间将按钮着色为红色。
                        Image(
                            painterResource(R.drawable.ic_switch_video),
                            contentDescription = stringResource(R.string.entry_record_video),
                            colorFilter = if (isRecording) RECORDING_TINT else null,
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // The session releases the camera, which ends any recording; reset the button now
        // rather than waiting for a callback that may not come.
        // 会话会释放相机，从而结束录像；现在就重置按钮，而不是等待可能不会到来的回调。
        isRecording = false
    }

    private fun toggleRecord() {
        val helper = camera.cameraHelper ?: return
        if (helper.isRecording) {
            helper.stopRecording()
        } else {
            startRecord(helper)
        }
    }

    private fun startRecord(helper: ICameraHelper) {
        // Saved to Movies/<app name> through MediaStore; VideoCapture.OutputFileOptions.Builder(file)
        // also works.
        // 通过 MediaStore 保存到 Movies/<应用名>；也可以使用 VideoCapture.OutputFileOptions.Builder(file)。
        val output = MediaStoreOutput.video(this)
        val options = output.videoOptions()

        helper.startRecording(options, object : VideoCapture.OnVideoCaptureCallback {
            override fun onStart() {
            }

            override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                Toast.makeText(
                    this@RecordVideoActivity,
                    getString(SampleR.string.saved_to, output.description),
                    Toast.LENGTH_SHORT,
                ).show()

                isRecording = false
            }

            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                Toast.makeText(this@RecordVideoActivity, message, Toast.LENGTH_LONG).show()

                isRecording = false
            }
        })

        isRecording = true
    }

    private companion object {
        private val TAG = RecordVideoActivity::class.java.simpleName

        /**
         * Red overlay drawn on the record button while recording.
         *
         * 录制期间叠加在录制按钮上的红色。
         */
        private val RECORDING_TINT = ColorFilter.tint(Color(0x7FFF0000), BlendMode.SrcAtop)
    }
}
