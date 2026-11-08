import Vision
import CoreVideo

/// Apple Vision replacement for ML Kit text recognition used on Android
/// (vision/OcrAnalyzer.kt, battle/BattleFieldDetector.kt, battle/CapturedTextReader.kt).
enum VisionOcr {
    struct Line { let text: String; let box: CGRect /* normalised, origin bottom-left */ }

    static func recognize(_ pixels: CVPixelBuffer, region: CGRect? = nil) -> [Line] {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.recognitionLanguages = ["pl-PL", "en-US"]
        request.usesLanguageCorrection = false
        if let region { request.regionOfInterest = region }
        try? VNImageRequestHandler(cvPixelBuffer: pixels).perform([request])
        return (request.results ?? []).compactMap { obs in
            obs.topCandidates(1).first.map { Line(text: $0.string, box: obs.boundingBox) }
        }
        // TODO (Etap 4): map to the shared OcrLine/OcrAnalysis model (top-left pixel coordinates).
    }
}
