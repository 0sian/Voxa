package dev.osianj.voxa.data

import java.io.File
import java.io.RandomAccessFile

/**
 * Reads the chapter list inside an .m4b / .m4a (the Nero "chpl" atom that ffmpeg, mp4v2 and most audiobook
 * tools write). Only the atom headers on the way to it are read, so a multi-gigabyte book is instant.
 * Returns an empty list if the file has no chpl chapters (QuickTime chapter tracks aren't read yet).
 */
object Mp4Chapters {
    fun read(file: File): List<Chapter> = runCatching {
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
    private fun find(f: RandomAccessFile, from: Long, to: Long, name: String): Pair<Long, Long>? {
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
