package com.herohan.uvcsample.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 theme shared by the sample apps; follows the system light/dark setting.
 *
 * On Android 12+ the colors come from the user's wallpaper (dynamic color); on older versions they
 * are the Material 3 baseline scheme.
 *
 * 示例应用共用的 Material 3 主题；跟随系统的浅色/深色设置。
 *
 * 在 Android 12 及以上版本中，颜色取自用户的壁纸(动态配色)；在更早的版本中使用 Material 3 基准配色方案。
 *
 * @param dynamicColor whether to use the wallpaper's colors where available / 在可用时是否使用壁纸的颜色
 */
@Composable
fun UvcSampleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = sampleColorScheme(darkTheme, dynamicColor),
        content = content,
    )
}

@Composable
private fun sampleColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme =
    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) darkColorScheme() else lightColorScheme()
    }
