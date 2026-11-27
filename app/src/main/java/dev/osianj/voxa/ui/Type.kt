package dev.osianj.voxa.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.osianj.voxa.R

/**
 * JetBrains Mono (the NL cut, so no code ligatures in UI text). SIL Open Font License,
 * see licenses/JetBrainsMono-OFL.txt.
 */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(R.font.jetbrainsmono_medium, FontWeight.Medium),
    Font(R.font.jetbrainsmono_bold, FontWeight.Bold),
)

private val base = Typography()

/** Scale tuned for a wide monospace face: smaller sizes than Material's defaults. */
val VoxaTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = JetBrainsMono),
    displayMedium = base.displayMedium.copy(fontFamily = JetBrainsMono),
    displaySmall = base.displaySmall.copy(fontFamily = JetBrainsMono),
    headlineLarge = base.headlineLarge.copy(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = (-1.5).sp,
    ),
    headlineMedium = base.headlineMedium.copy(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp,
    ),
    headlineSmall = base.headlineSmall.copy(fontFamily = JetBrainsMono),
    titleLarge = base.titleLarge.copy(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp,
    ),
    titleMedium = base.titleMedium.copy(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = base.titleSmall.copy(fontFamily = JetBrainsMono),
    bodyLarge = base.bodyLarge.copy(fontFamily = JetBrainsMono, fontSize = 14.sp, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = JetBrainsMono, fontSize = 13.sp, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.copy(fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.sp),
    labelLarge = base.labelLarge.copy(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.8.sp,
    ),
    labelMedium = base.labelMedium.copy(fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 1.sp),
    labelSmall = base.labelSmall.copy(fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.sp),
)
