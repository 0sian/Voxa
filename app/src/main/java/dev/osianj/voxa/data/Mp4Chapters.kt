package dev.osianj.voxa.data

import java.io.File
import java.io.RandomAccessFile

/**
 * Reads the chapter list inside an .m4b / .m4a. Two formats are in the wild and both are handled:
 *  - the Nero "chpl" atom (ffmpeg, mp4v2 and most audiobook tools), and
 *  - a QuickTime chapter track: a hidden text track the audio track points to with tref/chap
 *    (Apple Books and Audible conversions).
 * Only the file's index (the moov atom) and the few chapter-title samples are read, so a
 * multi-gigabyte book is instant. Returns an empty list if there are no chapters.
 */
object Mp4Chapters {
    fun read(file: File): List<Chapter> = readChpl(file).ifEmpty { QuickTimeChapters.read(file) }

    private fun readChpl(file: File): List<Chapter> = runCatching {
        RandomAccessFile(file, "r").use { f ->
            val moov = find(f, 0, f.length(), "moov") ?: return@use emptyList()
            val udta = find(f, moov.first, moov.second, "udta") ?: return@use emptyList()
            val chpl = find(f, udta.first, udta.second, "chpl") ?: return@use emptyList()
            f.seek(chpl.first)
            f.skipBytes(1 + 3 + 4) // version, flags, reserved
            val count = f.readUnsignedByte()
            val out = ArrayList<Chapter>(count)
            repeat(count) {
                val start100ns = f.readLong()
                val len = f.readUnsignedByte()
                val bytes = ByteArray(len).also { f.readFully(it) }
                val title = String(bytes, Charsets.UTF_8).trim().ifEmpty { "Chapter ${out.size + 1}" }
                out += Chapter(start100ns / 10_000, title)
            }
            out.sortedBy { it.startMs }
        }
    }.getOrDefault(emptyList())

    /** Payload range of the first child atom called [name] between [from] and [to]. */
    internal fun find(f: RandomAccessFile, from: Long, to: Long, name: String): Pair<Long, Long>? {
        var pos = from
        while (pos + 8 <= to) {
            f.seek(pos)
            val size32 = f.readInt().toLong() and 0xffffffffL
            val type = ByteArray(4).also { f.readFully(it) }.toString(Charsets.ISO_8859_1)
            var header = 8L
            var size = size32
            if (size32 == 1L) { size = f.readLong(); header = 16 } else if (size32 == 0L) size = to - pos
            if (size < header) return null
            if (type == name) return (pos + header) to (pos + size).coerceAtMost(to)
            pos += size
        }
        return null
    }
}

/** Chapters stored as a QuickTime text track (see [Mp4Chapters]). */
internal object QuickTimeChapters {
    private const val MAX_MOOV = 128L * 1024 * 1024

    private class Box(val type: String, val start: Int, val end: Int) // payload range inside the moov bytes

    fun read(file: File): List<Chapter> = runCatching {
        RandomAccessFile(file, "r").use { f ->
            val moovRange = Mp4Chapters.find(f, 0, f.length(), "moov") ?: return@use emptyList()
            val size = moovRange.second - moovRange.first
            if (size <= 0 || size > MAX_MOOV) return@use emptyList()
            val m = ByteArray(size.toInt()).also { f.seek(moovRange.first); f.readFully(it) }

            val traks = children(m, 0, m.size).filter { it.type == "trak" }
            // the audio track names its chapter track: trak > tref > chap > [track id]
            val chapterTrackId = traks.firstNotNullOfOrNull { t ->
                val chap = path(m, t, "tref", "chap") ?: return@firstNotNullOfOrNull null
                if (chap.end - chap.start >= 4) int(m, chap.start) else null
            } ?: return@use emptyList()
            val text = traks.firstOrNull { trackId(m, it) == chapterTrackId } ?: return@use emptyList()

            val timescale = path(m, text, "mdia", "mdhd")?.let { mdhdTimescale(m, it) } ?: return@use emptyList()
            val stbl = path(m, text, "mdia", "minf", "stbl") ?: return@use emptyList()
            val stts = child(m, stbl, "stts") ?: return@use emptyList()
            val stsz = child(m, stbl, "stsz") ?: return@use emptyList()
            val stsc = child(m, stbl, "stsc") ?: return@use emptyList()
            val offsets = chunkOffsets(m, stbl) ?: return@use emptyList()

            val sizes = sampleSizes(m, stsz)
            val starts = sampleStartsMs(m, stts, timescale, sizes.size)
            val sampleOffsets = sampleOffsets(m, stsc, offsets, sizes)
            val out = ArrayList<Chapter>(sizes.size)
            for (i in sizes.indices) {
                if (i >= starts.size || i >= sampleOffsets.size) break
                f.seek(sampleOffsets[i])
                val len = f.readUnsignedShort()
                if (len == 0 || len > sizes[i]) continue
                val bytes = ByteArray(len).also { f.readFully(it) }
                out += Chapter(starts[i], decode(bytes).trim().ifEmpty { "Chapter ${i + 1}" })
            }
            out.sortedBy { it.startMs }
        }
    }.getOrDefault(emptyList())

