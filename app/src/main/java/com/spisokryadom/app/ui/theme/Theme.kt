package com.spisokryadom.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFDCE5EB),
    surfaceTint = Primary,
    surfaceDim = androidx.compose.ui.graphics.Color(0xFFDEE6EB),
    surfaceBright = Surface,
    surfaceContainerLowest = Surface,
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF3F7F9),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFEDF2F5),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFE7EEF2),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFE0E8EE)
)

@Composable
fun SpisokRyadomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
        ),
        content = { com.spisokryadom.app.ui.components.FavoriteAppearanceProvider(content) }
    )
}
