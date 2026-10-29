package com.example.learnenglish

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ThemeSettings {
    private const val PREFS = "ui_prefs"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_DYNAMIC = "dynamic_color"

    private val _mode = MutableStateFlow("system")
    private val _dynamic = MutableStateFlow(false)
    val mode: StateFlow<String> = _mode
    val dynamic: StateFlow<Boolean> = _dynamic

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _mode.value = p.getString(KEY_MODE, "system") ?: "system"
        _dynamic.value = p.getBoolean(KEY_DYNAMIC, false)
    }

    fun setMode(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, value)
            .apply()
        _mode.value = value
    }

    fun setDynamic(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DYNAMIC, value)
            .apply()
        _dynamic.value = value
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E6FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFFB26A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE3A8),
    onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Color(0xFF00796B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB7F0E4),
    onTertiaryContainer = Color(0xFF00201B),
    background = Color(0xFFF7F9FF),
    onBackground = Color(0xFF181C22),
    surface = Color(0xFFF7F9FF),
    onSurface = Color(0xFF181C22),
    surfaceVariant = Color(0xFFDFE6F5),
    onSurfaceVariant = Color(0xFF424A59),
    outline = Color(0xFF727A8A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5FC),
    surfaceContainer = Color(0xFFEDF2FB),
    surfaceContainerHigh = Color(0xFFE8EEFA),
    surfaceContainerHighest = Color(0xFFE1E9F7)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF002F66),
    primaryContainer = Color(0xFF0F4A97),
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = Color(0xFFFFC857),
    onSecondary = Color(0xFF432C00),
    secondaryContainer = Color(0xFF5F4100),
    onSecondaryContainer = Color(0xFFFFE3A8),
    tertiary = Color(0xFF6FD9C6),
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005046),
    onTertiaryContainer = Color(0xFFB7F0E4),
    background = Color(0xFF10141B),
    onBackground = Color(0xFFE0E2EB),
    surface = Color(0xFF10141B),
    onSurface = Color(0xFFE0E2EB),
    surfaceVariant = Color(0xFF3A4252),
    onSurfaceVariant = Color(0xFFC2C9D9),
    outline = Color(0xFF8C93A3),
    surfaceContainerLowest = Color(0xFF0B0F15),
    surfaceContainerLow = Color(0xFF181E29),
    surfaceContainer = Color(0xFF1D2430),
    surfaceContainerHigh = Color(0xFF232B38),
    surfaceContainerHighest = Color(0xFF2A3342)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode by ThemeSettings.mode.collectAsState()
    val dynamic by ThemeSettings.dynamic.collectAsState()
    val systemDark = isSystemInDarkTheme()

    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    val colors = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}
