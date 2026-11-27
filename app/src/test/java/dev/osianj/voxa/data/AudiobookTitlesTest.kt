package dev.osianj.voxa.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AudiobookTitlesTest {
    @Test fun dropsALongSubtitle() = assertEquals(
        "How to Hold a Cockroach",
        AudiobookTitles.short("How to Hold a Cockroach: A Book for Those Who Are Free and Don’t Know It (Unabridged)"),
    )
    @Test fun keepsAShortSubtitle() = assertEquals("Star Wars: Thrawn", AudiobookTitles.short("Star Wars: Thrawn"))
    @Test fun keepsATwoWordSubtitle() = assertEquals("Dune: Part Two", AudiobookTitles.short("Dune: Part Two"))
    @Test fun stripsBracketedTags() = assertEquals("Project Hail Mary", AudiobookTitles.short("Project Hail Mary [B08GB58KD5] (Unabridged)"))
    @Test fun stripsTrailingFormatWord() = assertEquals("The Hobbit", AudiobookTitles.short("The Hobbit - Unabridged"))
    @Test fun neverReturnsBlank() = assertEquals("(Unabridged)", AudiobookTitles.short("(Unabridged)"))
    @Test fun leavesAPlainTitleAlone() = assertEquals("Atomic Habits", AudiobookTitles.short("Atomic Habits"))
    @Test fun fullKeepsTheSubtitle() = assertEquals(
        "How to Hold a Cockroach: A Book for Those Who Are Free and Don’t Know It",
        AudiobookTitles.full("How to Hold a Cockroach: A Book for Those Who Are Free and Don’t Know It (Unabridged)"),
    )
}
