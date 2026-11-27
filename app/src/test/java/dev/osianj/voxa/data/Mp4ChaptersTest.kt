package dev.osianj.voxa.data

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Mp4ChaptersTest {
    private fun resource(name: String) = File(javaClass.classLoader!!.getResource(name)!!.toURI())

    @Test fun readsEmbeddedChaptersFromAnM4b() {
        val chapters = Mp4Chapters.read(resource("chapters.m4b"))
        assertEquals(listOf("Opening", "The Middle: with “unicode” – dash", "Ending"), chapters.map { it.title })
        assertEquals(listOf(0L, 2000L, 5000L), chapters.map { it.startMs })
    }

    @Test fun aFileWithoutChaptersGivesNothing() {
        val plain = File.createTempFile("plain", ".m4b").apply { writeBytes(ByteArray(64)); deleteOnExit() }
        assertTrue(Mp4Chapters.read(plain).isEmpty())
    }

    @Test fun aMissingFileGivesNothing() {
        assertTrue(Mp4Chapters.read(File("/definitely/not/here.m4b")).isEmpty())
    }

    /** An Apple/Audible-style file: chapters are a text track the audio track points to, not a chpl atom. */
    @Test fun readsQuickTimeChapterTrack() {
        val chapters = Mp4Chapters.read(resource("qt-chapters.m4b"))
        assertEquals(listOf("Opening", "The Middle: with “unicode” – dash", "Ending"), chapters.map { it.title })
        assertEquals(listOf(0L, 2000L, 5000L), chapters.map { it.startMs })
    }

    /** Point VOXA_TEST_M4B at any real audiobook to check it by hand; skipped otherwise. */
    @Test fun readsARealBookIfOneIsProvided() {
        val path = System.getenv("VOXA_TEST_M4B")
        assumeTrue(path != null && File(path).exists())
        val chapters = Mp4Chapters.read(File(path!!))
        println("REAL BOOK: ${chapters.size} chapters")
        chapters.forEach { println("  ${it.startMs} ${it.title}") }
        assertTrue(chapters.isNotEmpty())
    }
}
