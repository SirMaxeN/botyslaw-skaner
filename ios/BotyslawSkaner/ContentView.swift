import SwiftUI
import ReplayKit

private enum ScannerAppearance {
    // iOS counterparts of the Android palette. The broadcast button itself is drawn by iPadOS.
    static let background = Color(red: 11 / 255, green: 17 / 255, blue: 29 / 255)
    static let panel = Color(red: 18 / 255, green: 28 / 255, blue: 45 / 255)
    static let accent = Color(red: 114 / 255, green: 216 / 255, blue: 181 / 255)
}

private struct ResourceInput: Identifiable {
    let id: String
    let title: String
    var optional = false
    var isCost = false

    static let fields = [
        ResourceInput(id: "hammers", title: "Młotki"),
        ResourceInput(id: "mount_keys", title: "Klucze wierzchowców"),
        ResourceInput(id: "mount_cost", title: "Koszt jednego przyzwania wierzchowca", isCost: true),
        ResourceInput(id: "mounts", title: "Wierzchowce do łączenia, bez 1 bazowego"),
        ResourceInput(id: "skill_tickets", title: "Bilety umiejętności"),
        ResourceInput(id: "skill_cost_x5", title: "Koszt przyzwania umiejętności x5", isCost: true),
        ResourceInput(id: "pets", title: "Zwierzaki lub jajka do łączenia, bez 3 bazowych"),
        ResourceInput(id: "coins", title: "Monety", optional: true),
        ResourceInput(id: "gems", title: "Diamenty", optional: true),
        ResourceInput(id: "shells", title: "Skorupki", optional: true),
        ResourceInput(id: "red_vials", title: "Czerwone fiolki", optional: true),
        ResourceInput(id: "green_vials", title: "Zielone fiolki", optional: true),
    ]
}

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @State private var capture: ResourceCapture?
    @State private var entries: [String: String] = [:]
    @State private var pairedName = SyncClient.shared.pairedName
    @State private var pairingCode = ""
    @State private var message: String?
    @State private var busy = false
    @State private var pendingUpload: Submission?
    @State private var sent = false

    private struct Submission {
        let values: [String: Int64]
        let id = UUID()
        let observedAt: Date
    }

    var body: some View {
        NavigationStack {
            List {
                Section("Walka") {
                    NavigationLink("Symulacja walki 1 na 1") { BattleView() }
                        .listRowBackground(ScannerAppearance.panel)
                }
                Section("Skan zasobów") {
                    Label("Otwórz Forge Master w pionie. Pokaż ekran główny, a następnie wierzchowce, umiejętności i zwierzaki. Listy otwieraj od góry i przewijaj powoli z nakładającymi się rzędami. Drzewo technologii służy do odczytu fiolek.", systemImage: "list.number")
                        .listRowBackground(ScannerAppearance.panel)
                    HStack {
                        Text("Rozpocznij transmisję")
                        Spacer()
                        BroadcastButton().frame(width: 44, height: 44)
                    }
                    .listRowBackground(ScannerAppearance.panel)
                    Text("Zakończ transmisję w Centrum sterowania i wróć do Botysława. Jeśli nie pojawi się powiadomienie, nadal możesz przejść ręcznie przez wszystkie ekrany.")
                        .listRowBackground(ScannerAppearance.panel)
                    if let capture {
                        Text(capture.active ? capture.instruction : "Transmisja zakończona. Sprawdź odczyt poniżej.")
                            .listRowBackground(ScannerAppearance.panel)
                    }
                    Button("Odśwież odczyt") { reloadCapture() }.listRowBackground(ScannerAppearance.panel)
                }
                Section("Podsumowanie") {
                    Text("Sprawdź każdą wartość, w tym liczebność obu list. Uzupełnij brakujące odczyty ręcznie. Pola dodatkowe mogą pozostać puste.")
                        .listRowBackground(ScannerAppearance.panel)
                    ForEach(ResourceInput.fields) { field in
                        VStack(alignment: .leading) {
                            Text(field.title).font(.subheadline)
                            TextField(field.optional ? "Opcjonalnie" : "Wpisz liczbę", text: Binding(
                                get: { entries[field.id, default: ""] },
                                set: { entries[field.id] = $0; pendingUpload = nil; sent = false }
                            ))
                            .keyboardType(.numberPad)
                            .accessibilityLabel(field.title)
                            if capture?.abbreviated.contains(field.id) == true {
                                Text(field.id == "mounts" || field.id == "pets" ? "Koniec listy nie został potwierdzony. Sprawdź wynik." : "Gra pokazała wartość zaokrągloną.").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .listRowBackground(ScannerAppearance.panel)
                    }
                    Button(sent ? "Wysłano do Discorda" : "Zatwierdź i wyślij do Discorda", action: send)
                        .disabled(busy || sent || capture == nil || capture?.active == true || pairedName == nil)
                        .listRowBackground(ScannerAppearance.panel)
                }
                Section("Discord") {
                    Text(pairedName.map { "Połączono: \($0)" } ?? "Niepołączono").listRowBackground(ScannerAppearance.panel)
                    TextField("Kod z /mobile pair", text: $pairingCode)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .listRowBackground(ScannerAppearance.panel)
                    Button("Połącz", action: pair)
                        .disabled(busy || pairingCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        .listRowBackground(ScannerAppearance.panel)
                }
                Section {
                    Text("Tworzone przez SirMaxeN").font(.caption)
                        .listRowBackground(ScannerAppearance.panel)
                    Text("Botysław jest darmowy. Jeśli chcesz wesprzeć rozwój aplikacji i utrzymanie serwera, możesz postawić mi kawę.")
                        .font(.footnote)
                        .listRowBackground(ScannerAppearance.panel)
                    Link("☕ Postaw mi kawę", destination: URL(string: "https://buymeacoffee.com/sirmaxen")!)
                        .listRowBackground(ScannerAppearance.panel)
                }
            }
            .disabled(busy)
            .scrollContentBackground(.hidden)
            .background(ScannerAppearance.background)
            .tint(ScannerAppearance.accent)
            .preferredColorScheme(.dark)
            .navigationTitle("Botysław Skaner")
            .alert("Botysław Skaner", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
                Button("OK") { message = nil }
            } message: {
                Text(message ?? "")
            }
            .onAppear { RecordingStore.prepare(.resources); reloadCapture() }
            .onChange(of: scenePhase) { phase in if phase == .active { reloadCapture() } }
        }
    }

    private func reloadCapture() {
        guard let latest = RecordingStore.load(),
              latest.updatedAt != capture?.updatedAt || latest.active != capture?.active else { return }
        capture = latest
        entries = latest.values.mapValues { String($0) }
        pendingUpload = nil
        sent = false
    }

    private func pair() {
        let code = pairingCode.trimmingCharacters(in: .whitespacesAndNewlines)
        busy = true
        Task { @MainActor in
            defer { busy = false }
            do { pairedName = try await SyncClient.shared.pair(code: code); pairingCode = "" }
            catch { message = error.localizedDescription }
        }
    }

    private func send() {
        guard let capture, !capture.active else { return }
        guard Date().timeIntervalSince(capture.updatedAt) < 30 * 60 else {
            message = "Odczyt ma ponad 30 minut. Zrób świeży skan."
            return
        }
        var values: [String: Int64] = [:]
        for field in ResourceInput.fields {
            let raw = entries[field.id, default: ""].trimmingCharacters(in: .whitespacesAndNewlines)
            if field.optional && raw.isEmpty { continue }
            guard let number = Int64(raw), (0...9_000_000_000_000).contains(number), !field.isCost || number > 0 else {
                message = "Sprawdź pole „\(field.title)”."
                return
            }
            values[field.id] = number
        }
        let submission = pendingUpload ?? Submission(values: values, observedAt: capture.updatedAt)
        pendingUpload = submission
        busy = true
        Task { @MainActor in
            defer { busy = false }
            do {
                try await SyncClient.shared.sendResources(submission.values, requestID: submission.id, observedAt: submission.observedAt)
                sent = true
                message = "Zapisano zasoby na Discordzie."
            } catch { message = error.localizedDescription }
        }
    }
}

struct BroadcastButton: UIViewRepresentable {
    func makeUIView(context: Context) -> RPSystemBroadcastPickerView {
        let picker = RPSystemBroadcastPickerView(frame: .zero)
        picker.preferredExtension = "com.sirmaxen.BotyslawSkaner.broadcast"
        picker.showsMicrophoneButton = false
        return picker
    }

    func updateUIView(_ uiView: RPSystemBroadcastPickerView, context: Context) {}
}
