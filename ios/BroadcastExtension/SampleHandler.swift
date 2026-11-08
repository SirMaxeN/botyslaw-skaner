import ReplayKit
import CoreMedia

/// Receives live screen frames while the player records through Botysław.
/// No video is stored: each frame is downscaled, analysed and dropped (memory limit of the extension ~50 MB).
final class SampleHandler: RPBroadcastSampleHandler {
    private let queue = DispatchQueue(label: "botyslaw.analysis")
    private var busy = false
    private var lastFrame = Date.distantPast
    private let interval: TimeInterval = 0.15

    override func broadcastStarted(withSetupInfo setupInfo: [String: NSObject]?) {
        Notifier.shared.prompt("🎬 Skan rozpoczęty. Otwórz grę na ekranie głównym.")
        // TODO (Etap 2): flow = GuidedRecordingFlow() from BotyslawShared
    }

    override func processSampleBuffer(_ sampleBuffer: CMSampleBuffer, with sampleBufferType: RPSampleBufferType) {
        guard sampleBufferType == .video, !busy, Date().timeIntervalSince(lastFrame) >= interval,
              let pixels = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lastFrame = Date(); busy = true
        queue.async { [weak self] in
            defer { self?.busy = false }
            // TODO (Etap 4): vImage downscale to 360 px width → ARGB IntArray → GuidedRecordingFlow.onFrame(...)
            //   where Android uses ML Kit, call VisionOcr.recognize(pixels) instead.
            //   Events: Prompt → Notifier.prompt, StepDone → save to App Group, Finished → finish().
            _ = pixels
        }
    }

    override func broadcastFinished() {
        // TODO: flush partial results to the App Group so the app can show them.
    }

    private func finish() {
        Notifier.shared.prompt("✅ Gotowe, wróć do Botysława")
        finishBroadcastWithError(NSError(domain: "Botyslaw", code: 0,
            userInfo: [NSLocalizedDescriptionKey: "Skan zakończony — wróć do Botysława."]))
    }
}
