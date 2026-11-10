package com.herohan.uvcapp.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.herohan.uvcapp.R
import com.serenegiant.usb.Format
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCCamera

/**
 * Dialog for choosing the camera's preview format, resolution and frame rate.
 *
 * 用于选择相机预览格式、分辨率和帧率的对话框。
 *
 * @param formats formats the camera supports / 相机支持的格式
 * @param currentSize current preview size, selected when the dialog opens / 当前预览尺寸，对话框打开时默认选中
 * @param onFormatSelected called with the chosen size when the user taps OK / 用户点击“确定”后，以所选尺寸回调
 * @param onDismiss called when the dialog should close / 对话框需要关闭时回调
 */
@Composable
fun VideoFormatDialog(
    formats: List<Format>,
    currentSize: Size,
    onFormatSelected: (Size) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = remember(formats) { VideoFormatOptions(formats) }
    if (options.isEmpty) {
        // Nothing to choose from: close instead of showing empty menus.
        // 没有可选项：直接关闭，而不是显示空菜单。
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var frameType by rememberSaveable { mutableIntStateOf(currentSize.type) }
    var width by rememberSaveable { mutableIntStateOf(currentSize.width) }
    var height by rememberSaveable { mutableIntStateOf(currentSize.height) }
    var fps by rememberSaveable { mutableIntStateOf(currentSize.fps) }
    val selection = options.coerce(frameType, Resolution(width, height), fps)

    fun select(newSelection: VideoFormatSelection) {
        frameType = newSelection.frameType
        width = newSelection.resolution.width
        height = newSelection.resolution.height
        fps = newSelection.fps
    }

    val formatNames = mapOf(
        UVCCamera.UVC_VS_FRAME_UNCOMPRESSED to stringResource(R.string.video_format_format_yuv),
        UVCCamera.UVC_VS_FRAME_MJPEG to stringResource(R.string.video_format_format_mjped),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_format_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionDropdown(
                    label = stringResource(R.string.video_format_format),
                    options = options.frameTypes,
                    selected = selection.frameType,
                    optionText = { formatNames[it].orEmpty() },
                    onSelected = {
                        select(options.coerce(it, selection.resolution, selection.fps))
                    },
                )
                OptionDropdown(
                    label = stringResource(R.string.video_format_resolution),
                    options = options.resolutions(selection.frameType),
                    selected = selection.resolution,
                    optionText = { it.toString() },
                    onSelected = {
                        select(options.coerce(selection.frameType, it, selection.fps))
                    },
                )
                OptionDropdown(
                    label = stringResource(R.string.video_format_frame_rate),
                    options = options.frameRates(selection.frameType, selection.resolution),
                    selected = selection.fps,
                    optionText = { it.toString() },
                    onSelected = { select(selection.copy(fps = it)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val size = currentSize.clone().apply {
                    type = selection.frameType
                    width = selection.resolution.width
                    height = selection.resolution.height
                    fps = selection.fps
                }
                onFormatSelected(size)
                onDismiss()
            }) {
                Text(stringResource(R.string.video_format_ok_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.video_format_cancel_button))
            }
        },
    )
}

/**
 * Read-only drop-down field that picks one of [options].
 *
 * 从 [options] 中选择一项的只读下拉字段。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionDropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionText: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = optionText(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionText(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
