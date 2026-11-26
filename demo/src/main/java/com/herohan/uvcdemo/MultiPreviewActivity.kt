package com.herohan.uvcdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.herohan.uvcdemo.ui.CameraSurfacePreview
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.OpenCloseButtons
import com.herohan.uvcdemo.ui.PreviewCellRow
import com.herohan.uvcsample.CameraSession
import com.herohan.uvcsample.ui.theme.UvcSampleTheme

/**
 * Shows one camera in four previews at once.
 *
 * 同时在四个预览中显示同一台相机。
 */
class MultiPreviewActivity : ComponentActivity() {

    private val camera = CameraSession(this, logTag = TAG)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            UvcSampleTheme {
                DemoScaffold(title = stringResource(R.string.entry_multi_preview)) {
                    Column {
                        for (row in 0 until PREVIEW_COUNT / 2) {
                            PreviewCellRow(cellCount = 2, cellFraction = 0.4f) {
                                PreviewCell()
                            }
                        }
                    }
                    OpenCloseButtons(
                        onOpen = camera::selectFirstDevice,
                        onClose = camera::closeCamera,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
        }
    }

    /**
     * One of the previews; the session attaches each of their surfaces to the camera.
     *
     * 其中一个预览；会话会将每个预览的 surface 挂接到相机上。
     */
    @Composable
    private fun BoxScope.PreviewCell() {
        CameraSurfacePreview(
            previewWidth = camera.previewWidth,
            previewHeight = camera.previewHeight,
            onSurfaceCreated = camera::addPreviewSurface,
            onSurfaceDestroyed = camera::removePreviewSurface,
            modifier = Modifier.align(Alignment.Center),
        )
    }

    private companion object {
        private val TAG = MultiPreviewActivity::class.java.simpleName

        private const val PREVIEW_COUNT = 4
    }
}
