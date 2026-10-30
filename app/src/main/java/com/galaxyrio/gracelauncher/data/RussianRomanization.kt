package com.galaxyrio.gracelauncher.data

private const val russianAlphabet = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
private val russianLatin = listOf(
    "a", "b", "v", "g", "d", "e", "yo", "zh", "z", "i", "y", "k", "l", "m", "n", "o",
    "p", "r", "s", "t", "u", "f", "kh", "ts", "ch", "sh", "shch", "", "y", "", "e", "yu", "ya",
)
private val cyrillicRuns = Regex("[\\p{IsCyrillic}\\p{M}]+")

/**
 * Readable Russian romanization, including on Android 9, before ICU loses distinctions such as
 * я/а and ч/ц. Latin text is untouched. Runs containing other Cyrillic alphabets fall back to ICU;
 * shared letters alone cannot identify a language unambiguously.
 */
internal fun romanizeRussianLabel(label: String): String = cyrillicRuns.replace(label) { match ->
    val indices = match.value.map { russianAlphabet.indexOf(it.lowercaseChar()) }
    if (indices.any { it < 0 }) match.value else indices.joinToString("") { russianLatin[it] }
}
