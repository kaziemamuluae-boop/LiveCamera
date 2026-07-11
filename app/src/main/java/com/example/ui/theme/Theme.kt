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

private val DarkColorScheme =
  darkColorScheme(
    primary = RedPrimary,
    onPrimary = TextPrimary,
    secondary = GrayCard,
    onSecondary = TextPrimary,
    background = BlackBackground,
    onBackground = TextPrimary,
    surface = GraySurface,
    onSurface = TextPrimary,
    surfaceVariant = GrayCard,
    onSurfaceVariant = TextSecondary,
    error = RedPrimary,
    onError = TextPrimary
  )

private val LightColorScheme = DarkColorScheme // Force dark theme always for professional monitor look

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme always
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve branding
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
