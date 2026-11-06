package eu.komars.forgescanner.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import eu.komars.forgescanner.model.GameNumberParser
import eu.komars.forgescanner.model.ResourceField
import eu.komars.forgescanner.model.ScanStep
import eu.komars.forgescanner.model.ScanTarget
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

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

/** ML Kit's bundled Latin model runs on-device. Frames are never persisted or transmitted. */
class OcrAnalyzer : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val processing = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    fun analyze(bitmap: Bitmap, step: ScanStep, region: Rect? = null, callback: (OcrAnalysis) -> Unit) =
        analyzeInternal(bitmap, step, region, step == ScanStep.HAMMERS, callback = callback)

    /** All variants belong to one copied source frame; callers must not count them as fresh votes. */
    fun analyzeVariants(bitmap: Bitmap, step: ScanStep, region: Rect, callback: (OcrAnalysis) -> Unit) =
        analyzeInternal(bitmap, step, region, true, callback = callback)

    /** Only use after a complete source-frame reading. Conflicting passes expand to all variants. */
    fun analyzeVerification(bitmap: Bitmap, target: ScanTarget, region: Rect, callback: (OcrAnalysis) -> Unit) =
        analyzeInternal(bitmap, target.step, region, true, target, callback)

    private fun analyzeInternal(bitmap: Bitmap, step: ScanStep, region: Rect?, variants: Boolean,
        verificationTarget: ScanTarget? = null, callback: (OcrAnalysis) -> Unit) {
        if (closed.get()) { callback(OcrAnalysis(emptyList(), emptyList(), "Skaner jest zamknięty.")); return }
        if (!processing.compareAndSet(false, true)) { callback(OcrAnalysis(emptyList(), emptyList(), "Odczyt nadal trwa.")); return }
        val full = Rect(0, 0, bitmap.width, bitmap.height)
        val cropRect = region?.let(::Rect) ?: Rect(full)
        if (!cropRect.intersect(full) || cropRect.width() < 32 || cropRect.height() < 32) {
            processing.set(false)
            callback(OcrAnalysis(emptyList(), emptyList(), "Zaznacz większy obszar licznika."))
            return
        }
        val crop = try {
            val selected = Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
            // createBitmap may return its input for a full-size crop. Own the pixels for the async task.
            if (selected === bitmap) requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)) else selected
        }
        catch (_: Exception) {
            processing.set(false)
            callback(OcrAnalysis(emptyList(), emptyList(), "Nie można odczytać tego obrazu."))
            return
        }
        if (variants) {
            analyzeCounterVariants(crop, cropRect, step, verificationTarget, callback)
            return
        }
        recognizer.process(InputImage.fromBitmap(crop, 0))
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                    line.boundingBox?.let { bounds -> OcrLine(line.text, ScreenBounds(bounds.left + cropRect.left, bounds.top + cropRect.top, bounds.right + cropRect.left, bounds.bottom + cropRect.top)) }
                }
                processing.set(false)
                callback(OcrAnalysis(OcrCandidateExtractor.extract(lines, step), lines))
            }
            .addOnFailureListener { processing.set(false); callback(OcrAnalysis(emptyList(), emptyList(), "Nie udało się rozpoznać tekstu. Powiększ licznik albo wpisz wartość ręcznie.")) }
            .addOnCompleteListener {
                if (!crop.isRecycled) crop.recycle()
            }
    }

    /** Calibrated counters are separated by blank gutters and never share label matching.
     * One recognizer invocation reads every slot for a given variant of one source screenshot.
     */
    fun analyzeMany(bitmap: Bitmap, regions: Map<ScanTarget, Rect>, verification: Boolean = false,
        onPass: () -> Unit = {}, callback: (Map<ScanTarget, OcrAnalysis>) -> Unit) {
        fun failure(message: String) = regions.keys.associateWith {
            OcrAnalysis(emptyList(), emptyList(), message, true)
        }
        if (closed.get()) { callback(failure("Skaner jest zamknięty.")); return }
        if (!processing.compareAndSet(false, true)) { callback(failure("Odczyt nadal trwa.")); return }
        val layout: CounterStripLayout
        val strip: Bitmap
        var allocated: Bitmap? = null
        try {
            require(regions.size in 1..5 && regions.keys.none { it.isInventory })
            require(regions.values.all { it.left >= 0 && it.top >= 0 && it.right <= bitmap.width &&
                it.bottom <= bitmap.height && it.width() >= 32 && it.height() >= 32 })
            layout = CounterStripLayout(regions.mapValues { (_, rect) ->
                ScreenBounds(rect.left, rect.top, rect.right, rect.bottom)
            })
            strip = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
            allocated = strip
            val canvas = Canvas(strip)
            canvas.drawColor(Color.WHITE)
            for (slot in layout.slots) {
                val source = slot.source; val packed = slot.packed
                canvas.drawBitmap(bitmap, Rect(source.left, source.top, source.right, source.bottom),
                    Rect(packed.left, packed.top, packed.right, packed.bottom), null)
            }
        } catch (_: Exception) {
            allocated?.takeUnless { it.isRecycled }?.recycle()
            processing.set(false)
            callback(failure("Sprawdź osobne obszary liczników i zaznacz większe pola."))
            return
        }
        val variants = if (verification)
            listOf(CounterVariant.ORIGINAL, CounterVariant.WHITE_DIGITS, CounterVariant.CONTRAST, CounterVariant.INVERSE)
        else listOf(CounterVariant.ORIGINAL, CounterVariant.CONTRAST, CounterVariant.INVERSE, CounterVariant.WHITE_DIGITS)
        val readings = regions.keys.associateWith { mutableListOf<OcrAnalysis>() }
        var completed = false
        fun finish(error: String? = null) {
            if (completed) return
            completed = true
            if (!strip.isRecycled) strip.recycle()
            processing.set(false)
            callback(regions.keys.associateWith { target ->
                val passes = readings.getValue(target)
                val lines = passes.flatMap { it.lines }
                OcrAnalysis(passes.flatMap { it.candidates }, lines,
                    error = error ?: if (passes.all { it.error != null }) "Nie udało się odczytać licznika." else null,
                    requiresConfirmation = error != null)
            })
        }
        fun read(index: Int) {
            if (closed.get()) { finish("Skaner jest zamknięty."); return }
            val rendered = try { renderCounterVariant(strip, CounterPass(0, strip.height, variants[index])) }
            catch (_: Exception) { finish("Nie można przygotować obrazu licznika."); return }
            fun accept(lines: List<OcrLine>, error: String?) {
                if (rendered.first !== strip && !rendered.first.isRecycled) rendered.first.recycle()
                val mapped = lines.mapNotNull(layout::mapLine).groupBy({ it.first }, { it.second })
                for (target in regions.keys) {
                    val ownLines = mapped[target].orEmpty()
                    val candidates = OcrCandidateExtractor.extract(ownLines, target.step).map { it.copy(field = target.field) }
                    readings.getValue(target) += OcrAnalysis(candidates, ownLines, error)
                }
                val verified = verification && index == 1 && regions.keys.all {
                    CounterOcrPassPolicy.verificationAgrees(readings.getValue(it), it)
                }
                if (verified || index + 1 == variants.size) finish() else read(index + 1)
            }
            try {
                onPass()
                recognizer.process(InputImage.fromBitmap(rendered.first, 0)).addOnCompleteListener { task ->
                    val lines = if (task.isSuccessful) task.result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                        line.boundingBox?.let { bounds -> OcrLine(line.text, ScreenBounds(
                            (bounds.left / rendered.second).toInt(), (bounds.top / rendered.second).toInt(),
                            (bounds.right / rendered.second).toInt(), (bounds.bottom / rendered.second).toInt())) }
                    } else emptyList()
                    accept(lines, if (task.isSuccessful) null else "Nie udało się odczytać wariantu obrazu.")
                }
            } catch (_: Exception) { accept(emptyList(), "Nie udało się odczytać wariantu obrazu.") }
        }
        read(0)
    }

    private enum class CounterVariant { ORIGINAL, CONTRAST, INVERSE, WHITE_DIGITS }
    private data class CounterPass(val top: Int, val height: Int, val variant: CounterVariant)

    /** Keep the full contrast sweep on the first source frame. Later clean frames can stop after
     * the original crop and a different white-interior image independently agree. Images are
     * generated on demand so the successful short path does not build unused contrast bitmaps.
     */
    private fun analyzeCounterVariants(crop: Bitmap, region: Rect, step: ScanStep,
        verificationTarget: ScanTarget?, callback: (OcrAnalysis) -> Unit) {
        val original = CounterPass(0, crop.height, CounterVariant.ORIGINAL)
        val white = CounterPass(0, crop.height, CounterVariant.WHITE_DIGITS)
        val bandHeight = if (crop.height > 180) (crop.height * .68).toInt() else crop.height
        val fullPasses = listOf(original) + listOf(0, crop.height - bandHeight).distinct().flatMap { top ->
            listOf(CounterVariant.CONTRAST, CounterVariant.INVERSE, CounterVariant.WHITE_DIGITS)
                .map { CounterPass(top, bandHeight, it) }
        }
        val passes = if (verificationTarget == null) fullPasses else
            listOf(original, white) + fullPasses.filter { it != original && it != white }
        val lines = mutableListOf<OcrLine>()
        val passReadings = mutableListOf<OcrAnalysis>()
        var completed = false
        fun finish(error: String? = null) {
            if (completed) return
            completed = true
            if (!crop.isRecycled) crop.recycle()
            processing.set(false)
            callback(OcrAnalysis(OcrCandidateExtractor.extract(lines, step), lines,
                error = error ?: if (lines.isEmpty() && passReadings.any { it.error != null })
                    "Nie udało się odczytać licznika. Spróbuj ponownie." else null))
        }
        fun read(index: Int) {
            if (closed.get()) { finish("Skaner jest zamknięty."); return }
            val pass = passes[index]
            val rendered = try { renderCounterVariant(crop, pass) } catch (_: Exception) {
                finish("Nie można przygotować obrazu licznika."); return
            }
            fun accept(reading: OcrAnalysis) {
                if (rendered.first !== crop && !rendered.first.isRecycled) rendered.first.recycle()
                passReadings += reading
                lines += reading.lines
                val verified = verificationTarget != null && index == 1 &&
                    CounterOcrPassPolicy.verificationAgrees(passReadings, verificationTarget)
                if (verified || index + 1 == passes.size) finish() else read(index + 1)
            }
            try {
                recognizer.process(InputImage.fromBitmap(rendered.first, 0)).addOnCompleteListener { task ->
                    val detected = if (task.isSuccessful) task.result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                        line.boundingBox?.let { bounds -> OcrLine(line.text, ScreenBounds(
                            region.left + (bounds.left / rendered.second).toInt(),
                            region.top + pass.top + (bounds.top / rendered.second).toInt(),
                            region.left + (bounds.right / rendered.second).toInt(),
                            region.top + pass.top + (bounds.bottom / rendered.second).toInt())) }
                    } else emptyList()
                    accept(OcrAnalysis(OcrCandidateExtractor.extract(detected, step), detected,
                        error = if (task.isSuccessful) null else "Nie udało się odczytać wariantu obrazu."))
                }
            } catch (_: Exception) {
                accept(OcrAnalysis(emptyList(), emptyList(), "Nie udało się odczytać wariantu obrazu."))
            }
        }
        read(0)
    }

    private fun renderCounterVariant(crop: Bitmap, pass: CounterPass): Pair<Bitmap, Double> {
        if (pass.variant == CounterVariant.ORIGINAL) return crop to 1.0
        val band = Bitmap.createBitmap(crop, 0, pass.top, crop.width, pass.height)
        val factor = minOf(2.0, 1800.0 / max(band.width, band.height)).coerceAtLeast(.5)
        val scaled = Bitmap.createScaledBitmap(band, (band.width * factor).toInt(), (band.height * factor).toInt(), true)
        val colors = IntArray(scaled.width * scaled.height)
        scaled.getPixels(colors, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        for (index in colors.indices) {
            val color = colors[index]
            val gray = (android.graphics.Color.red(color) * 299 + android.graphics.Color.green(color) * 587 +
                android.graphics.Color.blue(color) * 114) / 1000
            val value = when (pass.variant) {
                CounterVariant.CONTRAST -> ((gray - 128) * 1.8 + 128).toInt().coerceIn(0, 255)
                CounterVariant.INVERSE -> 255 - gray
                CounterVariant.WHITE_DIGITS -> if (CounterDigitMask.isInk(
                    Color.red(color), Color.green(color), Color.blue(color))) 0 else 255
                else -> gray
            }
            colors[index] = android.graphics.Color.rgb(value, value, value)
        }
        val output = Bitmap.createBitmap(colors, scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        if (scaled !== crop && scaled !== band) scaled.recycle()
        if (band !== crop) band.recycle()
        return output to factor
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) recognizer.close()
    }
}

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

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace('ł', 'l')
}

/** A line spanning a separator is deliberately rejected instead of assigning its digits to two fields. */
internal class CounterStripLayout(regions: Map<ScanTarget, ScreenBounds>) {
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
