import UserNotifications
import os

/// Called on the analysis queue when the current scan instruction changes.
final class Notifier {
    static let shared = Notifier()
    private let logger = Logger(subsystem: "com.sirmaxen.BotyslawSkaner", category: "notifications")

    func prompt(_ text: String) {
        let content = UNMutableNotificationContent()
        content.title = "Botysław"
        content.body = text
        content.sound = .default
        let request = UNNotificationRequest(identifier: "botyslaw.prompt", content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request) { [logger] error in
            if error != nil { logger.error("Nie udało się pokazać powiadomienia. Instrukcja pozostaje dostępna w aplikacji.") }
        }
    }
}
