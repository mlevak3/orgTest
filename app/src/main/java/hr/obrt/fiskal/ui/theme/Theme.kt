package hr.obrt.fiskal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Fiskal Obrt design tokeni (BRAND-UPUTE.md pogl. 3–6, 13) — "maslina + terakota,
 * topli obrtnički POS". Ekrani/komponente čitaju ove tokene preko [LocalFiskalTokens]
 * umjesto sirovih hex vrijednosti.
 */
data class FiskalTokens(
    val bg: Color,
    val bgAlt: Color,
    val surface: Color,
    val surfaceSunken: Color,
    val border: Color,
    val borderStrong: Color,
    val heroHeader: Color,
    val ink: Color,
    val inkSecondary: Color,
    val muted: Color,
    val mutedSoft: Color,
    val placeholder: Color,
    val olive: Color,
    val oliveInk: Color,
    val oliveTint: Color,
    val oliveTintInk: Color,
    val sage: Color,
    val terracotta: Color,
    val terracottaInk: Color,
    val terracottaTint: Color,
    val terracottaTintInk: Color,
    val success: Color,
    val successBg: Color,
    val error: Color,
    val errorBg: Color,
    val errorBorder: Color,
    val shadow: Color,
)

private val LightTokens = FiskalTokens(
    bg = Color(0xFFFAF4E9),
    bgAlt = Color(0xFFF5F0E3),
    surface = Color(0xFFFFFCF5),
    surfaceSunken = Color(0xFFF0EADB),
    border = Color(0xFFEFE8D9),
    borderStrong = Color(0xFFD5CDBA),
    heroHeader = Color(0xFF44523B),
    ink = Color(0xFF2B2A24),
    inkSecondary = Color(0xFF4A4838),
    muted = Color(0xFF757165),
    mutedSoft = Color(0xFF8B8674),
    placeholder = Color(0xFFB4AE9F),
    olive = Color(0xFF44523B),
    oliveInk = Color(0xFFFFFCF5),
    oliveTint = Color(0xFFE8EBDD),
    oliveTintInk = Color(0xFF44523B),
    sage = Color(0xFF8CA06E),
    terracotta = Color(0xFFD2603C),
    terracottaInk = Color(0xFFFFFCF5),
    terracottaTint = Color(0xFFF7E3D8),
    terracottaTintInk = Color(0xFFB54F2C),
    success = Color(0xFF3E7A4E),
    successBg = Color(0xFFE3EFE0),
    error = Color(0xFFB3402A),
    errorBg = Color(0xFFF9E0D9),
    errorBorder = Color(0xFFEAC7BC),
    shadow = Color(0x0F2B2A24),
)

private val DarkTokens = FiskalTokens(
    bg = Color(0xFF1D201A),
    bgAlt = Color(0xFF22261E),
    surface = Color(0xFF272B23),
    surfaceSunken = Color(0xFF20241C),
    border = Color(0xFF3A4033),
    borderStrong = Color(0xFF4A5240),
    heroHeader = Color(0xFF2E3627),
    ink = Color(0xFFF1ECDF),
    inkSecondary = Color(0xFFDAD5C6),
    muted = Color(0xFFA5A18F),
    mutedSoft = Color(0xFF8E8B7B),
    placeholder = Color(0xFF8E8B7B),
    olive = Color(0xFFAFC295),
    oliveInk = Color(0xFF1D201A),
    oliveTint = Color(0x29AFC295),
    oliveTintInk = Color(0xFFAFC295),
    sage = Color(0xFFAFC295),
    terracotta = Color(0xFFE17A52),
    terracottaInk = Color(0xFF1D201A),
    terracottaTint = Color(0x2EE17A52),
    terracottaTintInk = Color(0xFFE17A52),
    success = Color(0xFF8FCF9E),
    successBg = Color(0x2E8FCF9E),
    error = Color(0xFFF2A08C),
    errorBg = Color(0x33E17A52),
    errorBorder = Color(0x80F2A08C),
    shadow = Color(0x4D000000),
)

val LocalFiskalTokens = staticCompositionLocalOf { LightTokens }

/** Radius tokeni (dp) — BRAND-UPUTE.md pogl. 6. */
object FiskalRadius {
    val pill = 999.dp
    val card = 20.dp
    val cardSm = 18.dp
    val tile = 16.dp
    val input = 16.dp
    val hero = 32.dp
}

/** Razmak-skala (dp) — BRAND-UPUTE.md pogl. 6. */
object FiskalSpacing {
    val screenX = 20.dp
    val card = 18.dp
    val stackGap = 14.dp
    val gridGap = 12.dp
    val listPad = 100.dp
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(FiskalRadius.tile),
    small = RoundedCornerShape(FiskalRadius.input),
    medium = RoundedCornerShape(FiskalRadius.cardSm),
    large = RoundedCornerShape(FiskalRadius.card),
    extraLarge = RoundedCornerShape(FiskalRadius.hero),
)

