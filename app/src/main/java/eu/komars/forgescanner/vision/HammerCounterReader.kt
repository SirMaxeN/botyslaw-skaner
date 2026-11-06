package eu.komars.forgescanner.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import eu.komars.forgescanner.capture.CaptureConnection
import eu.komars.forgescanner.capture.FrameCallback
import eu.komars.forgescanner.model.ScanStep

/** Owns a bounded, user-requested burst; all source bitmaps are released after their crop is copied. */
class HammerCounterReader(
    private val ocr: OcrAnalyzer,
    private val freshFrame: (FrameCallback) -> Unit = { CaptureConnection.requestFreshFrame(it) },
    private val schedule: (() -> Unit) -> Unit = { Handler(Looper.getMainLooper()).postDelayed(it, 150) }
) {
    fun read(first: Bitmap, region: Rect, isActive: () -> Boolean, complete: (OcrAnalysis) -> Unit) {
        val width = first.width; val height = first.height
        val crop = Rect(region)
        val readings = mutableListOf<OcrAnalysis>()
        fun release(bitmap: Bitmap) { if (!bitmap.isRecycled) bitmap.recycle() }
        fun finish() { if (isActive()) complete(HammerOcrConsensus.combine(readings)) }
        fun next(bitmap: Bitmap) {
            if (!isActive()) { release(bitmap); return }
            if (bitmap.width != width || bitmap.height != height) {
                release(bitmap)
                complete(OcrAnalysis(emptyList(), emptyList(), "Rozmiar ekranu się zmienił. Ustaw obszar ponownie.", true))
                return
            }
            ocr.analyze(bitmap, ScanStep.HAMMERS, crop) { result ->
                if (isActive()) {
                    readings += result
                    if (readings.size == HammerOcrConsensus.FRAME_COUNT) finish()
                    else schedule {
                        if (isActive()) freshFrame { frame, error ->
                            if (frame != null) next(frame)
                            else {
                                readings += OcrAnalysis(emptyList(), emptyList(), error ?: "Brak nowej klatki.")
                                finish()
                            }
                        }
                    }
                }
            }
            // OcrAnalyzer synchronously owns a copied crop before returning.
            release(bitmap)
        }
        next(first)
    }
}
