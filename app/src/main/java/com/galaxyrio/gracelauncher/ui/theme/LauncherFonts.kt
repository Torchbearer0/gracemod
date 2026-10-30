package com.galaxyrio.gracelauncher.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.AppFont
import com.galaxyrio.gracelauncher.data.ClockFontStore
import com.galaxyrio.gracelauncher.data.ClockPresetFont
import com.galaxyrio.gracelauncher.data.FontLibrary
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.graphics.fonts.FontStyle as PlatformFontStyle

/**
 * Josefin Sans first, with bundled Noto fallbacks on Android 10+ and system CJK fonts.
 * Separate weight descriptors preserve real variable-font weights: wrapping a single
 * Android Typeface in Compose FontFamily would ignore subsequent TextStyle weights.
 */
val LauncherFontFamily: FontFamily = FontFamily(
    (100..700 step 100).map { weight -> LauncherFont(FontWeight(weight)) },
)

// The app and clock share the same bundled font families, not separate font buffers.
private val presetFamilies = mapOf(
    ClockPresetFont.Sacramento to FontFamily(Font(R.font.sacramento)),
    ClockPresetFont.Bokor to FontFamily(Font(R.font.bokor)),
    ClockPresetFont.Plaster to FontFamily(Font(R.font.plaster)),
    ClockPresetFont.Monoton to FontFamily(Font(R.font.monoton)),
    ClockPresetFont.LuckiestGuy to FontFamily(Font(R.font.luckiest_guy)),
)

internal fun ClockPresetFont.family(): FontFamily = presetFamilies.getValue(this)

val LocalFontLibrary = staticCompositionLocalOf { FontLibrary() }

@Composable
internal fun rememberAppFontFamily(id: String?, library: FontLibrary = LocalFontLibrary.current): FontFamily =
    remember(id, library.selected) {
        val ids = (listOfNotNull(id) + library.selected).distinct()
        when {
            ids.isEmpty() || ids.first() == AppFont.System.id -> FontFamily.Default
            ids == listOf(FontLibrary.Josefin) -> LauncherFontFamily
            else -> FontFamily((100..900 step 100).map { SelectedFont(ids, FontWeight(it)) })
        }
    }

/** Async per-weight faces keep imported variable fonts responsive without losing bold roles. */
private data class SelectedFont(val ids: List<String>, override val weight: FontWeight) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Async,
    typefaceLoader = SelectedTypefaceLoader,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
) {
    override val style: FontStyle = FontStyle.Normal
}

private object SelectedTypefaceLoader : AndroidFont.TypefaceLoader {
    private val faces = LruCache<SelectedFont, Typeface>(12)

    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? = null

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface = withContext(Dispatchers.IO) {
        val choice = font as SelectedFont
        val weight = choice.weight.weight
        synchronized(faces) {
            faces.get(choice) ?: run {
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    SelectedTypefaceApi29.build(context, choice.ids, weight)
                } else primaryTypeface(context, choice.ids, weight)
                faces.put(choice, result)
                result
            }
        }
    }

    /** Android 9 supports one custom primary face plus the system's glyph fallback. */
    private fun primaryTypeface(context: Context, ids: List<String>, weight: Int): Typeface {
        ids.forEach { id ->
            val face = runCatching {
                when (id) {
                    FontLibrary.Josefin -> launcherTypeface(context, weight.coerceAtMost(700))
                    AppFont.System.id -> Typeface.create(Typeface.DEFAULT, weight, false)
                    AppFont.NotoSans.id -> Typeface.Builder(context.assets, "fonts/noto_sans.ttf")
                        .setFontVariationSettings("'wght' $weight").setWeight(weight)
                        .setItalic(false).setFallback("sans-serif").build()
                    else -> {
                        val resource = when (id) {
                            AppFont.Sacramento.id -> R.font.sacramento
                            AppFont.Bokor.id -> R.font.bokor
                            AppFont.Plaster.id -> R.font.plaster
                            AppFont.Monoton.id -> R.font.monoton
                            AppFont.LuckiestGuy.id -> R.font.luckiest_guy
                            else -> null
                        }
                        if (resource != null) ResourcesCompat.getFont(context, resource)?.let { Typeface.create(it, weight, false) }
                        else ClockFontStore(context).file(id)?.let {
                            Typeface.Builder(it).setFontVariationSettings("'wght' $weight").setWeight(weight)
                                .setItalic(false).setFallback("sans-serif").build()
                        }
                    }
                }
            }.getOrNull()
            if (face != null) return face
        }
        return Typeface.create(Typeface.DEFAULT, weight, false)
    }
}

