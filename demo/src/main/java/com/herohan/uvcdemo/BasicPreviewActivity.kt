package com.herohan.uvcdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.herohan.uvcdemo.ui.CameraSurfacePreview
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.OpenCloseButtons
import com.herohan.uvcsample.CameraSession
import com.herohan.uvcsample.ui.theme.UvcSampleTheme

class BasicPreviewActivity : ComponentActivity() {

    private val camera = CameraSession(this, logTag = TAG)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            UvcSampleTheme {
                DemoScaffold(title = stringResource(R.string.entry_basic_preview)) {
                    CameraSurfacePreview(
                        previewWidth = camera.previewWidth,
                        previewHeight = camera.previewHeight,
                        onSurfaceCreated = camera::addPreviewSurface,
                        onSurfaceDestroyed = camera::removePreviewSurface,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    OpenCloseButtons(onOpen = camera::selectFirstDevice, onClose = camera::closeCamera)
                }
            }
        }
    }

    private companion object {
        private val TAG = BasicPreviewActivity::class.java.simpleName
    }
}
