package com.herohan.uvcdemo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.TestTags
import com.herohan.uvcdemo.ui.theme.UvcDemoTheme
import com.herohan.uvcdemo.ui.theme.enableEdgeToEdgeBelowAppBar
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission

/**
 * Lists the demos; each button checks the permissions its demo needs, then opens it.
 *
 * 列出各个演示；每个按钮先检查其演示所需的权限，再打开该演示。
 */
class EntryActivity : ComponentActivity() {

    /**
     * A demo on the entry screen.
     *
     * 入口界面上的一个演示。
     *
     * @param permissions permissions needed besides CAMERA / 除 CAMERA 外还需要的权限
     */
    private class Demo(
        @param:StringRes val title: Int,
        val activity: Class<out Activity>,
        val permissions: List<IPermission> = emptyList(),
    )

    private val demos = listOf(
        Demo(R.string.entry_basic_preview, BasicPreviewActivity::class.java),
        Demo(R.string.entry_custom_preview, CustomPreviewActivity::class.java),
        Demo(R.string.entry_multi_preview, MultiPreviewActivity::class.java),
        Demo(R.string.entry_multi_camera_new, MultiCameraNewActivity::class.java),
        Demo(R.string.entry_multi_camera, MultiCameraActivity::class.java),
        Demo(
            R.string.entry_take_picture,
            TakePictureActivity::class.java,
            listOf(PermissionLists.getManageExternalStoragePermission()),
        ),
        Demo(
            R.string.entry_record_video,
            RecordVideoActivity::class.java,
            listOf(
                PermissionLists.getManageExternalStoragePermission(),
                PermissionLists.getRecordAudioPermission(),
            ),
        ),
        Demo(R.string.entry_set_frame_callback, SetFrameCallbackActivity::class.java),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeBelowAppBar()

        setContent {
            UvcDemoTheme {
                DemoScaffold(
                    title = stringResource(R.string.app_name),
                    background = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        demos.forEach { demo ->
                            Button(
                                onClick = { openDemo(demo) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 30.dp, vertical = 10.dp)
                                    .testTag(TestTags.entryButton(demo.activity.simpleName)),
                            ) {
                                Text(stringResource(demo.title))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openDemo(demo: Demo) {
        XXPermissions.with(this)
            .permissions(listOf(PermissionLists.getCameraPermission()) + demo.permissions)
            .request { _, deniedList ->
                if (deniedList.isEmpty()) {
                    startActivity(Intent(this, demo.activity))
                }
            }
    }
}