/** Keep API 29-only types and their caches out of the loader used on Android 9. */
@RequiresApi(Build.VERSION_CODES.Q)
private object SelectedTypefaceApi29 {
    private val buffers = object : LruCache<String, PlatformFont>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: PlatformFont) = value.buffer.capacity().coerceAtLeast(1)
    }

    // Access is serialized by SelectedTypefaceLoader's face-cache lock.
    fun build(context: Context, ids: List<String>, weight: Int): Typeface {
        val families = ids.takeWhile { it != AppFont.System.id }.mapNotNull { id -> runCatching {
            val base = buffers.get(id) ?: when (id) {
                FontLibrary.Josefin -> PlatformFont.Builder(context.assets, PRIMARY_FONT_ASSET).build()
                AppFont.NotoSans.id -> PlatformFont.Builder(context.assets, "fonts/noto_sans.ttf").build()
                AppFont.Sacramento.id -> PlatformFont.Builder(context.resources, R.font.sacramento).build()
                AppFont.Bokor.id -> PlatformFont.Builder(context.resources, R.font.bokor).build()
                AppFont.Plaster.id -> PlatformFont.Builder(context.resources, R.font.plaster).build()
                AppFont.Monoton.id -> PlatformFont.Builder(context.resources, R.font.monoton).build()
                AppFont.LuckiestGuy.id -> PlatformFont.Builder(context.resources, R.font.luckiest_guy).build()
                else -> ClockFontStore(context).file(id)?.let { PlatformFont.Builder(it).build() }
            }?.also { buffers.put(id, it) }
            base?.let {
                val safeWeight = if (id == FontLibrary.Josefin) weight.coerceAtMost(700) else weight
                // Font.Builder(Font) is API 31; the shared-buffer constructor works on 29.
                val font = PlatformFont.Builder(it.buffer).setTtcIndex(it.ttcIndex).setWeight(safeWeight)
                    .setSlant(PlatformFontStyle.FONT_SLANT_UPRIGHT)
                    .setFontVariationSettings("'wght' $safeWeight").build()
                PlatformFontFamily.Builder(font).build()
            }
        }.getOrNull() }
        if (families.isEmpty()) return Typeface.create(Typeface.DEFAULT, weight, false)
        val builder = Typeface.CustomFallbackBuilder(families.first())
        families.drop(1).take(Typeface.CustomFallbackBuilder.getMaxCustomFallbackCount() - 1).forEach(builder::addCustomFallback)
        return builder.setStyle(PlatformFontStyle(weight, PlatformFontStyle.FONT_SLANT_UPRIGHT))
            .setSystemFallback("sans-serif").build()
    }
}

private data class LauncherFont(
    override val weight: FontWeight,
) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = LauncherTypefaceLoader,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
) {
    override val style: FontStyle = FontStyle.Normal
}

private object LauncherTypefaceLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface =
        launcherTypeface(context, font.weight.weight)

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
        loadBlocking(context, font)
}

private const val PRIMARY_FONT_ASSET = "fonts/josefin_sans.ttf"

private val typefaces = mutableMapOf<Int, Typeface>()

/**
 * Returns a cached real weight, shared by every launcher text surface.
 *
 * API 28 has no public custom per-glyph fallback-chain builder. It keeps the same
 * Josefin Sans variable weight and the device's sans-serif glyph fallback, which is not
 * guaranteed to be our bundled Noto files on OEM devices. No hidden APIs are used.
 *
 * Chinese, Japanese and Korean use Android's system sans-serif fallback.
 * Keep the text locale supplied by Compose so Android selects regional glyphs.
 */
internal fun launcherTypeface(context: Context, weight: Int): Typeface {
    require(weight in 100..700) { "Josefin Sans supports weights between 100 and 700" }
    return synchronized(typefaces) {
        typefaces.getOrPut(weight) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                NotoFallbackApi29.build(context, weight)
            } else {
                checkNotNull(
                    Typeface.Builder(context.assets, PRIMARY_FONT_ASSET)
                        .setFontVariationSettings("'wght' $weight")
                        .setWeight(weight)
                        .setItalic(false)
                        .setFallback("sans-serif")
                        .build(),
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private object NotoFallbackApi29 {
    private val fallbackAssets = listOf(
        "fonts/noto_sans.ttf",
        "fonts/noto_sans_arabic.ttf",
        "fonts/noto_sans_hebrew.ttf",
        "fonts/noto_sans_devanagari.ttf",
        "fonts/noto_sans_thai.ttf",
    )

    // Retain each bundled font's native backing buffer once. All weight
    // instances reuse that buffer instead of re-reading files.
    // Access is serialized by the outer typeface cache lock.
    private val baseFonts = mutableMapOf<String, PlatformFont>()

    private fun family(context: Context, asset: String, weight: Int): PlatformFontFamily {
        val base = baseFonts.getOrPut(asset) {
            PlatformFont.Builder(context.assets, asset).build()
        }
        // The Font copy-builder needs API 31. The public buffer constructor also
        // shares font data and works on every API level supporting custom fallback.
        val font = PlatformFont.Builder(base.buffer)
            .setTtcIndex(base.ttcIndex)
            .setFontVariationSettings("'wght' $weight")
            .setWeight(weight)
            .setSlant(PlatformFontStyle.FONT_SLANT_UPRIGHT)
            .build()
        return PlatformFontFamily.Builder(font).build()
    }

    fun build(context: Context, weight: Int): Typeface {
        val builder = Typeface.CustomFallbackBuilder(family(context, PRIMARY_FONT_ASSET, weight))
        fallbackAssets.forEach { asset ->
            builder.addCustomFallback(family(context, asset, weight))
        }
        return builder
            .setStyle(PlatformFontStyle(weight, PlatformFontStyle.FONT_SLANT_UPRIGHT))
            // CJK, emoji and scripts beyond the packaged Noto families use the OS.
            .setSystemFallback("sans-serif")
            .build()
    }
}
