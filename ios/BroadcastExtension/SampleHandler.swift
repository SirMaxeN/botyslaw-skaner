import BotyslawShared
import CoreImage
import ImageIO
import ReplayKit
import os

final class SampleHandler: RPBroadcastSampleHandler {
    private let queue = DispatchQueue(label: "botyslaw.analysis")
    private let frameSlot = DispatchSemaphore(value: 1)
    private let imageContext = CIContext(options: [.cacheIntermediates: false])
    private let logger = Logger(subsystem: "com.sirmaxen.BotyslawSkaner", category: "broadcast")
    private var flow = GuidedRecordingFlow()
    private var capture = ResourceCapture(instruction: "")
    private var nextFrame: TimeInterval = 0
    private var nextOcr: TimeInterval = 0
    private var mode = RecordingMode.resources
    private var battleReader = RecordingBattleReader()
    private var battleResult: BattleCapture?

    override func broadcastStarted(withSetupInfo setupInfo: [String: NSObject]?) {
        queue.sync {
            mode = RecordingStore.mode()
            nextFrame = 0
            nextOcr = 0
            battleReader = RecordingBattleReader()
            battleResult = nil
            if mode != .resources {
                Notifier.shared.prompt("Otwórz profil wybranej postaci. Następnie powoli przewiń bonusy.")
                return
            }
            flow = GuidedRecordingFlow()
            capture = ResourceCapture(instruction: flow.instruction())
            nextFrame = 0
            persist()
            Notifier.shared.prompt(capture.instruction)
        }
    }

    override func processSampleBuffer(_ sampleBuffer: CMSampleBuffer, with sampleBufferType: RPSampleBufferType) {
        guard sampleBufferType == .video, let pixels = CMSampleBufferGetImageBuffer(sampleBuffer),
              frameSlot.wait(timeout: .now()) == .success else { return }
        let orientation = (CMGetAttachment(sampleBuffer, key: RPVideoSampleOrientationKey as CFString, attachmentModeOut: nil) as? NSNumber)?.int32Value ?? 1
        queue.async { [self] in
            defer { frameSlot.signal() }
            let now = ProcessInfo.processInfo.systemUptime
            guard now >= nextFrame else { return }
            nextFrame = now + (mode == .resources ? 0.18 : 0.7)
            autoreleasepool { analyze(pixels, orientation: orientation, now: now) }
        }
    }

    override func broadcastFinished() {
        queue.sync {
            guard mode == .resources else { return }
            flow.finishInventory()
            apply(flow.takeInventoryResults())
            capture.active = false
            persist()
        }
    }

    private func analyze(_ pixels: CVPixelBuffer, orientation: Int32, now: TimeInterval) {
        let image = CIImage(cvPixelBuffer: pixels).oriented(forExifOrientation: orientation)
        let extent = image.extent
        guard extent.width > 0, extent.height >= extent.width else { return }
        if mode != .resources {
            analyzeBattle(image)
            return
        }
        let scale = 360 / extent.width
        let small = image.transformed(by: CGAffineTransform(translationX: -extent.minX, y: -extent.minY))
            .transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        guard let bitmap = imageContext.createCGImage(small, from: small.extent.integral) else { return }
        let width = bitmap.width
        let height = bitmap.height
        let argb = KotlinIntArray(size: Int32(width * height))
        var bytes = [UInt8](repeating: 0, count: width * height * 4)
        let rendered = bytes.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8,
                                          bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue) else { return false }
            context.draw(bitmap, in: CGRect(x: 0, y: 0, width: CGFloat(width), height: CGFloat(height)))
            return true
        }
        guard rendered else { return }
        for index in 0..<(width * height) {
            let offset = index * 4
            let rgb = Int32(bytes[offset]) << 16 | Int32(bytes[offset + 1]) << 8 | Int32(bytes[offset + 2])
            argb.set(index: Int32(index), value: rgb | Int32(bitPattern: 0xff000000))
        }
        let proposals = flow.regions(width: Int32(width), height: Int32(height), pixels: argb, nowMillis: Int64(now * 1000))
        let inventories = flow.takeInventoryResults()
        guard now >= nextOcr, !proposals.isEmpty else { apply(inventories); return }
        nextOcr = now + 0.5
        var readings: [String: String] = [:]
        if !proposals.isEmpty {
            let regions = Dictionary(uniqueKeysWithValues: proposals.map { counter in
                let box = counter.region
                return (counter.key, CGRect(x: CGFloat(box.left) / CGFloat(width),
                                            y: 1 - CGFloat(box.bottom) / CGFloat(height),
                                            width: CGFloat(box.right - box.left) / CGFloat(width),
                                            height: CGFloat(box.bottom - box.top) / CGFloat(height)))
            }.filter { $0.1.width > 0 && $0.1.height > 0 && CGRect(x: 0, y: 0, width: 1, height: 1).contains($0.1) })
            let ocrScale = min(1, 960 / extent.width)
            let sharp = image.transformed(by: CGAffineTransform(scaleX: ocrScale, y: ocrScale))
            if let source = imageContext.createCGImage(sharp, from: sharp.extent.integral) {
                readings = (try? VisionOcr.recognize(source, regions: regions)) ?? [:]
            }
        }
        apply(inventories + flow.observe(textByCounter: readings))
    }

    private func analyzeBattle(_ image: CIImage) {
        let scale = min(1, 960 / image.extent.width)
        let scaled = image.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        guard let bitmap = imageContext.createCGImage(scaled, from: scaled.extent.integral),
              let text = try? VisionOcr.recognize(bitmap, regions: ["profile": CGRect(x: 0, y: 0, width: 1, height: 1)])["profile"],
              let stats = battleReader.observe(text: text) else { return }
        let bonuses = stats.bonuses.mapValues { $0.doubleValue }
        let caps = stats.caps.mapValues { $0.doubleValue }
        guard battleResult?.damage != stats.damage || battleResult?.health != stats.health ||
              battleResult?.bonuses != bonuses || battleResult?.caps != caps else { return }
        let next = BattleCapture(damage: stats.damage, health: stats.health, bonuses: bonuses, caps: caps, updatedAt: Date())
        do {
            try RecordingStore.saveBattle(next, mode: mode)
            if battleResult == nil { Notifier.shared.prompt("Obrażenia i zdrowie odczytane. Przewiń bonusy do końca i zatrzymaj transmisję.") }
            battleResult = next
        } catch {
            logger.error("Nie udało się zapisać odczytu profilu w App Group.")
        }
    }

    private func apply(_ confirmed: [RecordingValue]) {
        let instruction = flow.instruction()
        guard !confirmed.isEmpty || capture.instruction != instruction else { return }
        for reading in confirmed {
            capture.values[reading.key] = reading.value
            if reading.abbreviated { capture.abbreviated.insert(reading.key) }
        }
        let changed = capture.instruction != instruction
        capture.instruction = instruction
        capture.updatedAt = Date()
        persist()
        if changed { Notifier.shared.prompt(instruction) }
    }

    private func persist() {
        do { try RecordingStore.save(capture) }
        catch { logger.error("Nie udało się zapisać wyniku w App Group.") }
    }
}
