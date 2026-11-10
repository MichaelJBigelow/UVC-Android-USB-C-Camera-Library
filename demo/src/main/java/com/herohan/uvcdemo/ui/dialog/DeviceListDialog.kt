package com.herohan.uvcdemo.ui.dialog

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.herohan.uvcdemo.R

/**
 * Dialog that lists the connected USB devices and lets the user pick one.
 *
 * 列出已连接的 USB 设备并供用户选择其一的对话框。
 *
 * @param devices devices to list; empty or null shows a hint instead / 要列出的设备；为空或 null 时显示提示
 * @param selectedDevice device currently in use, shown as selected / 当前正在使用的设备，显示为已选中
 * @param onDeviceSelected called with the device the user picked / 用户选择设备后回调
 * @param onDismiss called when the dialog should close / 对话框需要关闭时回调
 */
@Composable
fun DeviceListDialog(
    devices: List<UsbDevice>?,
    selectedDevice: UsbDevice?,
    onDeviceSelected: (UsbDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_list_dialog_title)) },
        text = {
            if (devices.isNullOrEmpty()) {
                Text(stringResource(R.string.device_list_empty_tip))
            } else {
                LazyColumn {
                    items(devices, key = { it.deviceName }) { device ->
                        DeviceItem(
                            device = device,
                            selected = device == selectedDevice,
                            onClick = {
                                onDeviceSelected(device)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.device_list_cancel_button))
            }
        },
    )
}

@Composable
private fun DeviceItem(device: UsbDevice, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row handles the click, so the radio button is display only.
        // 点击由整行处理，因此单选按钮仅用于显示。
        RadioButton(selected = selected, onClick = null)
        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = device.productName ?: device.manufacturerName.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = device.deviceName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
