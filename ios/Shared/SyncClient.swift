import Foundation

/// Same bot API as Android's sync/MobileSyncClient.kt, limited to pairing and resources.
final class SyncClient {
    static let shared = SyncClient()
    private let base = URL(string: "https://komars.eu/discord/botyslaw-smith/mobile/")!
    private let defaults = UserDefaults(suiteName: "group.com.sirmaxen.BotyslawSkaner")

    var pairedName: String? { defaults?.string(forKey: "pairedName") }

    /// POST pair {code} → {token, name}
    func pair(code: String) async throws -> String {
        let reply = try await post("pair", body: ["code": code], token: nil)
        guard let token = reply["token"] as? String, let name = reply["name"] as? String else { throw URLError(.badServerResponse) }
        // TODO: store the token in the Keychain with the App Group access group (not UserDefaults).
        defaults?.set(token, forKey: "token-TODO-keychain")
        defaults?.set(name, forKey: "pairedName")
        return name
    }

    /// POST resources {request_id, observed_at, values:{skill_tickets, mount_keys, hammers, pets, mounts, skill_cost_x5, mount_cost}}
    func sendResources(_ values: [String: Int]) async throws {
        let body: [String: Any] = [
            "request_id": UUID().uuidString.lowercased(),
            "observed_at": ISO8601DateFormatter().string(from: Date()),
            "values": values,
        ]
        _ = try await post("resources", body: body, token: defaults?.string(forKey: "token-TODO-keychain"))
    }

    private func post(_ route: String, body: [String: Any], token: String?) async throws -> [String: Any] {
        var request = URLRequest(url: base.appendingPathComponent(route))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
        return (try JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
    }
}
