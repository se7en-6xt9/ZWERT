package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Indigo600,
    secondary = Emerald500,
    tertiary = Amber500,
    background = Slate900,
    surface = Slate800,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = Slate50,
    onSurface = Slate50
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo600,
    secondary = Emerald500,
    tertiary = Amber500,
    background = Slate50,
    surface = White,
    onPrimary = White,
    onSecondary = White,
    onTertiary = Slate900,
    onBackground = Slate900,
    onSurface = Slate900
)

@Composable
fun AppTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
