package com.galaxyrio.gracelauncher

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Runs on the host JVM; no emulator or Android resource runtime is needed. */
class PluralResourcesTest {
    @Test fun everyPluralResourceHasANonEmptyOtherFallback() {
        val resources = listOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull { it.isDirectory } ?: error("Cannot find app/src/main/res")
        val parser = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder()
        val missing = mutableListOf<String>()
        var checked = 0
        resources.walkTopDown().filter {
            it.isFile && it.extension == "xml" && it.parentFile?.name?.startsWith("values") == true
        }.forEach { file ->
            val plurals = parser.parse(file).getElementsByTagName("plurals")
            for (index in 0 until plurals.length) {
                val plural = plurals.item(index) as Element
                val items = plural.getElementsByTagName("item")
                // A translated plurals bag does not inherit a missing quantity
                // from English. Android throws if both the selected case and
                // this fallback are absent (e.g. Spanish zero uses "other").
                val hasOther = (0 until items.length).any { itemIndex ->
                    val item = items.item(itemIndex) as Element
                    item.getAttribute("quantity") == "other" && item.textContent.isNotBlank()
                }
                if (!hasOther) missing += "${file.relativeTo(resources).invariantSeparatorsPath}: ${plural.getAttribute("name")}"
                checked++
            }
        }
        assertTrue("No plural resources were checked", checked > 0)
        assertTrue("Missing non-empty quantity=other:\n${missing.joinToString("\n")}", missing.isEmpty())
    }
}
