import Foundation
import Security

final class SyncClient {
    static let shared = SyncClient()
    private let base = URL(string: "https://komars.eu/discord/botyslaw-smith/mobile/")!
    private let key: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: "com.sirmaxen.BotyslawSkaner.discord",
        kSecAttrAccount as String: "paired-device",
    ]

    var pairedName: String? {
        guard let data = try? credentialData() else { return nil }
        return (try? JSONDecoder().decode(Credential.self, from: data))?.name
    }

    private struct Credential: Codable {
        let token: String
        let name: String
    }

    func pair(code: String) async throws -> String {
        let reply = try await post("pair", body: ["code": code], token: nil)
        let credential = try JSONDecoder().decode(Credential.self, from: reply)
        let data = try JSONEncoder().encode(credential)
        let status = SecItemUpdate(key as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecItemNotFound {
            var entry = key
            entry[kSecValueData as String] = data
            entry[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            guard SecItemAdd(entry as CFDictionary, nil) == errSecSuccess else { throw SyncError.keychain }
        } else if status != errSecSuccess { throw SyncError.keychain }
        return credential.name
    }

    func sendResources(_ values: [String: Int64], requestID: UUID, observedAt: Date) async throws {
        let credential = try JSONDecoder().decode(Credential.self, from: credentialData())
        let body: [String: Any] = [
            "request_id": requestID.uuidString.lowercased(),
            "observed_at": ISO8601DateFormatter().string(from: observedAt),
            "values": values,
        ]
        _ = try await post("resources", body: body, token: credential.token)
    }

    private func credentialData() throws -> Data {
        var query = key
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { throw SyncError.unpaired }
        guard status == errSecSuccess, let data = result as? Data else { throw SyncError.keychain }
        return data
    }

    private func post(_ route: String, body: [String: Any], token: String?) async throws -> Data {
        var request = URLRequest(url: base.appendingPathComponent(route), timeoutInterval: 20)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else {
            let error = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            throw SyncError.server(error?["error"] as? String ?? "Serwer odrzucił dane. Sprawdź połączenie i spróbuj ponownie.")
        }
        return data
    }

    private enum SyncError: LocalizedError {
        case unpaired, keychain, server(String)
        var errorDescription: String? {
            switch self {
            case .unpaired: return "Najpierw połącz aplikację z Discordem."
            case .keychain: return "Nie można uzyskać dostępu do klucza połączenia w systemowym pęku kluczy."
            case .server(let message): return message
            }
        }
    }
}
