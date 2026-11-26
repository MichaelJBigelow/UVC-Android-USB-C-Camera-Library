package com.herohan.uvcdemo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.herohan.uvcapp.IImageCapture
import com.herohan.uvcdemo.ui.CameraSurfacePreview
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.TestTags
import com.herohan.uvcsample.CameraSession
import com.herohan.uvcsample.media.MediaStoreOutput
import com.herohan.uvcsample.ui.theme.UvcSampleTheme
import com.herohan.uvcsample.R as SampleR
import com.serenegiant.uvccamera.R as UvcR

class TakePictureActivity : ComponentActivity() {

    private val camera = CameraSession(this, logTag = TAG)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            UvcSampleTheme {
                DemoScaffold(title = stringResource(R.string.entry_take_picture)) {
                    CameraSurfacePreview(
                        previewWidth = camera.previewWidth,
                        previewHeight = camera.previewHeight,
                        onSurfaceCreated = camera::addPreviewSurface,
                        onSurfaceDestroyed = camera::removePreviewSurface,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    LargeFloatingActionButton(
                        onClick = ::takePicture,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = dimensionResource(UvcR.dimen.vertical_margin))
                            .testTag(TestTags.CAPTURE_IMAGE),
                    ) {
                        Icon(
                            painterResource(SampleR.drawable.ic_photo_camera),
                            contentDescription = stringResource(SampleR.string.take_picture),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
        }
    }

    private fun takePicture() {
        val helper = camera.cameraHelper ?: return

        // Saved to Pictures/<app name> through MediaStore. A File or an OutputStream also works:
        // IImageCapture.OutputFileOptions.Builder(file) or .Builder(outputStream).
        // 通过 MediaStore 保存到 Pictures/<应用名>。也可以使用 File 或 OutputStream：
        // IImageCapture.OutputFileOptions.Builder(file) 或 .Builder(outputStream)。
        val output = MediaStoreOutput.picture(this)
        val options = output.imageOptions()

        helper.takePicture(options, object : IImageCapture.OnImageCaptureCallback {
            override fun onImageSaved(outputFileResults: IImageCapture.OutputFileResults) {
                Toast.makeText(
                    this@TakePictureActivity,
                    getString(SampleR.string.saved_to, output.description),
                    Toast.LENGTH_SHORT,
                ).show()
            }

            override fun onError(imageCaptureError: Int, message: String, cause: Throwable?) {
                Toast.makeText(this@TakePictureActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private companion object {
        private val TAG = TakePictureActivity::class.java.simpleName
    }
}
