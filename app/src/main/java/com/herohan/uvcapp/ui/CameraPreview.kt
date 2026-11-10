package com.herohan.uvcapp.ui

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Camera preview backed by a [TextureView], sized to the camera's aspect ratio.
 *
 * The camera renders into the view's [SurfaceTexture]; [onSurfaceAvailable] and
 * [onSurfaceDestroyed] report when it can be attached to, or must be detached from, the camera.
 *
 * 由 [TextureView] 承载的相机预览，尺寸与相机的宽高比一致。
 *
 * 相机渲染到该视图的 [SurfaceTexture] 上；[onSurfaceAvailable] 和
 * [onSurfaceDestroyed] 分别通知何时可以将其挂接到相机、何时必须将其从相机移除。
 *
 * @param previewWidth width of the camera preview, in pixels / 相机预览宽度(像素)
 * @param previewHeight height of the camera preview, in pixels / 相机预览高度(像素)
 */
@Composable
fun CameraTexturePreview(
    previewWidth: Int,
    previewHeight: Int,
    onSurfaceAvailable: (SurfaceTexture) -> Unit,
    onSurfaceDestroyed: (SurfaceTexture) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The view outlives recompositions, so read the latest callbacks through state.
    // 视图的生命周期长于重组，因此通过 state 读取最新的回调。
    val currentOnAvailable by rememberUpdatedState(onSurfaceAvailable)
    val currentOnDestroyed by rememberUpdatedState(onSurfaceDestroyed)

    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        currentOnAvailable(surface)
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        currentOnDestroyed(surface)
                        // The camera removes the surface asynchronously, so don't let the view release it.
                        // 相机会异步移除该 surface，因此不要让视图释放它。
                        return false
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                    }
                }
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
internal fun aspectRatioOf(width: Int, height: Int): Float =
    if (width > 0 && height > 0) width.toFloat() / height else 4f / 3f
