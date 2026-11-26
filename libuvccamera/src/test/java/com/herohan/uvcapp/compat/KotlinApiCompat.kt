package com.herohan.uvcapp.compat

import android.content.Context
import android.os.Environment
import com.serenegiant.utils.FileUtils

/**
 * Checks that Kotlin callers can still call the helpers the way they called the Java versions.
 * Only compiled, never run: the Android classes it calls only exist as stubs in unit tests.
 *
 * 检查 Kotlin 调用方仍可像调用 Java 版本那样调用这些辅助方法。只编译、不运行：
 * 它调用的 Android 类在单元测试中只有桩实现。
 */
@Suppress("unused")
private fun useTheHelpers(context: Context) {
    FileUtils.getCaptureFile(context, Environment.DIRECTORY_DCIM, ".jpg")
    FileUtils.getCaptureFile(context, Environment.DIRECTORY_DCIM, "IMG_", ".jpg")
    FileUtils.getCaptureUri(context, Environment.DIRECTORY_MOVIES, ".mp4")
    FileUtils.getCaptureUri(context, Environment.DIRECTORY_MOVIES, "VID_", ".mp4")
}
