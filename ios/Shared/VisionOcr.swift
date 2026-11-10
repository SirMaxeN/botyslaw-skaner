import Vision

enum VisionOcr {
    static func recognize(_ image: CGImage, regions: [String: CGRect]) throws -> [String: String] {
        let requests = regions.map { key, region -> (String, VNRecognizeTextRequest) in
            let request = VNRecognizeTextRequest()
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = false
            let available = (try? request.supportedRecognitionLanguages()) ?? []
            let preferred = ["pl-PL", "en-US"].filter { available.contains($0) }
            if !preferred.isEmpty { request.recognitionLanguages = preferred }
            request.regionOfInterest = region
            return (key, request)
        }
        guard !requests.isEmpty else { return [:] }
        try VNImageRequestHandler(cgImage: image).perform(requests.map { $0.1 })
        return Dictionary(uniqueKeysWithValues: requests.compactMap { key, request in
            let lines = request.results?.sorted { $0.boundingBox.midY > $1.boundingBox.midY }
                .compactMap { $0.topCandidates(1).first?.string } ?? []
            return lines.isEmpty ? nil : (key, lines.joined(separator: "\n"))
        })
    }
}
