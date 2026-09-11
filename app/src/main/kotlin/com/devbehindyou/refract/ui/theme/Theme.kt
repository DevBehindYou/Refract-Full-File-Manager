package com.devbehindyou.refract.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val PrimaryBlue = Color(0xFF0F6CBD)
private val OnPrimary = Color(0xFFFFFFFF)
private val PrimaryContainer = Color(0xFFD6E4FF)
private val OnPrimaryContainer = Color(0xFF001B3E)

private val SecondaryTeal = Color(0xFF00758F)
private val SecondaryContainer = Color(0xFFBCE9F5)
private val SurfaceLight = Color(0xFFF9FAFC)
private val SurfaceDark = Color(0xFF131518)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainer,
    background = Color(0xFFF4F6F9),
    onBackground = Color(0xFF191C1E),
    surface = SurfaceLight,
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFE5E9EE),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    onPrimary = Color(0xFF003061),
    primaryContainer = Color(0xFF004689),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF86D2E7),
    onSecondary = Color(0xFF003643),
    secondaryContainer = Color(0xFF004D5F),
    background = Color(0xFF101214),
    onBackground = Color(0xFFE1E2E5),
    surface = SurfaceDark,
    onSurface = Color(0xFFE1E2E5),
    surfaceVariant = Color(0xFF22262B),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
)

@Composable
fun RefractTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
