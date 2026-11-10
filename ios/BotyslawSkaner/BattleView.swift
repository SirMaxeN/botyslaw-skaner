import BotyslawShared
import SwiftUI

struct BattleView: View {
    @Environment(\.scenePhase) private var scenePhase
    @State private var selected: RecordingMode = .mine
    @State private var mine = BattleDraft()
    @State private var rival = BattleDraft()
    @State private var result: String?
    @State private var captured: [RecordingMode: Date] = [:]

    var body: some View {
        List {
            Section("Odczyt profilu") {
                Picker("Profil do skanu", selection: $selected) {
                    Text("Moja postać").tag(RecordingMode.mine)
                    Text("Przeciwnik").tag(RecordingMode.rival)
                }
                HStack {
                    Text("Rozpocznij transmisję")
                    Spacer()
                    BroadcastButton().frame(width: 44, height: 44)
                }
                Text("Dla każdej postaci rozpocznij osobną transmisję. Otwórz profil, zatrzymaj się na łącznych obrażeniach i zdrowiu, a potem powoli przewiń bonusy. Zakończ transmisję w Centrum sterowania.")
                Button("Wczytaj ostatni odczyt", action: refresh)
            }
            Section("Moja postać") { BattleProfileEditor(draft: $mine) }
            Section("Przeciwnik") { BattleProfileEditor(draft: $rival) }
            Section("Wynik") {
                Button("Symuluj walkę 1 na 1", action: simulate)
                if let result { Text(result) }
                Text("Model korzysta z zatwierdzonych danych obu postaci. Uzupełnij broń, wyposażenie, umiejętności i wróżkę oraz bonusy, których skan nie odczytał.")
                    .font(.footnote)
            }
        }
        .navigationTitle("Symulacja walki")
        .onAppear { prepare(); refresh() }
        .onChange(of: selected) { _ in prepare() }
        .onChange(of: scenePhase) { phase in if phase == .active { refresh() } }
    }

    private func prepare() {
        if !RecordingStore.prepare(selected) { result = "Brak dostępu do App Group. Sprawdź podpisy aplikacji i rozszerzenia." }
    }

    private func refresh() {
        for mode in [RecordingMode.mine, .rival] {
            guard let scan = RecordingStore.loadBattle(mode), captured[mode] != scan.updatedAt else { continue }
            if mode == .mine { mine.read(scan) } else { rival = BattleDraft(); rival.read(scan) }
            captured[mode] = scan.updatedAt
            result = nil
        }
    }

    private func simulate() {
        guard let mine = mine.input, let rival = rival.input else {
            result = "Sprawdź typ broni obu postaci i wybierz różne umiejętności."
            return
        }
        let outcome = RecordingBattleSimulator.shared.simulate(player: mine, opponent: rival)
        guard let battle = outcome.result, battle.count > 0 else {
            result = outcome.error ?? "Sprawdź dane obu postaci."
            return
        }
        func percent(_ count: Int32) -> String {
            (Double(count) / Double(battle.count)).formatted(.percent.precision(.fractionLength(1)))
        }
        result = "Wygrana: \(percent(battle.wins)) · przegrana: \(percent(battle.losses)) · remis: \(percent(battle.draws)). Prób: \(battle.count)."
    }
}

private struct SkillInput: Identifiable {
    var id: Int
    var key = ""
    var level = ""
}

private struct BattleDraft {
    var damage = ""
    var health = ""
    var weapon = ""
    var pets = 0
    var mounted = false
    var bonuses: [String: String] = [:]
    var caps: [String: String] = [:]
    var skills = (0..<3).map { SkillInput(id: $0) }
    var fairy = ""
    var fairyLevel = ""
    var skillMastery = ""
    var skillAscension = 0

    mutating func read(_ scan: BattleCapture) {
        damage = scan.damage
        health = scan.health
        bonuses = scan.bonuses.mapValues { String($0) }
        caps = scan.caps.mapValues { String($0) }
    }

    var input: RecordingBattleInput? {
        let equipped = skills.filter { !$0.key.isEmpty }
        guard !weapon.isEmpty, Set(equipped.map(\.key)).count == equipped.count else { return nil }
        return RecordingBattleInput(damage: damage, health: health, ranged: weapon == "ranged", bonuses: bonuses, caps: caps,
                                    skills: Dictionary(uniqueKeysWithValues: equipped.map { ($0.key, $0.level) }),
                                    pets: Int32(pets), mounted: mounted, skillMastery: skillMastery, skillAscension: Int32(skillAscension),
                                    fairy: fairy, fairyLevel: fairyLevel)
    }
}

private struct BattleProfileEditor: View {
    @Binding var draft: BattleDraft
    private static let bonusChoices = RecordingBattleSimulator.shared.bonusChoices()
    private static let skillChoices = RecordingBattleSimulator.shared.skillChoices()

    var body: some View {
        TextField("Łączne obrażenia, np. 37.1m", text: $draft.damage).autocorrectionDisabled()
        TextField("Łączne zdrowie, np. 111m", text: $draft.health).autocorrectionDisabled()
        Picker("Broń", selection: $draft.weapon) {
            Text("Wybierz").tag("")
            Text("Wręcz").tag("melee")
            Text("Dystansowa").tag("ranged")
        }
        Stepper("Liczba wyposażonych zwierzaków: \(draft.pets)", value: $draft.pets, in: 0...3)
        Toggle("Wyposażony wierzchowiec", isOn: $draft.mounted)
        DisclosureGroup("Bonusy i limity") {
            ForEach(Self.bonusChoices, id: \.key) { option in
                TextField(option.title + " (%)", text: Binding(
                    get: { draft.bonuses[option.key, default: ""] },
                    set: { draft.bonuses[option.key] = $0 }
                )).keyboardType(.decimalPad)
                if draft.caps[option.key] != nil || ["CRIT", "BLOCK"].contains(option.key) {
                    TextField("Limit · " + option.title, text: Binding(
                        get: { draft.caps[option.key, default: ""] },
                        set: { draft.caps[option.key] = $0 }
                    )).keyboardType(.decimalPad)
                }
            }
        }
        DisclosureGroup("Umiejętności i wróżka") {
            ForEach($draft.skills) { $skill in
                Picker("Umiejętność \(skill.id + 1)", selection: $skill.key) {
                    Text("Brak").tag("")
                    ForEach(Self.skillChoices, id: \.key) { option in Text(option.title).tag(option.key) }
                }
                if !skill.key.isEmpty { TextField("Poziom umiejętności", text: $skill.level).keyboardType(.numberPad) }
            }
            TextField("Mistrzostwo umiejętności (%)", text: $draft.skillMastery).keyboardType(.decimalPad)
            Stepper("Gwiazdy wzniesienia umiejętności: \(draft.skillAscension)", value: $draft.skillAscension, in: 0...5)
            Picker("Wróżka", selection: $draft.fairy) {
                Text("Brak").tag("")
                ForEach(["LORA", "MIRA", "TIRA"], id: \.self) { Text($0).tag($0) }
            }
            if !draft.fairy.isEmpty { TextField("Poziom wróżki", text: $draft.fairyLevel).keyboardType(.numberPad) }
        }
    }
}
