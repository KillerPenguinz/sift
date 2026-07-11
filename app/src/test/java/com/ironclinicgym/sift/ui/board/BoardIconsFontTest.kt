package com.ironclinicgym.sift.ui.board

import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Font
import java.io.File

/**
 * Guards the two icon-rendering failure modes that shipped to UAT during Phase 3.5 Round 2 and
 * needed three separate follow-up fixes to fully close:
 *
 *  1. A codepoint is declared in [CODEPOINTS] but the bundled subset font has no glyph for it, so
 *     it renders as a tofu box. The subset must be regenerated whenever an icon is added (see the
 *     icon-font-subset-workflow memory note).
 *  2. An icon *name* is passed to [MaterialSymbol] (or a `*_ICONS` list) but is absent from
 *     [CODEPOINTS], so [MaterialSymbol] silently falls back to the "label" tag glyph.
 *
 * Both are invisible to the compiler and only surfaced in manual UAT before; these tests make them
 * fail in CI instead. Pure JVM: [Font.createFont] reads the .ttf cmap directly, no display needed.
 */
class BoardIconsFontTest {

    private val moduleRoot: File = locateModuleRoot()
    private val sourceRoot = File(moduleRoot, "src/main/java")
    private val fontFile = File(moduleRoot, "src/main/res/font/material_symbols_rounded.ttf")

    @Test
    fun `bundled font has a glyph for every declared codepoint`() {
        assertTrue("Font not found at ${fontFile.absolutePath}", fontFile.exists())
        val font = Font.createFont(Font.TRUETYPE_FONT, fontFile)
        val missing = CODEPOINTS
            .filterValues { !font.canDisplay(it) }
            .map { (name, ch) -> "$name=0x%04x".format(ch.code) }
            .sorted()
        assertTrue(
            "These declared icons have no glyph in material_symbols_rounded.ttf and render as tofu. " +
                "Regenerate the font subset to include them. Missing: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `every literal icon name used in UI code is declared in the codepoint map`() {
        val referenced = collectLiteralIconNames(sourceRoot)
        val undeclared = (referenced - CODEPOINTS.keys).sorted()
        assertTrue(
            "These icon names are referenced but missing from CODEPOINTS, so MaterialSymbol falls " +
                "back to the tag glyph. Add them (with the correct codepoint) and regenerate the " +
                "font subset. Undeclared: $undeclared",
            undeclared.isEmpty(),
        )
    }

    /**
     * Scrapes icon-name string literals from Kotlin source: the first argument of every
     * `MaterialSymbol(...)` call (including conditional `if (x) "a" else "b"` forms), every
     * `icon = "..."` assignment, and the entries of any `*_ICONS` / `*_ICON_CHOICES` list. Dynamic
     * icon sources (a variable, a function call) are out of scope; only literals can be checked.
     */
    private fun collectLiteralIconNames(root: File): Set<String> {
        val names = mutableSetOf<String>()
        val quoted = Regex("\"([a-z0-9_]+)\"")
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val text = file.readText()

            // First argument of each MaterialSymbol( ... ) call.
            var idx = text.indexOf("MaterialSymbol(")
            while (idx >= 0) {
                val argStart = idx + "MaterialSymbol(".length
                val firstArg = firstArgumentOf(text, argStart)
                quoted.findAll(firstArg).forEach { names += it.groupValues[1] }
                idx = text.indexOf("MaterialSymbol(", argStart)
            }

            // icon = "name" / icon = if (...) "a" else "b" (up to the line end).
            Regex("""\bicon\s*=\s*([^\n]+)""").findAll(text).forEach { m ->
                quoted.findAll(m.groupValues[1]).forEach { names += it.groupValues[1] }
            }

            // Literal icon-choice lists, e.g. `val LABEL_ICONS = listOf("star", ...)`.
            Regex("""\b\w*ICON(?:_CHOICES|S)?\s*(?::[^=]+)?=\s*listOf\(([^)]*)\)""")
                .findAll(text)
                .forEach { m -> quoted.findAll(m.groupValues[1]).forEach { names += it.groupValues[1] } }
        }
        return names
    }

    /** Returns the substring of the first top-level argument starting at [from] (a call's `(` + 1). */
    private fun firstArgumentOf(text: String, from: Int): String {
        var depth = 0
        var i = from
        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> { if (depth == 0) return text.substring(from, i); depth-- }
                ',' -> if (depth == 0) return text.substring(from, i)
            }
            i++
        }
        return text.substring(from, minOf(i, text.length))
    }

    /** Unit tests run with the module dir as CWD, but tolerate the repo root too. */
    private fun locateModuleRoot(): File {
        val cwd = File(System.getProperty("user.dir"))
        return when {
            File(cwd, "src/main/res/font/material_symbols_rounded.ttf").exists() -> cwd
            File(cwd, "app/src/main/res/font/material_symbols_rounded.ttf").exists() -> File(cwd, "app")
            else -> cwd
        }
    }
}
