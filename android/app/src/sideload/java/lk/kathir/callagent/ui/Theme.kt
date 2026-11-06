package lk.kathir.callagent.ui

import android.app.Activity
import android.graphics.Color as AColor
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Brand: deep teal + warm amber
private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1E6), onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFFA16207), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDE9A8), onSecondaryContainer = Color(0xFF2B1D00),
    tertiary = Color(0xFF4458A8), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE1FF), onTertiaryContainer = Color(0xFF001452),
    background = Color(0xFFF4F8F7), onBackground = Color(0xFF161D1C),
    surface = Color(0xFFF4F8F7), onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E2), onSurfaceVariant = Color(0xFF3F4947),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEEF5F3),
    surfaceContainer = Color(0xFFE8EFED), surfaceContainerHigh = Color(0xFFE2EAE8),
    surfaceContainerHighest = Color(0xFFDDE4E2),
    outline = Color(0xFF6F7977), outlineVariant = Color(0xFFBEC9C6),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FDACB), onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFFB4F1E6),
    secondary = Color(0xFFF5C451), onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFF5C4300), onSecondaryContainer = Color(0xFFFDE9A8),
    tertiary = Color(0xFFB7C4FF), onTertiary = Color(0xFF0E2A78),
    tertiaryContainer = Color(0xFF2B408F), onTertiaryContainer = Color(0xFFDCE1FF),
    background = Color(0xFF0D1514), onBackground = Color(0xFFDDE4E2),
    surface = Color(0xFF0D1514), onSurface = Color(0xFFDDE4E2),
    surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceContainerLowest = Color(0xFF08100F), surfaceContainerLow = Color(0xFF161D1C),
    surfaceContainer = Color(0xFF1A2120), surfaceContainerHigh = Color(0xFF242B2A),
    surfaceContainerHighest = Color(0xFF2F3635),
    outline = Color(0xFF889391), outlineVariant = Color(0xFF3F4947),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** themeMode: 0 = follow system, 1 = light, 2 = dark. dynamic = Android 12+ wallpaper colours. */
@Composable
fun AgentTheme(themeMode: Int, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    // status/navigation bar icons follow the app theme (not just the system setting)
    DisposableEffect(dark) {
        (ctx as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
                             else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
                                 else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
        )
        onDispose {}
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}

@Suppress("unused")
private fun Activity.unusedRef() = Unit
