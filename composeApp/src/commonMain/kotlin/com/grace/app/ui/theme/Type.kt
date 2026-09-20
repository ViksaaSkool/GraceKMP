package com.grace.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.grace.app.resources.Res
import com.grace.app.resources.cabin_bold
import com.grace.app.resources.cabin_regular
import org.jetbrains.compose.resources.Font

/**
 * Typography backed by the bundled Cabin TTFs (originally shipped under
 * `assets/fonts` but effectively unused because Calligraphy was disabled).
 */
@Composable
fun graceFontFamily(): FontFamily = FontFamily(
    Font(Res.font.cabin_regular, FontWeight.Normal),
    Font(Res.font.cabin_bold, FontWeight.Bold)
)

@Composable
fun graceTypography(): Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = graceFontFamily()),
        displayMedium = displayMedium.copy(fontFamily = graceFontFamily()),
        displaySmall = displaySmall.copy(fontFamily = graceFontFamily()),
        headlineLarge = headlineLarge.copy(fontFamily = graceFontFamily()),
        headlineMedium = headlineMedium.copy(fontFamily = graceFontFamily()),
        headlineSmall = headlineSmall.copy(fontFamily = graceFontFamily()),
        titleLarge = titleLarge.copy(fontFamily = graceFontFamily()),
        titleMedium = titleMedium.copy(fontFamily = graceFontFamily()),
        titleSmall = titleSmall.copy(fontFamily = graceFontFamily()),
        bodyLarge = bodyLarge.copy(fontFamily = graceFontFamily()),
        bodyMedium = bodyMedium.copy(fontFamily = graceFontFamily()),
        bodySmall = bodySmall.copy(fontFamily = graceFontFamily()),
        labelLarge = labelLarge.copy(fontFamily = graceFontFamily()),
        labelMedium = labelMedium.copy(fontFamily = graceFontFamily()),
        labelSmall = labelSmall.copy(fontFamily = graceFontFamily())
    )
}
