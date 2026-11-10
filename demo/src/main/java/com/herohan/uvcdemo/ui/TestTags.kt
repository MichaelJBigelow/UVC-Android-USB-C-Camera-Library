package com.herohan.uvcdemo.ui

/**
 * Compose test tags of the elements that the instrumented tests drive.
 *
 * 仪器化测试所操作元素的 Compose 测试标签。
 */
object TestTags {
    const val CAPTURE_IMAGE = "btnCaptureImage"
    const val CAPTURE_VIDEO = "btnCaptureVideo"

    /** Tag of the entry screen button that opens [activityName]. / 入口界面中打开 [activityName] 的按钮标签。 */
    fun entryButton(activityName: String): String = "entry_$activityName"
}
