package com.psycode.spotiflac.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class SpotiflacPalette { Oceanic, IndigoNight, EmeraldPulse, AmberGlow }

private val TrueBackground = Color(0xFF000000)
private val TrueOnBackground = Color(0xFFE9EDF4)

private val TrueSurface = Color(0xFF0C0F14)
private val TrueOnSurface = Color(0xFFE5E9F1)

private val TrueSurfaceVariant = Color(0xFF10151C)
private val TrueOnSurfaceVariant = Color(0xFF9DA6B2)

private val TrueOutline = Color(0xFF2A313A)
private val TrueOutlineVariant = Color(0xFF343C46)

private val TrueContainerLowest = Color(0xFF050607)
private val TrueContainerLow = Color(0xFF0A0D11)
private val TrueContainer = Color(0xFF0D1116)
private val TrueContainerHigh = Color(0xFF10151C)
private val TrueContainerHighest = Color(0xFF141A22)

private val OnPrimaryText = Color(0xFF070D10)
private val OnErrorText = Color(0xFF2B0A0A)

private val TrueError = Color(0xFFFF6B6B)

private val OceanicAccents = Triple(
    Color(0xFF66D9E8),
    Color(0xFF74D1C4),
    Color(0xFF9BE7DA),
)

private val IndigoAccents = Triple(
    Color(0xFF9DB5FF),
    Color(0xFFAAB7E4),
    Color(0xFFCDAFFC),
)

private val EmeraldAccents = Triple(
    Color(0xFF63E6BE),
    Color(0xFF76D8A8),
    Color(0xFFC8A6F7),
)

private val AmberAccents = Triple(
    Color(0xFFFFCC66),
    Color(0xFFE8BA5C),
    Color(0xFFB4C6FF),
)

private fun buildDarkScheme(accents: Triple<Color, Color, Color>) = darkColorScheme(
    primary = accents.first,
    onPrimary = OnPrimaryText,

    secondary = accents.second,
    onSecondary = OnPrimaryText,

    tertiary = accents.third,
    onTertiary = OnPrimaryText,

    background = TrueBackground,
    onBackground = TrueOnBackground,

    surface = TrueSurface,
    onSurface = TrueOnSurface,

    surfaceVariant = TrueSurfaceVariant,
    onSurfaceVariant = TrueOnSurfaceVariant,

    outline = TrueOutline,
    outlineVariant = TrueOutlineVariant,

    surfaceContainerLowest = TrueContainerLowest,
    surfaceContainerLow = TrueContainerLow,
    surfaceContainer = TrueContainer,
    surfaceContainerHigh = TrueContainerHigh,
    surfaceContainerHighest = TrueContainerHighest,

    error = TrueError,
    onError = OnErrorText,

    surfaceTint = Color.Transparent,
)

private val OceanicContainers = buildDarkScheme(OceanicAccents).copy(
    primaryContainer = Color(0xFF0F2E34),
    onPrimaryContainer = Color(0xFFADE7F2),

    secondaryContainer = Color(0xFF13332E),
    onSecondaryContainer = Color(0xFFBDEDE2),

    tertiaryContainer = Color(0xFF1A3431),
    onTertiaryContainer = Color(0xFFCFF5ED),
)

private val IndigoContainers = buildDarkScheme(IndigoAccents).copy(
    primaryContainer = Color(0xFF1F2740),
    onPrimaryContainer = Color(0xFFD7E2FF),

    secondaryContainer = Color(0xFF242B3E),
    onSecondaryContainer = Color(0xFFD9DEF0),

    tertiaryContainer = Color(0xFF2F2442),
    onTertiaryContainer = Color(0xFFEAD9FF),
)

private val EmeraldContainers = buildDarkScheme(EmeraldAccents).copy(
    primaryContainer = Color(0xFF0F2F24),
    onPrimaryContainer = Color(0xFFBDFBE2),

    secondaryContainer = Color(0xFF153424),
    onSecondaryContainer = Color(0xFFCCF5E1),

    tertiaryContainer = Color(0xFF2A1F3A),
    onTertiaryContainer = Color(0xFFEAD9FF),
)

private val AmberContainers = buildDarkScheme(AmberAccents).copy(
    primaryContainer = Color(0xFF332407),
    onPrimaryContainer = Color(0xFFFFE8B5),

    secondaryContainer = Color(0xFF2E2208),
    onSecondaryContainer = Color(0xFFFCE3A6),

    tertiaryContainer = Color(0xFF11233F),
    onTertiaryContainer = Color(0xFFDDE7FF),
)

val OceanicDarkColorScheme = OceanicContainers
val IndigoNightDarkColorScheme = IndigoContainers
val EmeraldPulseDarkColorScheme = EmeraldContainers
val AmberGlowDarkColorScheme = AmberContainers

@Composable
fun SpotiflacTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    palette: SpotiflacPalette = SpotiflacPalette.IndigoNight,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val baseScheme =
        if (darkTheme && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dyn = dynamicDarkColorScheme(context)
            dyn.copy(
                background = TrueBackground,
                onBackground = TrueOnBackground,

                surface = TrueSurface,
                onSurface = TrueOnSurface,

                surfaceVariant = TrueSurfaceVariant,
                onSurfaceVariant = TrueOnSurfaceVariant,

                outline = TrueOutline,
                outlineVariant = TrueOutlineVariant,

                surfaceContainerLowest = TrueContainerLowest,
                surfaceContainerLow = TrueContainerLow,
                surfaceContainer = TrueContainer,
                surfaceContainerHigh = TrueContainerHigh,
                surfaceContainerHighest = TrueContainerHighest,

                error = TrueError,
                onError = OnErrorText,

                surfaceTint = Color.Transparent,
            )
        } else {
            when (palette) {
                SpotiflacPalette.Oceanic -> OceanicDarkColorScheme
                SpotiflacPalette.IndigoNight -> IndigoNightDarkColorScheme
                SpotiflacPalette.EmeraldPulse -> EmeraldPulseDarkColorScheme
                SpotiflacPalette.AmberGlow -> AmberGlowDarkColorScheme
            }
        }

    MaterialTheme(
        colorScheme = baseScheme,
        typography = Typography,
        content = content,
    )
}
