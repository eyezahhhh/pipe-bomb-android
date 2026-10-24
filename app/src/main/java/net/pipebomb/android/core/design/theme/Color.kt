package net.pipebomb.android.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

object PipeBombPalette {
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)

    val Gray50 = Color(0xFFFAFAFA)
    val Gray100 = Color(0xFFF4F4F5)
    val Gray200 = Color(0xFFE4E4E7)
    val Gray300 = Color(0xFFD4D4D8)
    val Gray400 = Color(0xFFA1A1AA)
    val Gray500 = Color(0xFF71717A)
    val Gray600 = Color(0xFF52525B)
    val Gray700 = Color(0xFF3F3F46)
    val Gray800 = Color(0xFF27272A)
    val Gray900 = Color(0xFF18181B)
    val Gray950 = Color(0xFF09090B)

    val Accent50 = Color(0xFFE6F9F0)
    val Accent100 = Color(0xFFC2F0DB)
    val Accent200 = Color(0xFF8FE6C2)
    val Accent300 = Color(0xFF5CDBAB)
    val Accent400 = Color(0xFF2AD094)
    val Accent500 = Color(0xFF1DB954)
    val Accent600 = Color(0xFF17A34A)
    val Accent700 = Color(0xFF12803A)
    val Accent800 = Color(0xFF0D5C2A)
    val Accent900 = Color(0xFF08381A)

    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val Info = Color(0xFF3B82F6)
}

@Immutable
data class PipeBombColors(
    val bgBase: Color,
    val bgSurface: Color,
    val bgElevated: Color,
    val bgSubtle: Color,
    val bgHover: Color,
    val bgInverse: Color,
    val fgPrimary: Color,
    val fgSecondary: Color,
    val fgTertiary: Color,
    val fgMuted: Color,
    val fgInverse: Color,
    val borderDefault: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val borderFocus: Color,
    val accentPrimary: Color,
    val accentHover: Color,
    val accentActive: Color,
    val accentSubtle: Color,
    val buttonPrimary: Color,
    val buttonPrimaryFg: Color,
    val buttonPrimaryHover: Color,
    val buttonPrimaryActive: Color,
    val buttonPrimaryDisabled: Color,
    val buttonSecondary: Color,
    val buttonSecondaryFg: Color,
    val buttonSecondaryHover: Color,
    val buttonSecondaryActive: Color,
    val buttonSecondaryDisabled: Color,
    val buttonGhostHover: Color,
    val buttonGhostActive: Color,
    val buttonDanger: Color,
    val inputBg: Color,
    val inputFg: Color,
    val inputBorder: Color,
    val inputPlaceholder: Color,
    val sliderTrack: Color,
    val sliderFill: Color,
    val sliderThumb: Color,
    val playerBg: Color,
    val playerFg: Color,
    val playerControls: Color,
    val playerProgressTrack: Color,
    val playerProgressBuffered: Color,
    val playerProgressPlayed: Color,
    val listHover: Color,
    val listActive: Color,
    val listActiveFg: Color,
    val success: Color,
    val successBg: Color,
    val warning: Color,
    val warningBg: Color,
    val error: Color,
    val errorBg: Color,
    val info: Color,
    val infoBg: Color,
    val overlayScrim: Color,
    val overlayHover: Color,
    val overlayActive: Color,
)

val DefaultPipeBombColors = PipeBombColors(
    bgBase = PipeBombPalette.Gray950,
    bgSurface = PipeBombPalette.Gray900,
    bgElevated = PipeBombPalette.Gray800,
    bgSubtle = PipeBombPalette.Gray900,
    bgHover = PipeBombPalette.Gray800,
    bgInverse = PipeBombPalette.White,
    fgPrimary = PipeBombPalette.Gray50,
    fgSecondary = PipeBombPalette.Gray300,
    fgTertiary = PipeBombPalette.Gray500,
    fgMuted = PipeBombPalette.Gray600,
    fgInverse = PipeBombPalette.Gray900,
    borderDefault = PipeBombPalette.Gray800,
    borderSubtle = PipeBombPalette.Gray900,
    borderStrong = PipeBombPalette.Gray700,
    borderFocus = PipeBombPalette.Accent500,
    accentPrimary = PipeBombPalette.Accent500,
    accentHover = PipeBombPalette.Accent400,
    accentActive = PipeBombPalette.Accent600,
    accentSubtle = PipeBombPalette.Accent500.copy(alpha = 0.15f),
    buttonPrimary = PipeBombPalette.Accent500,
    buttonPrimaryFg = PipeBombPalette.White,
    buttonPrimaryHover = PipeBombPalette.Accent400,
    buttonPrimaryActive = PipeBombPalette.Accent600,
    buttonPrimaryDisabled = PipeBombPalette.Gray700,
    buttonSecondary = PipeBombPalette.Gray800,
    buttonSecondaryFg = PipeBombPalette.Gray50,
    buttonSecondaryHover = PipeBombPalette.Gray700,
    buttonSecondaryActive = PipeBombPalette.Gray600,
    buttonSecondaryDisabled = PipeBombPalette.Gray800,
    buttonGhostHover = PipeBombPalette.White.copy(alpha = 0.08f),
    buttonGhostActive = PipeBombPalette.White.copy(alpha = 0.12f),
    buttonDanger = PipeBombPalette.Error,
    inputBg = PipeBombPalette.Gray950,
    inputFg = PipeBombPalette.Gray50,
    inputBorder = PipeBombPalette.Gray800,
    inputPlaceholder = PipeBombPalette.Gray500,
    sliderTrack = PipeBombPalette.Gray700,
    sliderFill = PipeBombPalette.Accent500,
    sliderThumb = PipeBombPalette.White,
    playerBg = PipeBombPalette.Gray900,
    playerFg = PipeBombPalette.Gray50,
    playerControls = PipeBombPalette.Gray300,
    playerProgressTrack = PipeBombPalette.Gray700,
    playerProgressBuffered = PipeBombPalette.Gray500,
    playerProgressPlayed = PipeBombPalette.Accent500,
    listHover = PipeBombPalette.White.copy(alpha = 0.06f),
    listActive = PipeBombPalette.White.copy(alpha = 0.12f),
    listActiveFg = PipeBombPalette.Gray50,
    success = PipeBombPalette.Success,
    successBg = PipeBombPalette.Success.copy(alpha = 0.15f),
    warning = PipeBombPalette.Warning,
    warningBg = PipeBombPalette.Warning.copy(alpha = 0.15f),
    error = PipeBombPalette.Error,
    errorBg = PipeBombPalette.Error.copy(alpha = 0.15f),
    info = PipeBombPalette.Info,
    infoBg = PipeBombPalette.Info.copy(alpha = 0.15f),
    overlayScrim = PipeBombPalette.Black.copy(alpha = 0.6f),
    overlayHover = PipeBombPalette.White.copy(alpha = 0.08f),
    overlayActive = PipeBombPalette.White.copy(alpha = 0.12f),
)
