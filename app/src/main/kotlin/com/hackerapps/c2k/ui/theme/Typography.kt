package com.hackerapps.c2k.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Each style pins an explicit lineHeight (matching the Material3 baseline type scale ratios)
// instead of leaving it unspecified. Without one, Compose derives line height from the device's
// own font metrics, which vary a lot across devices/GSI builds — on some, a wrapped multi-line
// title (e.g. a long translated program name) ends up with a much taller line height than on
// others, leaving a large-looking empty gap before whatever follows it. An explicit value fixes
// the vertical rhythm regardless of the system font.
val C2KTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)
