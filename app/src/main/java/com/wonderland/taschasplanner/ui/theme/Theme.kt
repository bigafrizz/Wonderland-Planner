package com.wonderland.taschasplanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.wonderland.taschasplanner.data.local.ThemeMode

@Immutable
data class WonderlandExtendedColors(
    val goldTrim: Color,
    val cheshireStripeA: Color,
    val cheshireStripeB: Color,
    val cheshireGlow: Color,
    val cardBorder: Color,
    val successAccent: Color,
    val warningAccent: Color
)

val LocalWonderlandColors = staticCompositionLocalOf {
    WonderlandExtendedColors(
        goldTrim = AntiqueGoldLight,
        cheshireStripeA = Color(0xFF4A148C),
        cheshireStripeB = Color(0xFF880E4F),
        cheshireGlow = Color(0xFFF2C94C),
        cardBorder = ParchmentOutline,
        successAccent = Color(0xFF2E7D32),
        warningAccent = Color(0xFFD97706)
    )
}

private val StorybookLightScheme = lightColorScheme(
    primary = CheshireVioletLight,
    onPrimary = Color.White,
    primaryContainer = CheshireVioletContainerLight,
    onPrimaryContainer = OnCheshireVioletContainerLight,
    secondary = WonderlandTealLight,
    onSecondary = Color.White,
    secondaryContainer = WonderlandTealContainerLight,
    onSecondaryContainer = OnWonderlandTealContainerLight,
    tertiary = CheshireMagentaLight,
    onTertiary = Color.White,
    tertiaryContainer = CheshireMagentaContainerLight,
    onTertiaryContainer = OnCheshireMagentaContainerLight,
    background = ParchmentBackground,
    onBackground = InkSepiaText,
    surface = AntiqueIvorySurface,
    onSurface = InkSepiaText,
    surfaceVariant = ParchmentSurfaceVariant,
    onSurfaceVariant = InkMutedText,
    outline = ParchmentOutline,
    error = QueensCrimsonLight,
    onError = Color.White,
    errorContainer = QueensCrimsonContainerLight,
    onErrorContainer = Color(0xFF410002)
)

private val CheshireDarkScheme = darkColorScheme(
    primary = CheshirePurpleDark,
    onPrimary = Color(0xFF220042),
    primaryContainer = CheshirePurpleContainerDark,
    onPrimaryContainer = OnCheshirePurpleContainerDark,
    secondary = EmeraldPotionDark,
    onSecondary = Color(0xFF003824),
    secondaryContainer = EmeraldPotionContainerDark,
    onSecondaryContainer = OnEmeraldPotionContainerDark,
    tertiary = NeonGrinPinkDark,
    onTertiary = Color(0xFF3E001F),
    tertiaryContainer = NeonGrinPinkContainerDark,
    onTertiaryContainer = OnNeonGrinPinkContainerDark,
    background = MidnightPlumBackground,
    onBackground = SoftCreamText,
    surface = VelvetIndigoSurface,
    onSurface = SoftCreamText,
    surfaceVariant = VelvetSurfaceVariant,
    onSurfaceVariant = LavenderMutedText,
    outline = TwilightOutline,
    error = CrimsonRoseDark,
    onError = Color(0xFF410002),
    errorContainer = CrimsonRoseContainerDark,
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightExtendedColors = WonderlandExtendedColors(
    goldTrim = AntiqueGoldLight,
    cheshireStripeA = Color(0xFF4A148C),
    cheshireStripeB = Color(0xFFAD1457),
    cheshireGlow = Color(0xFFFFD54F),
    cardBorder = Color(0xFFDCC9A3),
    successAccent = Color(0xFF2E7D32),
    warningAccent = Color(0xFFD97706)
)

private val DarkExtendedColors = WonderlandExtendedColors(
    goldTrim = MoonlitGoldDark,
    cheshireStripeA = Color(0xFF3A1266),
    cheshireStripeB = Color(0xFF7B1052),
    cheshireGlow = Color(0xFFF2C94C),
    cardBorder = Color(0xFF4B376E),
    successAccent = Color(0xFF2EE59D),
    warningAccent = Color(0xFFFFB74D)
)

@Composable
fun WonderlandTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.STORYBOOK_LIGHT -> false
        ThemeMode.CHESHIRE_DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDark) CheshireDarkScheme else StorybookLightScheme
    val extendedColors = if (useDark) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalWonderlandColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WonderlandTypography,
            content = content
        )
    }
}
