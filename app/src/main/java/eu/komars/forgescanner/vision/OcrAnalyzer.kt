package eu.komars.forgescanner.vision

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import eu.komars.forgescanner.model.GameNumberParser
import eu.komars.forgescanner.model.ResourceField
import eu.komars.forgescanner.model.ScanStep
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
    val error: String? = null
)

/** ML Kit's bundled Latin model runs on-device. Frames are never persisted or transmitted. */
class OcrAnalyzer : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val processing = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    fun analyze(bitmap: Bitmap, step: ScanStep, region: Rect? = null, callback: (OcrAnalysis) -> Unit) {
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
        if (step == ScanStep.HAMMERS) {
            analyzeHammers(crop, cropRect, callback)
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

    /** White outlined game counters often disappear in a large mixed-content OCR block.
     * Read overlapping, magnified inverse-luminance bands as well as the original pixels.
     */
    private fun analyzeHammers(crop: Bitmap, region: Rect, callback: (OcrAnalysis) -> Unit) {
        data class Pass(val bitmap: Bitmap, val top: Int, val scale: Double)
        val passes = mutableListOf(Pass(crop, 0, 1.0))
        val bandHeight = if (crop.height > 180) (crop.height * .68).toInt() else crop.height
        val starts = listOf(0, crop.height-bandHeight).distinct()
        for (top in starts) {
            val band = Bitmap.createBitmap(crop,0,top,crop.width,bandHeight)
            val factor = minOf(2.0, 1800.0/max(band.width,band.height)).coerceAtLeast(.5)
            val scaled = Bitmap.createScaledBitmap(band,(band.width*factor).toInt(),(band.height*factor).toInt(),true)
            val pixels = IntArray(scaled.width*scaled.height)
            scaled.getPixels(pixels,0,scaled.width,0,0,scaled.width,scaled.height)
            for (i in pixels.indices) {
                val value=pixels[i]
                val gray=255-((android.graphics.Color.red(value)*299+android.graphics.Color.green(value)*587+android.graphics.Color.blue(value)*114)/1000)
                pixels[i]=android.graphics.Color.rgb(gray,gray,gray)
            }
            val inverse=Bitmap.createBitmap(pixels,scaled.width,scaled.height,Bitmap.Config.ARGB_8888)
            if (scaled !== crop && scaled !== band) scaled.recycle()
            if (band !== crop) band.recycle()
            passes += Pass(inverse,top,factor)
        }
        val lines=mutableListOf<OcrLine>()
        fun read(index: Int) {
            val pass=passes[index]
            recognizer.process(InputImage.fromBitmap(pass.bitmap,0)).addOnCompleteListener { task ->
                if (task.isSuccessful) task.result.textBlocks.flatMap { it.lines }.forEach { line ->
                    line.boundingBox?.let { bounds -> lines += OcrLine(line.text,ScreenBounds(
                        region.left+(bounds.left/pass.scale).toInt(),region.top+pass.top+(bounds.top/pass.scale).toInt(),
                        region.left+(bounds.right/pass.scale).toInt(),region.top+pass.top+(bounds.bottom/pass.scale).toInt())) }
                }
                if (!pass.bitmap.isRecycled) pass.bitmap.recycle()
                if (index+1<passes.size) read(index+1) else {
                    processing.set(false)
                    callback(OcrAnalysis(OcrCandidateExtractor.extract(lines,ScanStep.HAMMERS),lines))
                }
            }
        }
        read(0)
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
