package app.qrcard.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Brand colours, taken from the Ventsim logo. */
object Brand {
    val Navy = Color(0xFF001E64)
    val Orange = Color(0xFFF47821)
    val OrangeLight = Color(0xFFFFE3D0)
    val NavyLight = Color(0xFFDCE3F5)
}

private val LightColors = lightColorScheme(
    primary = Brand.Navy,
    onPrimary = Color.White,
    // Orange circles and buttons with navy on top, like the logo.
    primaryContainer = Brand.Orange,
    onPrimaryContainer = Brand.Navy,
    secondary = Brand.Orange,
    onSecondary = Brand.Navy,
    secondaryContainer = Brand.OrangeLight,
    onSecondaryContainer = Brand.Navy,
    tertiary = Brand.Orange,
    onTertiary = Brand.Navy,
    surfaceVariant = Color(0xFFEEF1F8),
    onSurfaceVariant = Color(0xFF44495A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Brand.Navy,
    primaryContainer = Brand.Orange,
    onPrimaryContainer = Brand.Navy,
    secondary = Color(0xFFFFB68A),
    onSecondary = Color(0xFF3A1A00),
    secondaryContainer = Color(0xFF5A2E0A),
    onSecondaryContainer = Brand.OrangeLight,
    tertiary = Brand.Orange,
    onTertiary = Brand.Navy,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/** Navy top bars with white titles and icons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun brandBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Brand.Navy,
    titleContentColor = Color.White,
    navigationIconContentColor = Color.White,
    actionIconContentColor = Color.White,
)
