package com.psycode.spotiflac.ui.component.managefiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageFilesMatchingUtilsTest {

    @Test
    fun `normalizeCore removes punctuation and diacritics`() {
        val normalized = normalizeCore("  Bjo\u0308rk — Joga (Live)  ")
        assertEquals("bjork joga live", normalized)
    }

    @Test
    fun `duplicateKeyOf ignores track numbers and bracketed fragments`() {
        val first = duplicateKeyOf("01. Intro (Remastered)")
        val second = duplicateKeyOf("Intro [2011]")
        assertEquals("intro", first)
        assertEquals(first, second)
    }

    @Test
    fun `damerauLevenshteinSimilarity is high for close strings`() {
        val similarity = damerauLevenshteinSimilarity("metallica one", "mettalica one")
        assertTrue(similarity > 0.75)
    }

    @Test
    fun `buildTitleCandidates returns normalized and compact variants`() {
        val candidates = buildTitleCandidates(
            normalizedBaseName = "metallica one",
            artistNormalized = "metallica"
        )
        assertTrue(candidates.contains("metallica one"))
        assertTrue(candidates.contains("metallicaone"))
        assertTrue(candidates.contains("one"))
    }
}
