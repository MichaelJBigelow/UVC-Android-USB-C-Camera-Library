package com.herohan.uvcapp.ui

import android.graphics.SurfaceTexture
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.herohan.uvcapp.R
import com.herohan.uvcsample.ui.theme.UvcSampleTheme
import kotlinx.coroutines.delay
import java.util.Locale
import com.herohan.uvcsample.R as SampleR

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
 * Background behind the camera preview, black like a viewfinder in both light and dark themes.
 *
 * 相机预览后方的背景，与取景器一样在浅色和深色主题中均为黑色。
 */
private val ViewfinderBackground = Color.Black

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
            )
        },
        containerColor = ViewfinderBackground,
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
                ConnectCameraTip(modifier = Modifier.align(Alignment.Center))
            }

            if (isCameraConnected && recordStartTime != null) {
                RecordTime(
                    startTime = recordStartTime,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                )
            }

            if (isCameraConnected) {
                CaptureButtons(
                    isRecording = isRecording,
                    onTakePicture = onTakePicture,
                    onToggleRecord = onToggleRecord,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = dimensionResource(R.dimen.fab_margin), bottom = 16.dp),
                )
            }
        }
    }
}

/**
 * What the screen shows while no camera is connected.
 *
 * 未连接相机时界面显示的内容。
 */
@Composable
private fun ConnectCameraTip(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painterResource(SampleR.drawable.ic_usb),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.main_connect_usb_tip),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The record button above the larger picture button. While recording, the record button turns
 * into a stop button in the error colors.
 *
 * 位于较大拍照按钮上方的录像按钮。录像期间，录像按钮变为使用错误配色的停止按钮。
 */
@Composable
private fun CaptureButtons(
    isRecording: Boolean,
    onTakePicture: () -> Unit,
    onToggleRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isRecording) {
            FloatingActionButton(
                onClick = onToggleRecord,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Icon(
                    painterResource(SampleR.drawable.ic_stop),
                    contentDescription = stringResource(SampleR.string.stop_recording),
                )
            }
        } else {
            FloatingActionButton(onClick = onToggleRecord) {
                Icon(
                    painterResource(SampleR.drawable.ic_videocam),
                    contentDescription = stringResource(SampleR.string.start_recording),
                )
            }
        }
        LargeFloatingActionButton(onClick = onTakePicture) {
            Icon(
                painterResource(SampleR.drawable.ic_photo_camera),
                contentDescription = stringResource(SampleR.string.take_picture),
                modifier = Modifier.size(36.dp),
            )
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
                painterResource(SampleR.drawable.ic_tune),
                contentDescription = stringResource(R.string.action_control),
            )
        }
    }
    IconButton(onClick = { onMenuAction(MainMenuAction.Device) }) {
        Icon(
            painterResource(SampleR.drawable.ic_usb),
            contentDescription = stringResource(R.string.action_usb),
        )
    }
    if (isCameraConnected) {
        var expanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    painterResource(SampleR.drawable.ic_more_vert),
                    contentDescription = stringResource(SampleR.string.more_options),
                )
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
 * Elapsed recording time with a recording dot, in a pill in the error colors, refreshed every
 * quarter second.
 *
 * 带录制圆点的已录制时长，显示在使用错误配色的胶囊中，每四分之一秒刷新一次。
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
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            )
            Text(text = formatRecordTime(seconds), style = MaterialTheme.typography.labelLarge)
        }
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
    UvcSampleTheme {
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

@Preview
@Composable
private fun MainScreenRecordingPreview() {
    UvcSampleTheme {
        MainScreen(
            isCameraConnected = true,
            isRecording = true,
            previewWidth = 640,
            previewHeight = 480,
            recordStartTime = SystemClock.elapsedRealtime(),
            onSurfaceAvailable = {},
            onSurfaceDestroyed = {},
            onMenuAction = {},
            onTakePicture = {},
            onToggleRecord = {},
        )
    }
}
