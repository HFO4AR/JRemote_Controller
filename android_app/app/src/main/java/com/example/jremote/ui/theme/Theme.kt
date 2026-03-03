package com.example.jremote.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.jremote.data.ThemeMode

// 深蓝色暗色主题
private val DeepBlueDarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF0D47A1),
    onPrimaryContainer = Color(0xFFD1E4FF),

    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF00504A),
    onSecondaryContainer = Color(0xFFA0F0E9),

    tertiary = Color(0xFFFFB4AB),
    onTertiary = Color(0xFF5C1A13),
    tertiaryContainer = Color(0xFF733228),
    onTertiaryContainer = Color(0xFFFFDAD4),

    error = Color(0xFFEF5350),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0A1929),
    onBackground = Color(0xFFE3F2FD),

    surface = Color(0xFF132F4C),
    onSurface = Color(0xFFE3F2FD),
    surfaceVariant = Color(0xFF1E3A5F),
    onSurfaceVariant = Color(0xFF90CAF9),

    outline = Color(0xFF1976D2),
    outlineVariant = Color(0xFF0D47A1),

    inverseSurface = Color(0xFFE3F2FD),
    inverseOnSurface = Color(0xFF0A1929),
    inversePrimary = Color(0xFF1565C0),

    surfaceTint = Color(0xFF64B5F6),
)

// 米白色浅色主题
private val CreamLightColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF001D36),

    secondary = Color(0xFF00796B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF002020),

    tertiary = Color(0xFFE53935),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFCDD2),
    onTertiaryContainer = Color(0xFF410002),

    error = Color(0xFFD32F2F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFFFFBF5),
    onBackground = Color(0xFF1C1B1F),

    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF5F0E6),
    onSurfaceVariant = Color(0xFF49454F),

    outline = Color(0xFF8D6E63),
    outlineVariant = Color(0xFFD7CCC8),

    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFF90CAF9),

    surfaceTint = Color(0xFF1565C0),
)

@Composable
fun JRemoteTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // 根据设置确定是否使用深色模式
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        // 启用动态颜色（跟随手机壁纸）- 仅在系统是 Android 12+ 时生效
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // 使用自定义的深蓝色/米白色主题
        darkTheme -> DeepBlueDarkColorScheme
        else -> CreamLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
