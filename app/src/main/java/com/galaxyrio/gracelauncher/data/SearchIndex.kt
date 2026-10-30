package com.galaxyrio.gracelauncher.data

import java.util.Locale

/** Shared local matching for app names and contact names. No remote search/index service. */
internal class SearchQuery(val text: String, val fuzzy: Boolean) {
    val literal = text.trim().lowercase(Locale.ROOT)
    val compact = compactSearchText(text)
    val phone = text.filter(Char::isDigit)
    val isPhone = phone.isNotEmpty() && text.all { it.isDigit() || it.isWhitespace() || it in "+-()." }
}

internal class SearchName(val label: String) {
    private val literal = label.trim().lowercase(Locale.ROOT)
    private val compact = compactSearchText(label)
    private val initials by lazy {
        buildString {
            var wordStart = true
            var previousLowercase = false
            label.codePoints().forEach { code ->
                val han = Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN
                val letter = Character.isLetterOrDigit(code)
                if (letter && (han || wordStart || previousLowercase && Character.isUpperCase(code))) {
                    compactSearchText(String(Character.toChars(code))).firstOrNull()?.let(::append)
                }
                wordStart = !letter || han
                previousLowercase = Character.isLowerCase(code)
            }
        }
    }

    /** Literal and transliterated names always match; fuzzy adds initials and subsequences. */
    fun score(query: SearchQuery): Int? {
        if (query.literal.isEmpty()) return null
        val literalScore = when {
            literal == query.literal -> 0
            literal.startsWith(query.literal) -> 2
            literal.contains(query.literal) -> 8
            else -> null
        }
        val term = query.compact
        if (term.isEmpty()) return literalScore
        val transliteratedScore = when {
            compact == term -> 1
            compact.startsWith(term) -> 3
            compact.contains(term) -> 9
            else -> null
        }
        listOfNotNull(literalScore, transliteratedScore).minOrNull()?.let { return it }
        if (!query.fuzzy) return null
        return when {
            initials.startsWith(term) -> 12
            initials.contains(term) -> 16
            else -> {
                // Ordered subsequences also support short Latin queries such as 'gml' for Gmail.
                var position = 0
                var first = -1
                for (character in term) {
                    val found = compact.indexOf(character, position)
                    if (found < 0) return null
                    if (first < 0) first = found
                    position = found + 1
                }
                40 + position - first - term.length
            }
        }
    }
}

private fun compactSearchText(value: String): String = appSortKey(value).filter(Char::isLetterOrDigit)

/** Index aliases once, before typing. No phonetic guesses or Latin spelling rewrites. */
internal class SearchNames(labels: Iterable<String>) {
    private val names = labels.map(String::trim).filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }.map(::SearchName)

    constructor(vararg labels: String?) : this(labels.filterNotNull())

    fun score(query: SearchQuery): Int? = names.mapNotNull { it.score(query) }.minOrNull()
    fun matches(query: SearchQuery): Boolean = query.literal.isEmpty() || score(query) != null
}

internal fun LauncherApp.searchNames(): SearchNames = SearchNames(label, originalLabel, englishLabel)

/** Selectors retain package-name lookup without making it a searchable display-name alias. */
internal class AppSearchIndex(app: LauncherApp) {
    private val names = app.searchNames()
    private val packageName = app.packageName
    fun matches(query: SearchQuery): Boolean =
        names.matches(query) || packageName.contains(query.literal, ignoreCase = true)
}
