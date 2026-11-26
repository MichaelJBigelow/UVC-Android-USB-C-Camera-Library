package com.herohan.uvcdemo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.TestTags
import com.herohan.uvcsample.ui.theme.UvcSampleTheme
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import com.herohan.uvcsample.R as SampleR

/**
 * Lists the demos; tapping one checks the permissions its demo needs, then opens it.
 *
 * 列出各个演示；点击某项会先检查其演示所需的权限，再打开该演示。
 */
class EntryActivity : ComponentActivity() {

    /**
     * A demo on the entry screen.
     *
     * 入口界面上的一个演示。
     *
     * @param summary one line on what the demo shows / 一行说明该演示展示的内容
     * @param permissions permissions needed besides CAMERA / 除 CAMERA 外还需要的权限
     */
    private class Demo(
        @param:StringRes val title: Int,
        @param:StringRes val summary: Int,
        val activity: Class<out Activity>,
        val permissions: List<IPermission> = emptyList(),
    )

    private val demos = listOf(
        Demo(R.string.entry_basic_preview, R.string.entry_basic_preview_summary, BasicPreviewActivity::class.java),
        Demo(R.string.entry_custom_preview, R.string.entry_custom_preview_summary, CustomPreviewActivity::class.java),
        Demo(R.string.entry_multi_preview, R.string.entry_multi_preview_summary, MultiPreviewActivity::class.java),
        Demo(
            R.string.entry_multi_camera_new,
            R.string.entry_multi_camera_new_summary,
            MultiCameraNewActivity::class.java,
        ),
        Demo(R.string.entry_multi_camera, R.string.entry_multi_camera_summary, MultiCameraActivity::class.java),
        // Pictures and videos are saved through MediaStore, which needs no storage permission.
        // 照片和视频通过 MediaStore 保存，不需要存储权限。
        Demo(R.string.entry_take_picture, R.string.entry_take_picture_summary, TakePictureActivity::class.java),
        Demo(
            R.string.entry_record_video,
            R.string.entry_record_video_summary,
            RecordVideoActivity::class.java,
            listOf(PermissionLists.getRecordAudioPermission()),
        ),
        Demo(
            R.string.entry_set_frame_callback,
            R.string.entry_set_frame_callback_summary,
            SetFrameCallbackActivity::class.java,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            UvcSampleTheme {
                DemoScaffold(
                    title = stringResource(R.string.app_name),
                    showNavigateUp = false,
                    background = MaterialTheme.colorScheme.surface,
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(demos, key = { it.activity.name }) { demo ->
                            DemoItem(demo)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DemoItem(demo: Demo) {
        ListItem(
            headlineContent = { Text(stringResource(demo.title)) },
            supportingContent = { Text(stringResource(demo.summary)) },
            trailingContent = {
                Icon(painterResource(SampleR.drawable.ic_chevron_right), contentDescription = null)
            },
            modifier = Modifier
                .clickable { openDemo(demo) }
                .testTag(TestTags.entryButton(demo.activity.simpleName)),
        )
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
