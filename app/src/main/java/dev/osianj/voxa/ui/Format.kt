package dev.osianj.voxa.ui

import androidx.core.text.HtmlCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(sec: Int): String = when {
    sec <= 0 -> ""
    sec >= 3600 -> "${sec / 3600}h ${(sec % 3600) / 60}m"
    sec >= 60 -> "${sec / 60}m"
    else -> "${sec}s"
}

fun formatDate(ts: Long): String =
    if (ts <= 0) "" else SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ts))

/** "12 Apr 2024 • 1h 7m" */
fun episodeMeta(publishedAt: Long, durationSec: Int): String =
    listOf(formatDate(publishedAt), formatDuration(durationSec)).filter { it.isNotEmpty() }.joinToString(" • ")

/** "today", "2d ago", "3w ago"... */
fun ago(ts: Long): String {
    if (ts <= 0) return "no episodes"
    val d = (System.currentTimeMillis() - ts) / 86_400_000
    return when {
        d < 1 -> "today"
        d < 14 -> "${d}d ago"
        d < 60 -> "${d / 7}w ago"
        d < 365 -> "${d / 30}mo ago"
        else -> "${d / 365}y ago"
    }
}

/** 28:17 or 1:02:03 */
fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Feed descriptions are often HTML. */
fun stripHtml(html: String): String =
    HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%d MB".format(bytes / 1_000_000L)
    else -> "%d KB".format(bytes / 1_000L)
}
