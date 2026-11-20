package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CinnabarPrimary,
    onPrimary = CinnabarOnPrimary,
    primaryContainer = CinnabarContainer,
    onPrimaryContainer = CinnabarOnContainer,
    secondary = AmberGoldSecondary,
    onSecondary = AmberGoldOnSecondary,
    secondaryContainer = AmberGoldContainer,
    onSecondaryContainer = AmberGoldOnContainer,
    tertiary = ZenJadeTertiary,
    onTertiary = ZenJadeOnTertiary,
    tertiaryContainer = ZenJadeContainer,
    onTertiaryContainer = ZenJadeOnContainer,
    background = PaperBackground,
    onBackground = InkCharcoal,
    surface = PaperBackground,
    onSurface = InkCharcoal,
    surfaceVariant = LinenSurface,
    onSurfaceVariant = StoneMuted,
    surfaceContainerLowest = AlabasterCard,
    surfaceContainerLow = AlabasterCard,
    surfaceContainer = LinenSurface,
    surfaceContainerHigh = SandSurfaceHigh,
    outline = PaperBorder,
    outlineVariant = SubtleDivider,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = CoralCinnabarDark,
    onPrimary = OnCoralDark,
    primaryContainer = CinnabarContainerDark,
    onPrimaryContainer = OnCinnabarContainerDark,
    secondary = ChampagneGoldDark,
    onSecondary = OnChampagneDark,
    secondaryContainer = GoldContainerDark,
    onSecondaryContainer = OnGoldContainerDark,
    tertiary = MistyJadeDark,
    onTertiary = OnMistyJadeDark,
    tertiaryContainer = JadeContainerDark,
    onTertiaryContainer = OnJadeContainerDark,
    background = ObsidianBackground,
    onBackground = ParchmentText,
    surface = ObsidianBackground,
    onSurface = ParchmentText,
    surfaceVariant = InkSlateSurface,
    onSurfaceVariant = AshMutedText,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = CharcoalCard,
    surfaceContainer = InkSlateSurface,
    surfaceContainerHigh = UmberSurfaceHigh,
    outline = DarkBorder,
    outlineVariant = DarkSubtleDivider
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GreetingPromptDesignerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
