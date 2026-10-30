package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.icons.monochromeMaskPixels
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class IconMonochromeMaskTest {
    private fun alpha(pixels: IntArray) = pixels.map { it ushr 24 }.toIntArray()

    @Test fun defaultExtractsDarkInkAndInversionExtractsLightInk() {
        val source = intArrayOf(0, 0xFF404040.toInt(), 0x80808080.toInt(), 0xFFC0C0C0.toInt())
        assertArrayEquals(intArrayOf(0, 255, 64, 0), alpha(monochromeMaskPixels(source)))
        assertArrayEquals(intArrayOf(0, 0, 64, 255), alpha(monochromeMaskPixels(source, invert = true)))
    }

    @Test fun nearTransparentEdgeColorsCannotLeaveAGhostOfTheBackground() {
        val source = intArrayOf(0xFFDBEBFA.toInt(), 0xFF24354C.toInt(), 0x01FFFFFF, 0x01000000)
        val normal = monochromeMaskPixels(source)
        assertEquals(0, normal[0])
        assertEquals(255, normal[1] ushr 24)
        val inverted = monochromeMaskPixels(source, invert = true)
        assertEquals(255, inverted[0] ushr 24)
        assertEquals(0, inverted[1])
    }

    @Test fun translucentArtworkUsesItsOwnOpaqueInteriorAndKeepsCoverage() {
        val source = intArrayOf(0x80C0C0C0.toInt(), 0x80404040.toInt(), 0x40404040, 0x01FFFFFF)
        val result = monochromeMaskPixels(source)
        assertEquals(0, result[0])
        assertEquals(128, result[1] ushr 24)
        assertEquals(64, result[2] ushr 24)
    }

    @Test fun antialiasedSymbolEdgesStayContinuousInsteadOfBecomingBinary() {
        val source = intArrayOf(0xFF000000.toInt(), 0xFF404040.toInt(), 0xFF808080.toInt(),
            0xFFC0C0C0.toInt(), 0xFFFFFFFF.toInt())
        assertArrayEquals(intArrayOf(255, 191, 127, 63, 0), alpha(monochromeMaskPixels(source)))
    }

    @Test fun SingleColorArtworkKeepsItsSilhouetteAndDoesNotMutateTheSource() {
        val source = intArrayOf(0, 0xFF335577.toInt(), 0x80335577.toInt())
        val saved = source.copyOf()
        for (invert in listOf(false, true)) {
            assertArrayEquals(intArrayOf(0, 255, 128), alpha(monochromeMaskPixels(source, invert)))
        }
        assertArrayEquals(saved, source)
    }

    @Test fun emptyAndTransparentImagesRemainEmpty() {
        assertArrayEquals(intArrayOf(), monochromeMaskPixels(intArrayOf()))
        assertArrayEquals(intArrayOf(0, 0), monochromeMaskPixels(intArrayOf(0, 0x00FFFFFF)))
    }
}
