package com.herohan.uvcdemo.ui

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.herohan.uvcdemo.R

/**
 * Background behind the camera previews.
 *
 * 相机预览后方的背景色。
 */
val PreviewBackground = Color(0xFF808080)

/**
 * Screen frame shared by the demos: a top app bar with [title] and [actions] above [content].
 *
 * 各演示共用的界面框架：[content] 上方是带 [title] 和 [actions] 的顶部应用栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScaffold(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    background: Color = PreviewBackground,
    content: @Composable BoxScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        containerColor = background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            content = content,
        )
    }
}

/**
 * Camera preview backed by a [SurfaceView], sized to the camera's aspect ratio.
 *
 * The camera renders into the view's [Surface]; [onSurfaceCreated] and [onSurfaceDestroyed]
 * report when it can be attached to, or must be detached from, the camera.
 *
 * 由 [SurfaceView] 承载的相机预览，尺寸与相机的宽高比一致。
 *
 * 相机渲染到该视图的 [Surface] 上；[onSurfaceCreated] 和 [onSurfaceDestroyed]
 * 分别通知何时可以将其挂接到相机、何时必须将其从相机移除。
 *
 * @param previewWidth width of the camera preview, in pixels / 相机预览宽度(像素)
 * @param previewHeight height of the camera preview, in pixels / 相机预览高度(像素)
 */
@Composable
fun CameraSurfacePreview(
    previewWidth: Int,
    previewHeight: Int,
    onSurfaceCreated: (Surface) -> Unit,
    onSurfaceDestroyed: (Surface) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The view outlives recompositions, so read the latest callbacks through state.
    // 视图的生命周期长于重组，因此通过 state 读取最新的回调。
    val currentOnCreated by rememberUpdatedState(onSurfaceCreated)
    val currentOnDestroyed by rememberUpdatedState(onSurfaceDestroyed)

    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        currentOnCreated(holder.surface)
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        currentOnDestroyed(holder.surface)
                    }
                })
            }
        },
        modifier = modifier.aspectRatio(aspectRatioOf(previewWidth, previewHeight)),
    )
}

/**
 * Width-to-height ratio of a preview size, falling back to 4:3 for an invalid size.
 *
 * 预览尺寸的宽高比；尺寸无效时回退为 4:3。
 */
fun aspectRatioOf(width: Int, height: Int): Float =
    if (width > 0 && height > 0) width.toFloat() / height else 4f / 3f

/**
 * A row of [cellCount] 4:3 cells, each [cellFraction] of the available width, with equal gaps
 * before, between and after them.
 *
 * 一行 [cellCount] 个 4:3 的单元格，每个占可用宽度的 [cellFraction]，
 * 单元格前、之间和之后的间距相等。
 */
@Composable
fun PreviewCellRow(
    cellCount: Int,
    cellFraction: Float,
    modifier: Modifier = Modifier,
    cell: @Composable BoxScope.(index: Int) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cellWidth = maxWidth * cellFraction
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            repeat(cellCount) { index ->
                Box(
                    modifier = Modifier
                        .width(cellWidth)
                        .aspectRatio(4f / 3f),
                ) {
                    cell(index)
                }
            }
        }
    }
}

/**
 * One camera of a multi-camera demo: its preview, with Open and Close buttons along the bottom.
 *
 * 多相机演示中的一台相机：其预览，底部带有“打开”和“关闭”按钮。
 */
@Composable
fun BoxScope.CameraCell(
    previewWidth: Int,
    previewHeight: Int,
    onSurfaceCreated: (Surface) -> Unit,
    onSurfaceDestroyed: (Surface) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    CameraSurfacePreview(
        previewWidth = previewWidth,
        previewHeight = previewHeight,
        onSurfaceCreated = onSurfaceCreated,
        onSurfaceDestroyed = onSurfaceDestroyed,
        modifier = Modifier.align(Alignment.Center),
    )
    OpenCloseButtons(
        onOpen = onOpen,
        onClose = onClose,
        openText = stringResource(R.string.btn_open),
        closeText = stringResource(R.string.btn_close),
        horizontalPadding = 5.dp,
    )
}

/**
 * A pair of buttons at the bottom of [BoxScope]: [openText] on the left, [closeText] on the right.
 *
 * 位于 [BoxScope] 底部的一对按钮：左侧为 [openText]，右侧为 [closeText]。
 */
@Composable
fun BoxScope.OpenCloseButtons(
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    openText: String = stringResource(R.string.btn_open_camera),
    closeText: String = stringResource(R.string.btn_close_camera),
    horizontalPadding: Dp = 20.dp,
) {
    Row(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Button(onClick = onOpen) { Text(openText) }
        Button(onClick = onClose) { Text(closeText) }
    }
}
