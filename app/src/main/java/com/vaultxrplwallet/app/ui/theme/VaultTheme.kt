package com.vaultxrplwallet.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultxrplwallet.app.R
import com.vaultxrplwallet.app.data.AppAppearance

val Accent = Color(0xFF246BFE)
val AccentSoft = Color(0xFF42D3FF)
val Success = Color(0xFF2FB344)
val Warning = Color(0xFFF2A900)
val Danger = Color(0xFFE5484D)
val RlusdFill = Color(0xFF133DC7)
val OrbHighlight = Color(0xFF9ED6FF)

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
)

@Immutable
data class VaultColors(
    val accent: Color = Accent,
    val accentSoft: Color = AccentSoft,
    val success: Color = Success,
    val warning: Color = Warning,
    val danger: Color = Danger,
    val navy: Color,
    val canvas: Color,
    val elevated: Color,
    val elevatedStrong: Color,
    val line: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
) {
    val brandGradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(accent, accentSoft),
            start = Offset.Zero,
            end = Offset.Infinite,
        )
}

val LightVaultColors = VaultColors(
    navy = Color(0xFF0E1E37),
    canvas = Color(0xFFF5F6F8),
    elevated = Color(0xFFFFFFFF),
    elevatedStrong = Color(0xFFEBF0F7),
    line = Color(0x14000000),
    primary = Color(0xFF171C25),
    secondary = Color(0xFF5B6472),
    tertiary = Color(0xFF8B93A0),
)

val DarkVaultColors = VaultColors(
    navy = Color(0xFF071426),
    canvas = Color(0xFF0B0D11),
    elevated = Color(0xFF191C22),
    elevatedStrong = Color(0xFF1B2029),
    line = Color(0x17FFFFFF),
    primary = Color(0xFFDCE4EF),
    secondary = Color(0xFF9AA6B5),
    tertiary = Color(0xFF6E7886),
)

val LocalVaultColors = staticCompositionLocalOf { LightVaultColors }

object Dimens {
    val horizontal = 18.dp
    val section = 18.dp
    val cardRadius = 20.dp
    val fieldRadius = 16.dp
    val fieldMin = 54.dp
    val onboardingPad = 24.dp
}

val VaultTypography = Typography(
    displayLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 48.sp, letterSpacing = (-1.6).sp),
    headlineLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 17.sp),
    bodyMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    labelLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.7.sp),
)

val LocalVaultColorsCurrent: VaultColors
    @Composable get() = LocalVaultColors.current

@Composable
fun VaultTheme(appearance: AppAppearance, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        AppAppearance.SYSTEM -> isSystemInDarkTheme()
        AppAppearance.LIGHT -> false
        AppAppearance.DARK -> true
    }
    val colors = if (dark) DarkVaultColors else LightVaultColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            background = colors.canvas,
            surface = colors.elevated,
            onBackground = colors.primary,
            onSurface = colors.primary,
            error = Danger,
        )
    } else {
        lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            background = colors.canvas,
            surface = colors.elevated,
            onBackground = colors.primary,
            onSurface = colors.primary,
            error = Danger,
        )
    }
    CompositionLocalProvider(LocalVaultColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = VaultTypography, content = content)
    }
}
