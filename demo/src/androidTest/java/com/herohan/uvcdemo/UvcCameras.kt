package com.herohan.uvcdemo

import android.content.Context
import android.hardware.usb.UsbManager
import com.serenegiant.usb.DeviceFilter
import com.serenegiant.uvccamera.R as LibraryR

/**
 * Whether a USB camera the library would offer is attached, by the library's own device filter.
 * Emulators have no USB host, so this is false there.
 *
 * 按库自身的设备过滤器判断，是否连接了库会提供的 USB 相机。模拟器没有 USB 主机，因此在模拟器上为 false。
 */
fun isUvcCameraAttached(context: Context): Boolean {
    val usbManager = context.getSystemService(UsbManager::class.java) ?: return false
    val filters = DeviceFilter.getDeviceFilters(context, LibraryR.xml.device_filter)
    return usbManager.deviceList.values.any { device ->
        filters.any { !it.isExclude && it.matches(device) }
    }
}
