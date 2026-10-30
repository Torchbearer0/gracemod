package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.icons.IconDominantColor
import org.junit.Assert.*
import org.junit.Test

class IconDominantColorTest {
    @Test fun neutralBackgroundDoesNotOverruleTheColoredSymbol() {
        val white = IntArray(500) { 0xFFFFFFFF.toInt() }
        val blue = IntArray(60) { 0xFF2266CC.toInt() }
        assertEquals(0xFF2266CC.toInt(), IconDominantColor.fromPixels(white + blue))
    }

    @Test fun transparentAndBarelyVisiblePixelsDoNotWin() {
        val transparentRed = IntArray(500) { 0x7FFF0000 }
        assertEquals(0xFF00AA44.toInt(), IconDominantColor.fromPixels(transparentRed + 0x8000AA44.toInt()))
    }

    @Test fun mostFrequentColoredBucketWinsInsteadOfAveragingColors() {
        val red = IntArray(80) { 0xFFFF0000.toInt() }
        val blue = IntArray(40) { 0xFF0000FF.toInt() }
        assertEquals(0xFFFF0000.toInt(), IconDominantColor.fromPixels(red + blue))
    }

    @Test fun nearbyColorsShareTheSameFourBitBucket() {
        val closeBlues = intArrayOf(0xFF2266CC.toInt(), 0xFF2F6FCF.toInt(), 0xFF0000FF.toInt())
        assertEquals(0xFF2266CC.toInt(), IconDominantColor.fromPixels(closeBlues))
    }

    @Test fun EmptyNeutralAndExtremeLightnessImagesUseTheThemeFallback() {
        assertNull(IconDominantColor.fromPixels(intArrayOf()))
        assertNull(IconDominantColor.fromPixels(intArrayOf(0, 0xFF000000.toInt(), 0xFF888888.toInt(), -1)))
        assertNull(IconDominantColor.fromPixels(intArrayOf(0xFF110000.toInt(), 0xFFFFEEEE.toInt())))
    }
}
