import UserNotifications

/// Tells the player what to do next while the game is in front: a banner with sound,
/// only on step changes or problems, never per frame.
final class Notifier {
    static let shared = Notifier()
    private var lastText: String?
    private var lastAt = Date.distantPast
    private let minGap: TimeInterval = 2

    func prompt(_ text: String, sound: Bool = true) {
        guard text != lastText || Date().timeIntervalSince(lastAt) > 15,
              Date().timeIntervalSince(lastAt) >= minGap else { return }
        lastText = text; lastAt = Date()
        let content = UNMutableNotificationContent()
        content.title = "Botysław"
        content.body = text
        if sound { content.sound = .default }
        // TODO: verify on device that the broadcast extension may post local notifications;
        //       fallback = write to App Group and let the app post them.
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "botyslaw.prompt", content: content, trigger: nil))
    }
}
