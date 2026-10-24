package net.pipebomb.android.core.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val DarkColorScheme = darkColorScheme(
    primary = DefaultPipeBombColors.accentPrimary,
    onPrimary = DefaultPipeBombColors.buttonPrimaryFg,
    primaryContainer = PipeBombPalette.Accent900,
    onPrimaryContainer = DefaultPipeBombColors.fgPrimary,
    secondary = DefaultPipeBombColors.buttonSecondary,
    onSecondary = DefaultPipeBombColors.buttonSecondaryFg,
    background = DefaultPipeBombColors.bgBase,
    onBackground = DefaultPipeBombColors.fgPrimary,
    surface = DefaultPipeBombColors.bgSurface,
    onSurface = DefaultPipeBombColors.fgPrimary,
    surfaceVariant = DefaultPipeBombColors.bgElevated,
    onSurfaceVariant = DefaultPipeBombColors.fgSecondary,
    outline = DefaultPipeBombColors.borderStrong,
    outlineVariant = DefaultPipeBombColors.borderDefault,
    error = DefaultPipeBombColors.error,
    onError = PipeBombPalette.White,
    scrim = DefaultPipeBombColors.overlayScrim,
)

private val LocalPipeBombColors = staticCompositionLocalOf { DefaultPipeBombColors }
private val LocalPipeBombTextStyles = staticCompositionLocalOf { DefaultPipeBombTextStyles }
private val LocalPipeBombSpacing = staticCompositionLocalOf { DefaultPipeBombSpacing }
private val LocalPipeBombShapes = staticCompositionLocalOf { DefaultPipeBombShapes }
private val LocalPipeBombMotion = staticCompositionLocalOf { DefaultPipeBombMotion }

object PipeBombTheme {
    val colors: PipeBombColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPipeBombColors.current

    val typography: PipeBombTextStyles
        @Composable
        @ReadOnlyComposable
        get() = LocalPipeBombTextStyles.current

    val spacing: PipeBombSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalPipeBombSpacing.current

    val shapes: PipeBombShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalPipeBombShapes.current

    val motion: PipeBombMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalPipeBombMotion.current
}

@Composable
fun PipeBombTheme(
    colors: PipeBombColors = DefaultPipeBombColors,
    typography: PipeBombTextStyles = DefaultPipeBombTextStyles,
    spacing: PipeBombSpacing = DefaultPipeBombSpacing,
    shapes: PipeBombShapes = DefaultPipeBombShapes,
    motion: PipeBombMotion = DefaultPipeBombMotion,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPipeBombColors provides colors,
        LocalPipeBombTextStyles provides typography,
        LocalPipeBombSpacing provides spacing,
        LocalPipeBombShapes provides shapes,
        LocalPipeBombMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = PipeBombTypography,
            shapes = Shapes(
                extraSmall = shapes.small,
                small = shapes.small,
                medium = shapes.medium,
                large = shapes.large,
                extraLarge = shapes.large,
            ),
            content = content,
        )
    }
}
