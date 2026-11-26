package com.herohan.uvcdemo.cases

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import com.herohan.uvcdemo.EntryActivity
import com.herohan.uvcdemo.RecordVideoActivity
import com.herohan.uvcdemo.isUvcCameraAttached
import com.herohan.uvcdemo.ui.TestTags
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToLong

class CommonTest {

    /**
     * Launches [EntryActivity] and gives access to the Compose UI of every activity it opens.
     *
     * 启动 [EntryActivity]，并可访问其打开的每个 Activity 的 Compose 界面。
     */
    @get:Rule
    val composeTestRule = createAndroidComposeRule<EntryActivity>()

    /**
     * This soak test records from a real camera for about 30 minutes, so it is skipped when no UVC
     * camera is attached (always on an emulator).
     *
     * 这项长时间测试会用真实相机录像约 30 分钟，因此在未连接 UVC 相机时(在模拟器上总是如此)跳过。
     */
    @Before
    fun requireCamera() {
        assumeTrue("needs a UVC camera", isUvcCameraAttached(composeTestRule.activity))
    }

    @Test
    fun testMemoryLeak() {
        Thread.sleep(1000)

        repeat(200) {
            composeTestRule
                .onNodeWithTag(TestTags.entryButton(RecordVideoActivity::class.java.simpleName))
                .performScrollTo()
                .performClick()

            // Waiting for USB camera to finish loading (4s)
            // 等待 USB 摄像头加载完成(4 秒)
            Thread.sleep(4000 + (Math.random() * 500).roundToLong())

            // Click the "Start Recording" button
            // 点击“开始录制”按钮
            composeTestRule.onNodeWithTag(TestTags.CAPTURE_VIDEO).performClick()

            // Record video (4s)
            // 录制视频(4 秒)
            Thread.sleep(4000)

            // Return to entry activity
            // 返回入口 Activity
            Espresso.pressBack()
        }
    }

    @After
    fun onAfter() {
        logAllThreads()
    }

    private fun logAllThreads() {
        val allStackTraces = Thread.getAllStackTraces()
        Log.d(TAG, "Thread Count: " + allStackTraces.size)
        for ((thread, stackTraceElements) in allStackTraces) {
            Log.d(TAG, "Thread Name: " + thread.name)
            Log.d(TAG, "Thread ID: " + thread.id)
            Log.d(TAG, "Thread State: " + thread.state)

            stackTraceElements?.forEach { element ->
                Log.d(TAG, "\t$element")
            }
        }
    }

    private companion object {
        private val TAG = CommonTest::class.java.simpleName
    }
}
