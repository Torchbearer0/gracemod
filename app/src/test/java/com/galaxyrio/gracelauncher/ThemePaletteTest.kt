package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.decodePaletteStyle
import com.galaxyrio.gracelauncher.data.launcherColorScheme
import com.galaxyrio.gracelauncher.data.launcherIconColors
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.materialkolor.utils.ColorUtils
import org.junit.Assert.*
import org.junit.Test

class ThemePaletteTest {
    @Test fun appSeedGeneratesContrastingTrayAndSymbolRolesForEveryStyle() {
        val seed = 0xff1177ee.toInt()
        listOf(false, true).forEach { dark ->
            PaletteStyle.entries.forEach { style ->
                val (tray, symbol) = launcherIconColors(seed, dark, style)
                val expected = launcherColorScheme(seed, dark, style)
                assertEquals(expected.primaryContainer.toArgb(), tray)
                assertEquals(expected.onPrimaryContainer.toArgb(), symbol)
                assertNotEquals(tray, symbol)
                val trayLuminance = ColorUtils.xyzFromArgb(tray)[1] / 100.0
                val symbolLuminance = ColorUtils.xyzFromArgb(symbol)[1] / 100.0
                val contrast = (maxOf(trayLuminance, symbolLuminance) + .05) / (minOf(trayLuminance, symbolLuminance) + .05)
                assertTrue("$style dark=$dark contrast=$contrast", contrast >= 3.0)
            }
        }
        assertNotEquals(launcherIconColors(seed, false, PaletteStyle.TonalSpot),
            launcherIconColors(seed, true, PaletteStyle.TonalSpot))
        assertNotEquals(launcherIconColors(seed, false, PaletteStyle.TonalSpot),
            launcherIconColors(0xffee5511.toInt(), false, PaletteStyle.TonalSpot))
    }

    @Test fun defaultsAndUnknownStoredStylesRemainTonalSpot() {
        assertEquals(PaletteStyle.TonalSpot, LauncherSettings().paletteStyle)
        assertEquals(PaletteStyle.TonalSpot, decodePaletteStyle("unknown"))
        PaletteStyle.entries.forEach { assertEquals(it, decodePaletteStyle(it.name)) }
    }

    @Test fun allSupportedStylesGenerateLightAndDarkSchemesAndChangeTheResult() {
        val seed = 0xFF6750A4.toInt()
        listOf(false, true).forEach { dark ->
            val schemes = PaletteStyle.entries.associateWith { launcherColorScheme(seed, dark, it) }
            assertEquals(9, schemes.size)
            val signatures = schemes.mapValues { (_, colors) ->
                listOf(colors.primary, colors.primaryContainer, colors.secondary, colors.tertiary, colors.surface)
            }
            assertEquals("Each style must change actual color roles, not just the scheme instance", 9, signatures.values.toSet().size)
            assertNotEquals(schemes[PaletteStyle.TonalSpot]!!.primary, schemes[PaletteStyle.Monochrome]!!.primary)
            assertNotEquals(schemes[PaletteStyle.TonalSpot]!!.primary, schemes[PaletteStyle.Expressive]!!.primary)
        }
    }
}
