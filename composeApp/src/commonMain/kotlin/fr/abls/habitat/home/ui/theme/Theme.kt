package fr.abls.habitat.home.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/* Palette inspirée de Bootstrap dark (thème du frontend web). */
object AblsColors {
    val Background = Color(0xFF212529)
    val Surface = Color(0xFF2B3035)
    val SurfaceVariant = Color(0xFF343A40)
    val Primary = Color(0xFF0D6EFD)
    val Success = Color(0xFF198754)
    val Warning = Color(0xFFFFC107)
    val Danger = Color(0xFFDC3545)
    val Info = Color(0xFF0DCAF0)
    val Secondary = Color(0xFF6C757D)
    val TextWhite = Color(0xFFF8F9FA)
}

fun bootstrapColor(key: String?): Color = when (key) {
    "success" -> AblsColors.Success
    "warning" -> AblsColors.Warning
    "danger" -> AblsColors.Danger
    "info" -> AblsColors.Info
    "primary" -> AblsColors.Primary
    "secondary" -> AblsColors.Secondary
    else -> AblsColors.TextWhite
}

private val DarkScheme = darkColorScheme(
    primary = AblsColors.Primary,
    onPrimary = Color.White,
    secondary = AblsColors.Secondary,
    background = AblsColors.Background,
    onBackground = AblsColors.TextWhite,
    surface = AblsColors.Surface,
    onSurface = AblsColors.TextWhite,
    surfaceVariant = AblsColors.SurfaceVariant,
    onSurfaceVariant = AblsColors.TextWhite,
    error = AblsColors.Danger,
)

@Composable
fun AblsTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
