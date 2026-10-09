package top.zekal.koom.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val night = Color(0xFF111A2B)
    val surface = Color(0xFF1D2A3E)
    val surfaceBright = Color(0xFF293850)
    val sunrise = Color(0xFFFFC66B)
    val coral = Color(0xFFFF8171)
    val mint = Color(0xFFA6E8CE)
    val muted = Color(0xFFB3C1D2)
    val ink = Color(0xFF142033)
}

private val colors = darkColorScheme(
    primary = Palette.sunrise,
    onPrimary = Palette.ink,
    secondary = Palette.mint,
    background = Palette.night,
    onBackground = Color.White,
    surface = Palette.surface,
    onSurface = Color.White,
    surfaceVariant = Palette.surfaceBright,
    onSurfaceVariant = Palette.muted,
    error = Palette.coral
)

@Composable
fun KoomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 39.sp, fontWeight = FontWeight.Black),
            headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Bold),
            titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 16.sp),
        ),
        content = content
    )
}
