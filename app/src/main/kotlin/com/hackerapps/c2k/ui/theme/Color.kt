package com.hackerapps.c2k.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.hackerapps.c2k.data.model.IntervalType

// Interval colours as fills: the ring, bars and tinted backgrounds. They are too light to read as
// text on a light background (green came out at 1.6:1 on the week cards), so text and icons use
// the readable shades below instead.
val RunOrange       = Color(0xFFFF6B35)
val WalkBlue        = Color(0xFF4A90D9)
val WarmCoolGreen   = Color(0xFF7BC67E)

// Text and icon shades, each at least 4.5:1 against the lightest or darkest background they can
// land on in that theme, including the grey cards and Android's wallpaper-based surfaces.
private val RunOrangeTextLight = Color(0xFFA8380A)
private val WalkBlueTextLight  = Color(0xFF1A62A3)
private val GreenTextLight     = Color(0xFF276C2B)
private val RunOrangeTextDark  = Color(0xFFFFA07A)
private val WalkBlueTextDark   = Color(0xFF9CC4EE)
private val GreenTextDark      = Color(0xFF94D597)

// Reads the theme's background rather than the system setting, so it follows whichever scheme
// C2KTheme actually applied.
@Composable
@ReadOnlyComposable
private fun lightBackground() = MaterialTheme.colorScheme.background.luminance() > 0.5f

val RunOrangeText: Color
    @Composable @ReadOnlyComposable
    get() = if (lightBackground()) RunOrangeTextLight else RunOrangeTextDark

val WalkBlueText: Color
    @Composable @ReadOnlyComposable
    get() = if (lightBackground()) WalkBlueTextLight else WalkBlueTextDark

val GreenText: Color
    @Composable @ReadOnlyComposable
    get() = if (lightBackground()) GreenTextLight else GreenTextDark

@Composable
@ReadOnlyComposable
fun intervalTextColor(type: IntervalType): Color = when (type) {
    IntervalType.RUN      -> RunOrangeText
    IntervalType.WALK     -> WalkBlueText
    IntervalType.WARMUP,
    IntervalType.COOLDOWN -> GreenText
}

// Light scheme. Only used below Android 12, which has no wallpaper colours. Darker than RunOrange,
// which gave white button text 2.8:1; this gives it 5.9:1, and passes as text on the grey cards too.
val PrimaryLight        = Color(0xFFB43C0B)
val OnPrimaryLight      = Color.White
val BackgroundLight     = Color(0xFFFAFAFA)
val SurfaceLight        = Color.White
val OnBackgroundLight   = Color(0xFF1A1A1A)

// Dark scheme
val PrimaryDark         = Color(0xFFFFAB8A)
val OnPrimaryDark       = Color(0xFF5C1800)
val BackgroundDark      = Color(0xFF121212)
val SurfaceDark         = Color(0xFF1E1E1E)
val OnBackgroundDark    = Color(0xFFE8E8E8)
