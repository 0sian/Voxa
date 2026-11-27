package dev.osianj.voxa.data

import org.junit.Assert.assertEquals
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
}
