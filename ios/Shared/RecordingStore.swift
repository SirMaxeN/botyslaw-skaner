import Foundation

struct ResourceCapture: Codable {
    var values: [String: Int64] = [:]
    var abbreviated: Set<String> = []
    var instruction: String
    var active = true
    var updatedAt = Date()
}

enum RecordingMode: String, Hashable { case resources, mine, rival }

struct BattleCapture: Codable, Equatable {
    let damage: String
    let health: String
    let bonuses: [String: Double]
    let caps: [String: Double]
    let updatedAt: Date
}

enum RecordingStore {
    static let group = "group.com.sirmaxen.BotyslawSkaner"

    private static func location(_ filename: String = "resource-capture.json") throws -> URL {
        guard let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) else {
            throw CocoaError(.fileNoSuchFile)
        }
        return container.appendingPathComponent(filename)
    }

    private static var defaults: UserDefaults? { UserDefaults(suiteName: group) }

    @discardableResult
    static func prepare(_ mode: RecordingMode) -> Bool {
        guard (try? location()) != nil, let defaults else { return false }
        defaults.set(mode.rawValue, forKey: "recording-mode")
        return true
    }

    static func mode() -> RecordingMode {
        defaults?.string(forKey: "recording-mode").flatMap(RecordingMode.init(rawValue:)) ?? .resources
    }

    static func saveBattle(_ capture: BattleCapture, mode: RecordingMode) throws {
        try JSONEncoder().encode(capture).write(to: location("battle-\(mode.rawValue).json"), options: .atomic)
    }

    static func loadBattle(_ mode: RecordingMode) -> BattleCapture? {
        guard let url = try? location("battle-\(mode.rawValue).json"), let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(BattleCapture.self, from: data)
    }

    static func save(_ capture: ResourceCapture) throws {
        try JSONEncoder().encode(capture).write(to: location(), options: .atomic)
    }

    static func load() -> ResourceCapture? {
        guard let url = try? location(), let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(ResourceCapture.self, from: data)
    }
}