    // ---- the box tree, in memory --------------------------------------------------

    private fun int(m: ByteArray, at: Int): Int =
        ((m[at].toInt() and 0xff) shl 24) or ((m[at + 1].toInt() and 0xff) shl 16) or ((m[at + 2].toInt() and 0xff) shl 8) or (m[at + 3].toInt() and 0xff)

    private fun long(m: ByteArray, at: Int): Long = (int(m, at).toLong() shl 32) or (int(m, at + 4).toLong() and 0xffffffffL)

    private fun children(m: ByteArray, from: Int, to: Int): List<Box> {
        val out = ArrayList<Box>()
        var pos = from
        while (pos + 8 <= to) {
            var size = int(m, pos).toLong() and 0xffffffffL
            val type = String(m, pos + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) { size = long(m, pos + 8); header = 16 } else if (size == 0L) size = (to - pos).toLong()
            if (size < header || pos + size > to) break
            out += Box(type, pos + header, (pos + size).toInt())
            pos += size.toInt()
        }
        return out
    }

    private fun child(m: ByteArray, parent: Box, type: String): Box? = children(m, parent.start, parent.end).firstOrNull { it.type == type }

    private fun path(m: ByteArray, from: Box, vararg types: String): Box? {
        var cur = from
        for (t in types) cur = child(m, cur, t) ?: return null
        return cur
    }

    private fun trackId(m: ByteArray, trak: Box): Int? {
        val tkhd = child(m, trak, "tkhd") ?: return null
        val version = m[tkhd.start].toInt() and 0xff
        return int(m, tkhd.start + if (version == 1) 20 else 12)
    }

    private fun mdhdTimescale(m: ByteArray, mdhd: Box): Long? {
        val version = m[mdhd.start].toInt() and 0xff
        val ts = int(m, mdhd.start + if (version == 1) 20 else 12).toLong() and 0xffffffffL
        return ts.takeIf { it > 0 }
    }

    // ---- sample tables -----------------------------------------------------------

    private fun sampleSizes(m: ByteArray, stsz: Box): IntArray {
        val fixed = int(m, stsz.start + 4)
        val count = int(m, stsz.start + 8)
        return IntArray(count) { if (fixed != 0) fixed else int(m, stsz.start + 12 + it * 4) }
    }

    /** Start time in ms of each sample: the running total of the stts durations. */
    private fun sampleStartsMs(m: ByteArray, stts: Box, timescale: Long, samples: Int): LongArray {
        val entries = int(m, stts.start + 4)
        val out = LongArray(samples)
        var t = 0L
        var i = 0
        for (e in 0 until entries) {
            val count = int(m, stts.start + 8 + e * 8)
            val delta = int(m, stts.start + 12 + e * 8).toLong() and 0xffffffffL
            repeat(count) { if (i < samples) { out[i++] = t * 1000 / timescale; t += delta } }
        }
        return out
    }

    private fun chunkOffsets(m: ByteArray, stbl: Box): LongArray? {
        child(m, stbl, "stco")?.let { b ->
            val n = int(m, b.start + 4)
            return LongArray(n) { int(m, b.start + 8 + it * 4).toLong() and 0xffffffffL }
        }
        child(m, stbl, "co64")?.let { b ->
            val n = int(m, b.start + 4)
            return LongArray(n) { long(m, b.start + 8 + it * 8) }
        }
        return null
    }

    /** File offset of every sample, from the chunk offsets, the samples-per-chunk table and the sample sizes. */
    private fun sampleOffsets(m: ByteArray, stsc: Box, chunks: LongArray, sizes: IntArray): LongArray {
        val entries = int(m, stsc.start + 4)
        val firstChunk = IntArray(entries) { int(m, stsc.start + 8 + it * 12) }
        val perChunk = IntArray(entries) { int(m, stsc.start + 12 + it * 12) }
        val out = LongArray(sizes.size)
        var sample = 0
        for (c in chunks.indices) {
            val chunkNo = c + 1
            var e = entries - 1
            while (e > 0 && firstChunk[e] > chunkNo) e--
            var offset = chunks[c]
            repeat(perChunk[e]) {
                if (sample < sizes.size) { out[sample] = offset; offset += sizes[sample]; sample++ }
            }
        }
        return out
    }

    /** A chapter title is UTF-8, or UTF-16 with a byte-order mark. */
    private fun decode(b: ByteArray): String = when {
        b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte() -> String(b, 2, b.size - 2, Charsets.UTF_16BE)
        b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() -> String(b, 2, b.size - 2, Charsets.UTF_16LE)
        else -> String(b, Charsets.UTF_8)
    }
}
