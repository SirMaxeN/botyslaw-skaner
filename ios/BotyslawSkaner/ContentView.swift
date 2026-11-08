import SwiftUI
import ReplayKit

/// Main screen: resource scan, 1v1 battle, Discord pairing, options.
/// War list and clan ranking are intentionally absent (Android + Discord /report only).
struct ContentView: View {
    @State private var pairedName: String? = SyncClient.shared.pairedName

    var body: some View {
        NavigationStack {
            List {
                Section("Skan") {
                    // The system picker is the only way to start a broadcast; the user confirms and gets a 3 s countdown.
                    BroadcastButton(title: "Rozpocznij skan zasobów")
                    NavigationLink("Symulacja walki 1 na 1") { Text("TODO (Etap 4): odczyt profilu + PvpBattleEngine") }
                    NavigationLink("Ostatni wynik") { Text("TODO: wyniki z App Group (results.json)") }
                }
                Section("Discord") {
                    Text(pairedName.map { "Połączono: \($0)" } ?? "Niepołączono")
                    NavigationLink("Połącz kodem z /start") { Text("TODO: SyncClient.pair(code:)") }
                }
                Section("Opcje") {
                    Text("Wersja \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?")")
                    #if DEBUG
                    NavigationLink("Źródło: nagranie (symulator)") { Text("TODO: AVAssetReader → ten sam pipeline co rozszerzenie") }
                    #endif
                }
            }
            .navigationTitle("Botysław Skaner")
        }
    }
}

/// Wraps RPSystemBroadcastPickerView preselected to our extension.
struct BroadcastButton: UIViewRepresentable {
    let title: String
    func makeUIView(context: Context) -> RPSystemBroadcastPickerView {
        let picker = RPSystemBroadcastPickerView(frame: CGRect(x: 0, y: 0, width: 44, height: 44))
        picker.preferredExtension = "com.sirmaxen.BotyslawSkaner.broadcast"
        picker.showsMicrophoneButton = false
        // TODO: style with the title text (the picker only renders its own button).
        return picker
    }
    func updateUIView(_ uiView: RPSystemBroadcastPickerView, context: Context) {}
}
