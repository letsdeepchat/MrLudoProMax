package com.ludosovereign.app

import androidx.compose.ui.graphics.Color
import com.ludosovereign.designsystem.BoardThemes
import com.ludosovereign.designsystem.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audits every theme's menu palette: text is readable on the backdrop and on every button, and
 * cards, pills and the call-to-action never blend into what they sit on.
 */
class ThemeContrastTest {
    private fun check(theme: String, what: String, a: Color, b: Color, min: Double) {
        val r = contrastRatio(a, b)
        assertTrue("$theme: $what contrast %.2f < $min".format(r), r >= min)
    }

    @Test fun everyThemeKeepsMenusReadable() {
        for (t in BoardThemes.ALL) {
            val p = t.ui
            for ((end, bg) in listOf("top" to p.backdropTop, "bottom" to p.backdropBottom)) {
                check(t.name, "text on backdrop $end", p.onBackdrop, bg, 4.5)
                // A card is unmistakable if its face or its border stands 3:1 clear of the backdrop.
                val card = maxOf(contrastRatio(p.card, bg), contrastRatio(p.cardBorder, bg), contrastRatio(p.cardLip, bg))
                assertTrue("${t.name}: card vs backdrop $end %.2f".format(card), card >= 3.0)
                val pill = maxOf(contrastRatio(p.pill, bg), contrastRatio(p.pillBorder, bg))
                assertTrue("${t.name}: pill vs backdrop $end %.2f".format(pill), pill >= 3.0)
            }
            check(t.name, "text on card", p.onCard, p.card, 7.0)
            check(t.name, "text on pill", p.onPill, p.pill, 4.5)
            check(t.name, "text on CTA", p.onCta, p.cta, 4.5)
            check(t.name, "text on primary", p.onPrimary, p.primary, 4.5)
            check(t.name, "controls on card", p.primary, p.card, 3.0)
            val cta = maxOf(contrastRatio(p.cta, p.card), contrastRatio(p.ctaLip, p.card))
            assertTrue("${t.name}: CTA vs card %.2f".format(cta), cta >= 3.0)
        }
    }
}
