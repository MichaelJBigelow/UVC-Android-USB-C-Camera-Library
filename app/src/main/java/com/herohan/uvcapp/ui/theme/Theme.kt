package com.herohan.uvcapp.ui.theme

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand colors carried over from the previous XML theme (res/values/colors.xml).
// 沿用自原 XML 主题(res/values/colors.xml)的品牌色。
val Purple200 = Color(0xFFBB86FC)
val Purple500 = Color(0xFF6200EE)
val Purple700 = Color(0xFF3700B3)
val Teal200 = Color(0xFF03DAC5)
val Teal700 = Color(0xFF018786)

/**
 * Accent color of the camera controls panel (sliders, radio buttons, buttons).
 *
 * 相机控制面板(滑块、单选按钮、按钮)的强调色。
 */
val DeepPink = Color(0xFFFF1493)

/**
 * Text color of labels on the dark, translucent camera controls panel.
 *
 * 深色半透明相机控制面板上标签的文字颜色。
 */
val Gray400 = Color(0xFFBDBDBD)

/**
 * Text color of the field labels in the video format dialog.
 *
 * 视频格式对话框中字段标签的文字颜色。
 */
val Gray600 = Color(0xFF757575)

/**
 * Color of the record button and the recording dot while a video is being recorded.
 *
 * 正在录制视频时，录制按钮和录制圆点的颜色。
 */
val RecordRed = Color(0xFFFF0000)

private val LightColors = lightColorScheme(
    primary = Purple500,
    onPrimary = Color.White,
    primaryContainer = Purple700,
    onPrimaryContainer = Color.White,
    secondary = Teal200,
    onSecondary = Color.Black,
    tertiary = Teal700,
)

private val DarkColors = darkColorScheme(
    primary = Purple200,
    onPrimary = Color.Black,
    primaryContainer = Purple700,
    onPrimaryContainer = Color.White,
    secondary = Teal200,
    onSecondary = Color.Black,
    tertiary = Teal200,
)

/**
 * Material 3 theme of the app; follows the system light/dark setting.
 *
 * 应用的 Material 3 主题；跟随系统的浅色/深色设置。
 */
@Composable
fun UvcAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

/**
 * Draws the activity behind the system bars, with status bar icons that contrast with the top app
 * bar: light icons on the dark purple bar of the light theme, dark icons on the light purple bar of
 * the dark theme.
 *
 * 让 Activity 绘制到系统栏后方，并使状态栏图标与顶部应用栏形成对比：
 * 浅色主题的深紫色应用栏上使用浅色图标，深色主题的浅紫色应用栏上使用深色图标。
 */
fun ComponentActivity.enableEdgeToEdgeBelowAppBar() {
    val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val transparent = android.graphics.Color.TRANSPARENT
    enableEdgeToEdge(
        statusBarStyle = if (isNightMode) {
            SystemBarStyle.light(transparent, transparent)
        } else {
            SystemBarStyle.dark(transparent)
        },
    )
}
