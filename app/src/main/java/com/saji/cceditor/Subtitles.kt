package com.saji.cceditor

import java.util.Locale

data class CaptionCue(val text: String, val startMs: Long, val endMs: Long)

data class CaptionStyleState(
    val colorHex: String = "#FFFFFF",
    val background: CaptionBackground = CaptionBackground.PILL,
    val bold: Boolean = true,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val fontScale: Float = 1f
)

enum class CaptionBackground { PILL, OUTLINE, PLAIN }
enum class CanvasPreset(val title: String, val aspectRatio: Float?) {
    PORTRAIT("9:16", 9f / 16f), SQUARE("1:1", 1f),
    LANDSCAPE("16:9", 16f / 9f), ORIGINAL("Original", null)
}

private val srtTiming = Regex("(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})")

fun parseSrt(content: String): List<CaptionCue> {
    val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
    val cues = mutableListOf<CaptionCue>()
    normalized.split(Regex("\\n\\s*\\n")).forEach { block ->
        val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val timingLine = lines.firstOrNull { srtTiming.containsMatchIn(it) } ?: return@forEach
        val match = srtTiming.find(timingLine) ?: return@forEach
        val values = match.groupValues.drop(1).map { it.toLong() }
        fun toMs(h: Long, m: Long, s: Long, ms: Long) = ((h * 60 + m) * 60 + s) * 1000 + ms
        val start = toMs(values[0], values[1], values[2], values[3])
        val end = toMs(values[4], values[5], values[6], values[7])
        val caption = lines.drop(lines.indexOf(timingLine) + 1).joinToString("\n").trim()
        if (caption.isNotBlank() && end > start) cues += CaptionCue(caption, start, end)
    }
    return cues.sortedBy { it.startMs }
}

fun createSrt(cues: List<CaptionCue>): String = cues.sortedBy { it.startMs }.mapIndexed { index, cue ->
    "${index + 1}\n${formatSrtTimestamp(cue.startMs)} --> ${formatSrtTimestamp(cue.endMs)}\n${cue.text.trim()}"
}.joinToString("\n\n") + if (cues.isNotEmpty()) "\n" else ""

fun formatSrtTimestamp(timeMs: Long): String {
    val safe = timeMs.coerceAtLeast(0)
    val hours = safe / 3_600_000
    val minutes = (safe % 3_600_000) / 60_000
    val seconds = (safe % 60_000) / 1_000
    val millis = safe % 1_000
    return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
}

fun formatClock(timeMs: Long): String {
    val seconds = (timeMs.coerceAtLeast(0) / 1_000).toInt()
    return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
}