/** Tipografska skala — Sora za naslove/iznose, Manrope za UI tekst (pogl. 5). Fontovi
 * nisu bundlani (nema pristupa mreži za preuzimanje), pa se koristi sistemski font s
 * ispravnim težinama/veličinama iz brand skale; zamijeni [androidx.compose.ui.text.font.FontFamily]
 * dolje sa Sora/Manrope ako se .ttf datoteke dodaju u res/font. */
private fun appTypography(tokens: FiskalTokens): Typography {
    val display = FontWeight.Bold // Sora 700
    val displaySemi = FontWeight.SemiBold // Sora 600
    val ui = FontWeight.Medium // Manrope 500-600
    val uiBold = FontWeight.ExtraBold // Manrope 800
    return Typography(
        displaySmall = TextStyle(fontWeight = display, fontSize = 42.sp, letterSpacing = (-0.8).sp),
        headlineSmall = TextStyle(fontWeight = display, fontSize = 22.sp),
        titleLarge = TextStyle(fontWeight = display, fontSize = 18.sp),
        titleMedium = TextStyle(fontWeight = displaySemi, fontSize = 17.sp),
        titleSmall = TextStyle(fontWeight = display, fontSize = 15.sp),
        bodyLarge = TextStyle(fontWeight = uiBold, fontSize = 14.sp),
        bodyMedium = TextStyle(fontWeight = ui, fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontWeight = ui, fontSize = 12.5.sp),
        labelLarge = TextStyle(fontWeight = uiBold, fontSize = 15.sp),
        labelMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
        labelSmall = TextStyle(fontWeight = uiBold, fontSize = 11.sp, letterSpacing = 1.3.sp),
    )
}

@Composable
fun FiskalTheme(tema: hr.obrt.fiskal.data.TemaAplikacije = hr.obrt.fiskal.data.TemaAplikacije.SUSTAV, content: @Composable () -> Unit) {
    val tamna = when (tema) {
        hr.obrt.fiskal.data.TemaAplikacije.SUSTAV -> isSystemInDarkTheme()
        hr.obrt.fiskal.data.TemaAplikacije.SVIJETLA -> false
        hr.obrt.fiskal.data.TemaAplikacije.TAMNA -> true
    }
    val tokens = if (tamna) DarkTokens else LightTokens

    // Material3 ColorScheme mapiran na brand tokene, tako da postojeće komponente
    // (Card, Button, Chip...) koje čitaju MaterialTheme.colorScheme automatski
    // dobiju ispravne brand boje. Terakota je namjerno NA "secondary" ulozi (rezervirana
    // isključivo za primarnu akciju preko dediciranih komponenti), a ne na "primary".
    val colorScheme = if (tamna) {
        darkColorScheme(
            primary = tokens.olive, onPrimary = tokens.oliveInk,
            primaryContainer = tokens.oliveTint, onPrimaryContainer = tokens.oliveTintInk,
            secondary = tokens.terracotta, onSecondary = tokens.terracottaInk,
            secondaryContainer = tokens.terracottaTint, onSecondaryContainer = tokens.terracottaTintInk,
            tertiary = tokens.sage, onTertiary = tokens.oliveInk,
            tertiaryContainer = tokens.oliveTint, onTertiaryContainer = tokens.oliveTintInk,
            background = tokens.bg, onBackground = tokens.ink,
            surface = tokens.surface, onSurface = tokens.ink,
            surfaceVariant = tokens.surfaceSunken, onSurfaceVariant = tokens.muted,
            outline = tokens.borderStrong, outlineVariant = tokens.border,
            error = tokens.error, onError = tokens.terracottaInk,
            errorContainer = tokens.errorBg, onErrorContainer = tokens.error,
        )
    } else {
        lightColorScheme(
            primary = tokens.olive, onPrimary = tokens.oliveInk,
            primaryContainer = tokens.oliveTint, onPrimaryContainer = tokens.oliveTintInk,
            secondary = tokens.terracotta, onSecondary = tokens.terracottaInk,
            secondaryContainer = tokens.terracottaTint, onSecondaryContainer = tokens.terracottaTintInk,
            tertiary = tokens.sage, onTertiary = tokens.oliveInk,
            tertiaryContainer = tokens.oliveTint, onTertiaryContainer = tokens.oliveTintInk,
            background = tokens.bg, onBackground = tokens.ink,
            surface = tokens.surface, onSurface = tokens.ink,
            surfaceVariant = tokens.surfaceSunken, onSurfaceVariant = tokens.muted,
            outline = tokens.borderStrong, outlineVariant = tokens.border,
            error = tokens.error, onError = tokens.terracottaInk,
            errorContainer = tokens.errorBg, onErrorContainer = tokens.error,
        )
    }

    CompositionLocalProvider(LocalFiskalTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = AppShapes,
            typography = appTypography(tokens),
            content = content,
        )
    }
}
