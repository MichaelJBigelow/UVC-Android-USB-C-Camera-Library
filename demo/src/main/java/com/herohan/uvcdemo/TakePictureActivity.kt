package com.herohan.uvcdemo

import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.herohan.uvcapp.IImageCapture
import com.herohan.uvcdemo.ui.CameraSurfacePreview
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.TestTags
import com.herohan.uvcsample.CameraSession
import com.herohan.uvcsample.ui.theme.UvcSampleTheme
import com.herohan.uvcsample.ui.theme.enableEdgeToEdgeBelowAppBar
import com.serenegiant.utils.FileUtils
import com.serenegiant.utils.UriHelper
import com.serenegiant.uvccamera.R as UvcR

class TakePictureActivity : ComponentActivity() {

    private val camera = CameraSession(this, logTag = TAG)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeBelowAppBar()

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
                    IconButton(
                        onClick = ::takePicture,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = dimensionResource(UvcR.dimen.vertical_margin))
                            .testTag(TestTags.CAPTURE_IMAGE),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_switch_camera),
                            contentDescription = stringResource(R.string.entry_take_picture),
                            tint = Color.Unspecified,
                        )
                    }
                }
            }
        }
    }

    private fun takePicture() {
        val helper = camera.cameraHelper ?: return

        val file = FileUtils.getCaptureFile(this, Environment.DIRECTORY_DCIM, ".jpg")
        val options = IImageCapture.OutputFileOptions.Builder(file).build()

//        val contentValues = ContentValues().apply {
//            put(MediaStore.MediaColumns.DISPLAY_NAME, "NEW_IMAGE")
//            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
//        }
//        val options = IImageCapture.OutputFileOptions.Builder(
//            contentResolver,
//            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//            contentValues,
//        ).build()

//        val outputStream = ByteArrayOutputStream()
//        val options = IImageCapture.OutputFileOptions.Builder(outputStream).build()

        helper.takePicture(options, object : IImageCapture.OnImageCaptureCallback {
            override fun onImageSaved(outputFileResults: IImageCapture.OutputFileResults) {
                Toast.makeText(
                    this@TakePictureActivity,
                    "save \"" + UriHelper.getPath(this@TakePictureActivity, outputFileResults.savedUri) + "\"",
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
