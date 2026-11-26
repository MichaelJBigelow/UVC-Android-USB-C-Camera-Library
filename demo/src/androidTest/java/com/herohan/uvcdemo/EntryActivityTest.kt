package com.herohan.uvcdemo

import android.Manifest
import android.app.Activity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.herohan.uvcdemo.ui.TestTags
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.herohan.uvcsample.R as SampleR

/**
 * Checks that the entry screen lists every demo, and that each demo opens and returns to the list.
 * With no USB camera the demos show an empty preview, so this runs on an emulator. It is skipped
 * when a camera is attached: each demo would then ask for USB permission in a system dialog the test
 * can't answer.
 *
 * 检查入口界面列出了所有演示，且每个演示都能打开并返回列表。没有 USB 相机时演示显示空白预览，
 * 因此可在模拟器上运行。连接了相机时跳过：此时每个演示都会通过系统对话框请求 USB 权限，而测试无法应答。
 */
@RunWith(AndroidJUnit4::class)
class EntryActivityTest {

    /**
     * Grants what the demos ask for before they open, so no permission dialog appears.
     *
     * 在演示打开前授予其所需的权限，使权限对话框不会出现。
     */
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<EntryActivity>()

    private val navigateUp: String get() = composeRule.activity.getString(SampleR.string.navigate_up)

    private val demos: List<Class<out Activity>> = listOf(
        BasicPreviewActivity::class.java,
        CustomPreviewActivity::class.java,
        MultiPreviewActivity::class.java,
        MultiCameraNewActivity::class.java,
        MultiCameraActivity::class.java,
        TakePictureActivity::class.java,
        RecordVideoActivity::class.java,
        SetFrameCallbackActivity::class.java,
    )

    private fun entry(demo: Class<out Activity>) =
        composeRule.onNodeWithTag(TestTags.entryButton(demo.simpleName)).performScrollTo()

    /** Whether a demo screen, which has a navigate up button, is showing. / 是否正在显示演示界面(其带有返回按钮)。 */
    private fun isDemoShowing(): Boolean =
        composeRule.onAllNodesWithContentDescription(navigateUp).fetchSemanticsNodes().isNotEmpty()

    @Before
    fun requireNoCamera() {
        assumeFalse("needs no UVC camera attached", isUvcCameraAttached(composeRule.activity))
    }

    @Test
    fun listsEveryDemo() {
        demos.forEach { demo -> entry(demo).assertIsDisplayed() }
    }

    @Test
    fun everyDemoOpensAndNavigatesBackToTheList() {
        demos.forEach { demo ->
            entry(demo).performClick()
            composeRule.waitUntil(TIMEOUT_MS) { isDemoShowing() }

            composeRule.onNodeWithContentDescription(navigateUp).performClick()
            composeRule.waitUntil(TIMEOUT_MS) { !isDemoShowing() }

            entry(demo).assertIsDisplayed()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
