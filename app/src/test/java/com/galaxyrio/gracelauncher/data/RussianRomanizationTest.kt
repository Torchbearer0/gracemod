package com.galaxyrio.gracelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RussianRomanizationTest {
    @Test fun russianNamesUseReadableLatinLetters() {
        mapOf("Яндекс" to "yandeks", "Юла" to "yula", "Чат" to "chat", "Ёж" to "yozh",
            "Еда" to "eda", "Щука" to "shchuka", "Холод" to "kholod", "Центр" to "tsentr",
            "Йога" to "yoga").forEach { (name, expected) -> assertEquals(expected, romanizeRussianLabel(name)) }
    }

    @Test fun latinSpellingsAreNeverRewritten() {
        listOf("Yandex", "Yandeks", "ks", "x", "Česká", "München", "İstanbul").forEach {
            assertEquals(it, romanizeRussianLabel(it))
        }
    }

    @Test fun otherScriptsAndDistinctCyrillicAlphabetsFallBackUnchanged() {
        listOf("Україна", "Київ", "Српскиљ", "Ελλάδα", "微信", "カメラ", "카메라").forEach {
            assertEquals(it, romanizeRussianLabel(it))
        }
    }

    @Test fun mixedNamesPreserveOtherScriptsAndPunctuation() {
        assertEquals("yandeks Go 微信", romanizeRussianLabel("Яндекс Go 微信"))
        assertEquals("chat-2", romanizeRussianLabel("Чат-2"))
    }
}
