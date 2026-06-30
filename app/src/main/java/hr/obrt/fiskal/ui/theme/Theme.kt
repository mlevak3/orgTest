package hr.obrt.fiskal.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val Brand = Color(0xFF00696E)       // teal
private val BrandDark = Color(0xFF4FD8DF)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6FF6FD),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365),
    tertiary = Color(0xFF4F5F7E),
    background = Color(0xFFF6FBFB),
    surface = Color(0xFFF6FBFB),
    surfaceVariant = Color(0xFFDAE4E4),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = BrandDark,
    onPrimary = Color(0xFF00373A),
    primaryContainer = Color(0xFF004F53),
    onPrimaryContainer = Color(0xFF6FF6FD),
    secondary = Color(0xFFB1CBCD),
    tertiary = Color(0xFFB7C7EA),
    background = Color(0xFF0F1414),
    surface = Color(0xFF0F1414),
    surfaceVariant = Color(0xFF3F4949),
    error = Color(0xFFFFB4AB),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun FiskalTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
}
