package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.GameNumberParser
import eu.komars.forgescanner.model.GameScreenKind
import eu.komars.forgescanner.model.GameUiGeometryDetector
import eu.komars.forgescanner.model.PixelRegion
import eu.komars.forgescanner.model.ScanSession
import eu.komars.forgescanner.model.TopCounters

data class RecordingCounter(val key: String, val region: PixelRegion)
data class RecordingValue(val key: String, val value: Long, val abbreviated: Boolean)

/** Read-only resource capture. The player controls game navigation; the UI reviews values before sync. */
class GuidedRecordingFlow {
    private data class Field(val key: String, val step: AutoScanStep? = null, val icon: TopCounters.Icon? = null)

    private val fields = mapOf(
        GameScreenKind.HOME to listOf(
            Field("hammers", AutoScanStep.HAMMERS), Field("coins", icon = TopCounters.Icon.CROWN), Field("gems", icon = TopCounters.Icon.PINK)
        ),
        GameScreenKind.MOUNTS to listOf(Field("mount_keys", AutoScanStep.MOUNT_KEYS), Field("mount_cost", AutoScanStep.MOUNT_COST)),
        GameScreenKind.SKILLS to listOf(Field("skill_tickets", AutoScanStep.SKILL_TICKETS), Field("skill_cost_x5", AutoScanStep.SKILL_COST)),
        GameScreenKind.PETS to listOf(Field("shells", icon = TopCounters.Icon.SHELL)),
        GameScreenKind.TECHNOLOGY to listOf(Field("red_vials", icon = TopCounters.Icon.RED), Field("green_vials", icon = TopCounters.Icon.GREEN))
    )
    private var previousScreen = GameScreenKind.UNKNOWN
    private var visible = emptyList<RecordingCounter>()
    private var previous = emptyMap<String, RecordingValue>()
    private val accepted = linkedMapOf<String, RecordingValue>()

    private var inventory: RecordingInventory? = null
    private var outsideInventory = 0
    private val inventories = mutableListOf<RecordingValue>()

    fun takeInventoryResults(): List<RecordingValue> = inventories.toList().also { inventories.clear() }

    fun finishInventory() {
        inventory?.finish()?.let { value ->
            accepted[value.key] = value
            inventories += value
        }
        inventory = null
        outsideInventory = 0
    }

    fun regions(width: Int, height: Int, pixels: IntArray, nowMillis: Long = 0): List<RecordingCounter> {
        val detector = GameUiGeometryDetector(width, height, pixels, allowTabletAspect = true)
        val kind = detector.screenKind()
        if (previousScreen != kind) previous = emptyMap()
        previousScreen = kind
        if (nowMillis > 0) {
            val current = inventory
            if (current != null && current.kind != kind) {
                outsideInventory++
                if (outsideInventory >= 2) finishInventory()
            } else {
                outsideInventory = 0
            }
            val key = when (kind) {
                GameScreenKind.MOUNTS -> "mounts"
                GameScreenKind.PETS -> "pets"
                else -> null
            }
            if (inventory == null && key != null && key !in accepted) {
                inventory = RecordingInventory.begin(kind, detector, width, height, pixels)
            }
            inventory?.takeIf { it.kind == kind }?.offer(width, height, pixels, nowMillis)
        }
        visible = fields[kind].orEmpty().mapNotNull { field ->
            if (field.key in accepted) null
            else {
                val region = field.step?.let { detector.detect(it)?.region }
                    ?: field.icon?.let { TopCounters.forCounter(it, kind, width, height, pixels) }
                region?.let { RecordingCounter(field.key, it) }
            }
        }
        return visible
    }

    fun observe(textByCounter: Map<String, String>): List<RecordingValue> {
        val values = visible.mapNotNull { counter ->
            val raw = textByCounter[counter.key] ?: return@mapNotNull null
            val cleaned = if (counter.key == "skill_cost_x5") raw.replace(Regex("[x×]\\s*5", RegexOption.IGNORE_CASE), "") else raw
            val parsed = GameNumberParser.parse(cleaned)
                ?: GameNumberParser.tokenPattern.findAll(cleaned).toList().singleOrNull()?.value?.let(GameNumberParser::parse)
                ?: return@mapNotNull null
            val amount = parsed.wholeValueOrNull()?.takeIf { it <= ScanSession.MAX_VALUE } ?: return@mapNotNull null
            if (counter.key.endsWith("_cost") || counter.key == "skill_cost_x5") {
                if (amount == 0L || parsed.abbreviated) return@mapNotNull null
            }
            RecordingValue(counter.key, amount, parsed.abbreviated)
        }.associateBy { it.key }
        val confirmed = values.values.filter { it == previous[it.key] && it.key !in accepted }
        confirmed.forEach { accepted[it.key] = it }
        previous = values
        return confirmed
    }

    fun instruction(): String = when {
        inventory?.kind == GameScreenKind.MOUNTS && "mount_cost" in accepted -> "Powoli przewiń wierzchowce od góry do końca. Zamknij listę, żeby zapisać wynik."
        inventory?.kind == GameScreenKind.PETS -> "Powoli przewiń zwierzaki lub jajka od góry do końca. Zamknij listę, żeby zapisać wynik."
        "hammers" !in accepted -> "Otwórz ekran główny gry i pokaż licznik młotków."
        "mount_keys" !in accepted || "mount_cost" !in accepted -> "Otwórz przyzywanie wierzchowców i pokaż klucze oraz koszt x1."
        "skill_tickets" !in accepted || "skill_cost_x5" !in accepted -> "Otwórz umiejętności i pokaż bilety oraz koszt x5."
        "mounts" !in accepted -> "Otwórz górę listy wierzchowców i powoli przewiń do końca."
        "pets" !in accepted -> "Otwórz górę listy zwierzaków lub jajek i powoli przewiń do końca."
        else -> "Zasoby odczytane. Opcjonalnie otwórz Drzewo technologii, następnie zatrzymaj transmisję i sprawdź wynik."
    }
}
