package com.xzo.agent.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.xzo.agent.R

/**
 * Bundled typefaces — no network fetch, no Google Fonts provider dependency.
 *
 *  * **Inter** (variable, OFL) for the UI. One file covers every weight through
 *    `FontVariation`, which is supported from API 26 and this app is minSdk 28.
 *  * **Noto Naskh Arabic** (variable, OFL) so Arabic renders beautifully instead of
 *    falling back to whatever the OEM ships — important, this app is used in Arabic.
 *  * **JetBrains Mono** for code blocks: a true coding face with clear 0/O and 1/l/I.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
object XzoFonts {

    private fun interWeight(w: Int) = Font(
        R.font.inter,
        weight = FontWeight(w),
        variationSettings = FontVariation.Settings(FontVariation.weight(w))
    )

    private fun arabicWeight(w: Int) = Font(
        R.font.noto_naskh_arabic,
        weight = FontWeight(w),
        variationSettings = FontVariation.Settings(FontVariation.weight(w))
    )

    /**
     * Latin first, Arabic second: Compose falls through to the next family for any
     * glyph Inter does not contain, which is exactly the Arabic range.
     */
    val Sans = FontFamily(
        interWeight(300), interWeight(400), interWeight(500),
        interWeight(600), interWeight(700),
        arabicWeight(400), arabicWeight(500), arabicWeight(700)
    )

    val Mono = FontFamily(Font(R.font.jetbrains_mono, weight = FontWeight.Normal))
}
