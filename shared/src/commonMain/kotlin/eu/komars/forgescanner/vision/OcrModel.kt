package eu.komars.forgescanner.vision

import eu.komars.forgescanner.model.GameNumberParser
import eu.komars.forgescanner.model.ResourceField
import eu.komars.forgescanner.model.ScanStep
import eu.komars.forgescanner.model.ScanTarget
import kotlin.math.abs
import kotlin.math.max
import eu.komars.forgescanner.platform.normalizeNfd

data class ScreenBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

data class OcrLine(val text: String, val bounds: ScreenBounds)

data class OcrCandidate(
    val field: ResourceField?,
    val value: Long,
    val sourceText: String,
    val bounds: ScreenBounds,
    val confidence: Float,
    val approximate: Boolean,
    val reason: String
)

data class OcrAnalysis(
    val candidates: List<OcrCandidate>,
    val lines: List<OcrLine>,
    val error: String? = null,
    val requiresConfirmation: Boolean = false
)


/** Scores nearby labels; icon-only counters intentionally remain unassigned for manual selection. */
object OcrCandidateExtractor {
    private val labels = mapOf(
        ResourceField.MOUNT_KEYS to listOf("klucz", "keys", "mount key"),
        ResourceField.MOUNT_COST to listOf("koszt", "cost", "przyzw", "summon"),
        ResourceField.TICKETS to listOf("bilet", "tickets", "skill ticket"),
        ResourceField.SKILL_COST to listOf("koszt", "cost", "przyzw", "summon", "x5", "5x"),
        ResourceField.HAMMERS to listOf("mlot", "hammer"),
        ResourceField.MOUNTS to listOf("wierzchow", "mount"),
        ResourceField.PETS to listOf("zwierzak", "pet", "jaj", "egg")
    )

    fun extract(lines: List<OcrLine>, step: ScanStep): List<OcrCandidate> {
        val result = mutableListOf<OcrCandidate>()
        for (line in lines) {
            for (match in GameNumberParser.tokenPattern.findAll(line.text)) {
                // Do not turn pack sizes and multiplication labels into resource values.
                val before = line.text.getOrNull(match.range.first - 1)
                val after = line.text.getOrNull(match.range.last + 1)
                val timeComponent = (before == ':' && line.text.getOrNull(match.range.first - 2)?.isDigit() == true) ||
                    (after == ':' && line.text.getOrNull(match.range.last + 2)?.isDigit() == true)
                if (before == 'x' || before == 'X' || after == 'x' || after == 'X' || timeComponent) continue
                val parsed = GameNumberParser.parse(match.value) ?: continue
                val value = parsed.wholeValueOrNull() ?: continue
                val fraction = match.range.first.toFloat() / max(line.text.length, 1)
                val endFraction = (match.range.last + 1).toFloat() / max(line.text.length, 1)
                val bounds = line.bounds.copy(left = line.bounds.left + (line.bounds.width * fraction).toInt(), right = line.bounds.left + (line.bounds.width * endFraction).toInt())
                val ranked = step.fields.map { field -> field to labelScore(field, line, lines) }.sortedByDescending { it.second }
                val best = ranked.firstOrNull()
                val tied = ranked.getOrNull(1)?.second?.let { second -> best != null && abs(best.second - second) < 0.05f } ?: false
                val selected = best?.takeIf { it.second >= 0.6f && !tied }?.first
                if (selected?.isCost == true && value == 0L) continue
                val confidence = if (selected == null) 0.35f else best!!.second.coerceAtMost(0.88f)
                result += OcrCandidate(selected, value, match.value.trim(), bounds, confidence, parsed.abbreviated,
                    if (selected == null) "Wybierz pole ręcznie; licznik może być oznaczony tylko ikoną."
                    else "Dopasowano pobliski opis; sprawdź odczyt przed zatwierdzeniem.")
            }
        }
        return result.distinctBy { listOf(it.value, it.bounds.left, it.bounds.top, it.field) }.sortedByDescending { it.confidence }
    }

    private fun labelScore(field: ResourceField, line: OcrLine, lines: List<OcrLine>): Float {
        val words = labels[field] ?: return 0f
        val sameLine = normalize(line.text)
        if (words.any(sameLine::contains)) return 0.86f
        return lines.filter { it !== line }.mapNotNull { label ->
            if (words.none(normalize(label.text)::contains)) return@mapNotNull null
            val dy = abs(label.bounds.centerY - line.bounds.centerY)
            val dx = abs(label.bounds.centerX - line.bounds.centerX)
            val scale = max(line.bounds.height, label.bounds.height).coerceAtLeast(1)
            if (dy <= scale * 2 && dx <= scale * 14) 0.66f else null
        }.maxOrNull() ?: 0f
    }

    private fun normalize(value: String): String = normalizeNfd(value.lowercase())
        .replace(Regex("\\p{M}"), "").replace('ł', 'l')
}

/** A line spanning a separator is deliberately rejected instead of assigning its digits to two fields. */
class CounterStripLayout(regions: Map<ScanTarget, ScreenBounds>) {
    data class Slot(val target: ScanTarget, val source: ScreenBounds, val packed: ScreenBounds)
    val slots: List<Slot>
    val width: Int
    val height: Int
    init {
        require(regions.isNotEmpty() && regions.values.all { it.width >= 32 && it.height >= 32 })
        width = regions.values.maxOf { it.width } + PADDING * 2
        var y = PADDING
        slots = regions.map { (target, source) ->
            Slot(target, source, ScreenBounds(PADDING, y, PADDING + source.width, y + source.height)).also {
                y += source.height + PADDING
            }
        }
        height = y
    }
    fun mapLine(line: OcrLine): Pair<ScanTarget, OcrLine>? {
        val bounds = line.bounds
        val slot = slots.singleOrNull { (_, _, packed) -> bounds.left >= packed.left && bounds.top >= packed.top &&
            bounds.right <= packed.right && bounds.bottom <= packed.bottom && bounds.width > 0 && bounds.height > 0 } ?: return null
        val dx = slot.source.left - slot.packed.left; val dy = slot.source.top - slot.packed.top
        return slot.target to OcrLine(line.text, ScreenBounds(bounds.left + dx, bounds.top + dy, bounds.right + dx, bounds.bottom + dy))
    }
    companion object { private const val PADDING = 32 }
}
