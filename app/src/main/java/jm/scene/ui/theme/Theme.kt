package jm.scene.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Blue = Color(0xFF1E88E5)
val BlueLight = Color(0xFFBBDEFB)
val Orange = Color(0xFFFFA726)
val PageBg = Color(0xFFEFEFEF)
val CardBg = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = Blue,
    surface = CardBg,
    background = PageBg,
    surfaceVariant = BlueLight,
)

private val DarkColors = darkColorScheme(
    primary = Blue,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
)

@Composable
fun SceneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
