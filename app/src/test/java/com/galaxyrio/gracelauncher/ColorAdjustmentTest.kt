package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.IconColor
import com.galaxyrio.gracelauncher.data.adjustHsb
import org.junit.Assert.*
import org.junit.Test

class ColorAdjustmentTest {
    @Test fun zeroOffsetsPreserveEveryBitIncludingAlpha() {
        listOf(0, 0x00808080, 0x8044aa77.toInt(), -1, 0xff123456.toInt()).forEach {
            assertEquals(it, adjustHsb(it, 0, 0, 0))
            assertEquals(it, IconColor.Theme.resolve(it))
            assertEquals(it, IconColor.FromApp.resolve(0, it))
        }
    }

    @Test fun hueWrapsInBothDirectionsAndKeepsAlpha() {
        val red = 0x80ff0000.toInt()
        assertEquals(0x8000ff00.toInt(), adjustHsb(red, 120, 0, 0))
        assertEquals(0x800000ff.toInt(), adjustHsb(red, -120, 0, 0))
        assertEquals(0x8000ffff.toInt(), adjustHsb(red, 180, 0, 0))
        assertEquals(adjustHsb(red, 180, 0, 0), adjustHsb(red, -180, 0, 0))
    }

    @Test fun saturationAndBrightnessAddPercentagePointsNotMultipliers() {
        val source = 0xff804040.toInt() // S = 50%, B = 128/255.
        assertEquals(0xff806060.toInt(), adjustHsb(source, 0, -25, 0))
        assertEquals(0xffb35a5a.toInt(), adjustHsb(source, 0, 0, 20))
        assertEquals(0xff802020.toInt(), adjustHsb(source, 0, 25, 0))
    }

    @Test fun saturationAndBrightnessClampAtTheirLimits() {
        val source = 0xff804040.toInt()
        assertEquals(0xff808080.toInt(), adjustHsb(source, 0, -100, 0))
        assertEquals(0xff000000.toInt(), adjustHsb(source, 0, 0, -100))
        assertEquals(0xffff0000.toInt(), adjustHsb(source, 0, 100, 100))
        assertEquals(0xffffffff.toInt(), adjustHsb(source, 0, -100, 100))
        assertEquals(0xff333333.toInt(), adjustHsb(0xff000000.toInt(), 0, 0, 20))
    }

    @Test fun sameSavedOffsetsAreAppliedToTheCurrentSourceWithoutAccumulation() {
        val red = 0xffff0000.toInt()
        val blue = 0xff0000ff.toInt()
        val adjusted = IconColor.Theme.copy(hueShift = 120)
        assertEquals(0xff00ff00.toInt(), adjusted.resolve(red))
        assertEquals(red, adjusted.resolve(blue))
        assertEquals(0xff00ff00.toInt(), adjusted.resolve(red))
        val fromApp = adjusted.copy(theme = false, fromApp = true)
        assertEquals(red, fromApp.resolve(0, blue))
        assertEquals(adjusted.resolve(red), fromApp.resolve(red))
    }

    @Test fun storedOffsetsNormalizeWhileLegacyAndFixedColorsKeepTheirBehavior() {
        val loaded = IconColor.fromStored(0, theme = true, hueShift = 999, saturationShift = -999, brightnessShift = 25)
        assertEquals(IconColor.Theme.copy(hueShift = 180, saturationShift = -100, brightnessShift = 25), loaded)
        assertEquals(IconColor.Theme, IconColor.fromStored(-1, dynamic = true))
        val fixed = IconColor(0xff123456.toInt(), hueShift = 90, brightnessShift = 30)
        assertEquals(fixed.argb, fixed.resolve(-1))
        assertEquals(IconColor(fixed.argb), fixed.normalized())
    }
}
