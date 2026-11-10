package com.herohan.uvcapp.ui

import android.graphics.SurfaceTexture
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.herohan.uvcapp.R
import com.herohan.uvcapp.ui.theme.RecordRed
import com.herohan.uvcapp.ui.theme.UvcAppTheme
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Actions offered by the top app bar.
 *
 * 顶部应用栏提供的操作。
 */
enum class MainMenuAction {
    CameraControls,
    Device,
    SafelyEject,
    VideoFormat,
    Rotate90Cw,
    Rotate90Ccw,
    FlipHorizontally,
    FlipVertically,
}

/**
 * Background of the picture and video buttons over the preview.
 *
 * 预览上方拍照和录像按钮的背景色。
 */
private val FabContainerColor = Color(0x773700B3)

/**
 * How often the recording time is refreshed, in milliseconds.
 *
 * 录制时间的刷新间隔(毫秒)。
 */
private const val RECORD_TIME_REFRESH_MS = 250L

/**
 * The app's single screen: the camera preview with a top app bar and picture/video buttons.
 *
 * 应用的唯一界面：带顶部应用栏和拍照/录像按钮的相机预览。
 *
 * @param recordStartTime [SystemClock.elapsedRealtime] when recording started, or null when not recording /
 *                        开始录制时的 [SystemClock.elapsedRealtime]；未在录制时为 null
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isCameraConnected: Boolean,
    isRecording: Boolean,
    previewWidth: Int,
    previewHeight: Int,
    recordStartTime: Long?,
    onSurfaceAvailable: (SurfaceTexture) -> Unit,
    onSurfaceDestroyed: (SurfaceTexture) -> Unit,
    onMenuAction: (MainMenuAction) -> Unit,
    onTakePicture: () -> Unit,
    onToggleRecord: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = { MainMenu(isCameraConnected, onMenuAction) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        containerColor = Color.Black,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // The preview stays composed while no camera is connected, only hidden, so its
            // SurfaceTexture is kept and reused when a camera connects again.
            // 未连接相机时预览仍保留在组合中，只是被隐藏，因此其 SurfaceTexture
            // 会被保留，并在再次连接相机时复用。
            CameraTexturePreview(
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                onSurfaceAvailable = onSurfaceAvailable,
                onSurfaceDestroyed = onSurfaceDestroyed,
                modifier = Modifier
                    .align(Alignment.Center)
                    .alpha(if (isCameraConnected) 1f else 0f),
            )
            if (!isCameraConnected) {
                Text(
                    text = stringResource(R.string.main_connect_usb_tip),
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (isCameraConnected && recordStartTime != null) {
                RecordTime(
                    startTime = recordStartTime,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp),
                )
            }

            if (isCameraConnected) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = dimensionResource(R.dimen.fab_margin), bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = onToggleRecord,
                        containerColor = FabContainerColor,
                        contentColor = if (isRecording) RecordRed else Color.White,
                    ) {
                        Icon(painterResource(R.drawable.ic_switch_video), contentDescription = null)
                    }
                    SmallFloatingActionButton(
                        onClick = onTakePicture,
                        containerColor = FabContainerColor,
                        contentColor = Color.White,
                    ) {
                        Icon(painterResource(R.drawable.ic_switch_camera), contentDescription = null)
                    }
                }
            }
        }
    }
}

/**
 * Top app bar actions. Camera actions are only offered while a camera is connected.
 *
 * 顶部应用栏的操作。仅在相机已连接时提供与相机相关的操作。
 */
@Composable
private fun MainMenu(isCameraConnected: Boolean, onMenuAction: (MainMenuAction) -> Unit) {
    if (isCameraConnected) {
        IconButton(onClick = { onMenuAction(MainMenuAction.CameraControls) }) {
            Icon(
                painterResource(R.drawable.ic_action_control),
                contentDescription = stringResource(R.string.action_control),
            )
        }
    }
    IconButton(onClick = { onMenuAction(MainMenuAction.Device) }) {
        Icon(
            painterResource(R.drawable.ic_action_usb),
            contentDescription = stringResource(R.string.action_usb),
        )
    }
    if (isCameraConnected) {
        var expanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf(
                    MainMenuAction.SafelyEject to R.string.action_safely_eject,
                    MainMenuAction.VideoFormat to R.string.action_video_format,
                    MainMenuAction.Rotate90Cw to R.string.action_rotate_90_CW,
                    MainMenuAction.Rotate90Ccw to R.string.action_rotate_90_CCW,
                    MainMenuAction.FlipHorizontally to R.string.action_flip_horizontally,
                    MainMenuAction.FlipVertically to R.string.action_flip_vertically,
                ).forEach { (action, title) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(title)) },
                        onClick = {
                            expanded = false
                            onMenuAction(action)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Elapsed recording time with a red dot, refreshed every quarter second.
 *
 * 带红点的已录制时长，每四分之一秒刷新一次。
 */
@Composable
private fun RecordTime(startTime: Long, modifier: Modifier = Modifier) {
    var seconds by remember(startTime) { mutableLongStateOf(0L) }
    LaunchedEffect(startTime) {
        while (true) {
            seconds = (SystemClock.elapsedRealtime() - startTime) / 1000
            delay(RECORD_TIME_REFRESH_MS)
        }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .background(RecordRed, CircleShape),
        )
        Spacer(Modifier.width(5.dp))
        Text(text = formatRecordTime(seconds), color = Color.White, fontSize = 14.sp)
    }
}

/**
 * Formats a duration as HH:mm:ss.
 *
 * 将时长格式化为 HH:mm:ss。
 *
 * @param seconds duration in seconds / 时长(秒)
 */
fun formatRecordTime(seconds: Long): String =
    String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)

@Preview
@Composable
private fun MainScreenDisconnectedPreview() {
    UvcAppTheme {
        MainScreen(
            isCameraConnected = false,
            isRecording = false,
            previewWidth = 640,
            previewHeight = 480,
            recordStartTime = null,
            onSurfaceAvailable = {},
            onSurfaceDestroyed = {},
            onMenuAction = {},
            onTakePicture = {},
            onToggleRecord = {},
        )
    }
}
