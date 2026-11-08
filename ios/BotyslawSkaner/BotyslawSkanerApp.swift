import SwiftUI
import UserNotifications

@main
struct BotyslawSkanerApp: App {
    init() {
        // Prompts arrive as local notifications with sound while the player is in the game.
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
