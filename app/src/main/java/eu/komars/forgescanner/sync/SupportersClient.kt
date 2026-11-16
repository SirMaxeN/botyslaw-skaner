package eu.komars.forgescanner.sync

import android.content.Context
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Thank-you list for the start screen. Shown at once from the last downloaded copy (or the built-in list on the
 * first start); a fresh copy is fetched in the background for the next start, so the intro never waits for the network.
 * The list lives on the bot server (config/supporters.json), so it can change without an app update.
 */
class SupportersClient(context: Context, private val demoMode: () -> Boolean = { false }) {
    data class Supporters(val title: String, val names: List<String>)

    private val settings = context.applicationContext.getSharedPreferences("supporters", Context.MODE_PRIVATE)

    fun cached(): Supporters = settings.getString("json", null)?.let { runCatching { parse(it) }.getOrNull() } ?: DEFAULT

    /** Downloads the current list on a background thread and keeps it for the next start. */
    fun refresh() {
        if (demoMode()) return
        Thread {
            runCatching {
                val connection = URL(URL_LIST).openConnection() as HttpsURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 6000; connection.readTimeout = 6000
                    if (connection.responseCode != 200) return@runCatching
                    val bytes = connection.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                        while (true) {
                            val length = input.read(buffer); if (length < 0) break
                            require(out.size() + length <= 256 * 1024); out.write(buffer, 0, length)
                        }
                        out.toString("UTF-8")
                    }
                    parse(bytes)
                    settings.edit().putString("json", bytes).apply()
                } finally { connection.disconnect() }
            }
        }.apply { isDaemon = true; name = "supporters" }.start()
    }

    companion object {
        const val URL_LIST = "https://komars.eu/discord/botyslaw-smith/mobile/supporters"
        val DEFAULT = Supporters("Dziękuję za wsparcie ❤",
            listOf("PolskiWariat", "EvilMosquito", "MarianoItaliano", "XlawoK", "KrecikPulpecik", "UltraLegion", "Skubi1990"))

        fun parse(json: String): Supporters {
            val data = JSONObject(json)
            val list = data.getJSONArray("supporters")
            val names = (0 until list.length()).map { list.optString(it).trim().take(40) }.filter { it.isNotEmpty() }.distinct()
            return Supporters(data.optString("title").trim().take(60).ifEmpty { DEFAULT.title }, names)
        }
    }
}
