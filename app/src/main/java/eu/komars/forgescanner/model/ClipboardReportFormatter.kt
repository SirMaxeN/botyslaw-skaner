package eu.komars.forgescanner.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ClipboardReportFormatter {
    fun command(snapshot: ScanSnapshot): String {
        require(snapshot.readyToExport) { "Confirm all seven values before copying." }
        val names = linkedMapOf(ResourceField.TICKETS to "skill_tickets", ResourceField.MOUNT_KEYS to "mount_keys",
            ResourceField.MOUNTS to "mounts", ResourceField.HAMMERS to "hammers", ResourceField.PETS to "pets",
            ResourceField.SKILL_COST to "skill_cost_x5", ResourceField.MOUNT_COST to "mount_cost")
        return "/import dane:" + names.entries.joinToString(",", "{", "}") { (field, key) ->
            "\"$key\":" + snapshot.values.getValue(field).value
        }
    }

    fun format(snapshot: ScanSnapshot): String = buildString {
        appendLine("Forge Master · zasoby")
        appendLine("Odczyt: " + SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale("pl", "PL")).format(Date(snapshot.updatedAtMillis)))
        appendLine()
        ResourceField.entries.forEach { field ->
            val observation = snapshot.values[field]
            append(field.label).append(": ")
            if (observation == null) append("nie odczytano")
            else {
                if (observation.approximate) append("≈ ")
                append(observation.value)
                if (!observation.confirmed) append(" [sprawdź]")
            }
            appendLine()
        }
        if (snapshot.values.any { (field, value) -> value.approximate && field in listOf(ResourceField.PETS, ResourceField.MOUNTS) }) {
            appendLine().appendLine("≈ Lista oznaczona jako estymacja: liczba oszacowana z przewijania, wymaga sprawdzenia.")
        }
        if (snapshot.values.any { (field, value) -> value.approximate && field !in listOf(ResourceField.PETS, ResourceField.MOUNTS) }) {
            appendLine().appendLine("≈ Wartość rozwinięta ze skrótu gry, np. 9,88k = 9880. Gra może ukrywać końcowe cyfry.")
        }
        appendLine().append("Raport do wklejenia na Discordzie. Dane bota zaktualizuj przez /update i formularz kosztów; samo wklejenie nie zapisuje danych.")
    }.trim()
}
