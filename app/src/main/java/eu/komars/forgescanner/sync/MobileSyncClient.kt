package eu.komars.forgescanner.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import eu.komars.forgescanner.model.ResourceField
import java.net.URL
import java.security.KeyStore
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/** Fixed HTTPS origin, no redirects; device credential encrypted with a nonexportable Android key. */
class MobileSyncClient(context: Context, private val demoMode: () -> Boolean = { false }, private val testTransport: ((String, JSONObject, String?) -> JSONObject)? = null) {
    private val settings = context.applicationContext.getSharedPreferences("mobile_sync", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "ResourceSync").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val pairingEpoch = java.util.concurrent.atomic.AtomicLong()
    val name: String? get() = if (demoMode()) "Gracz demonstracyjny (DEMO)" else settings.getString("name", null)
    val paired: Boolean get() = demoMode() || settings.contains("credential")

    fun pair(code: String, callback: (Result<String>) -> Unit) {
        if (demoMode()) { main.post { callback(Result.success("DEMO: połączenie symulowane lokalnie. Bez dostępu do Discorda.")) }; return }
        val epoch = pairingEpoch.get()
        execute(callback) {
        val response = post("pair", JSONObject().put("code", code.trim().removeSurrounding("`")), null)
        val token = response.getString("token")
        require(Regex("[A-Za-z0-9_-]{43}").matches(token)) { "Nieprawidłowa odpowiedź serwera." }
        val encrypted = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key())
            Base64.encodeToString(iv + doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        }
        val label = response.getString("name")
        synchronized(settings) {
            check(pairingEpoch.get() == epoch) { "Łączenie anulowane po odłączeniu telefonu." }
            settings.edit().putString("credential", encrypted).putString("name", label).apply()
        }
        "Połączono: $label"
        }
    }

    fun send(values: Map<ResourceField, Long>, callback: (Result<String>) -> Unit) {
        if (demoMode()) { main.post { callback(Result.success("DEMO: zasymulowano wysłanie danych. Nic nie wysłano do serwera ani Discorda.")) }; return }
        val credential = settings.getString("credential", null)
        execute(callback) {
        require(values.keys.containsAll(ResourceField.entries)) { "Sprawdź wszystkie wartości." }
        val names = mapOf(ResourceField.TICKETS to "skill_tickets", ResourceField.MOUNT_KEYS to "mount_keys",
            ResourceField.HAMMERS to "hammers", ResourceField.PETS to "pets", ResourceField.MOUNTS to "mounts",
            ResourceField.SKILL_COST to "skill_cost_x5", ResourceField.MOUNT_COST to "mount_cost")
        val resources = JSONObject()
        names.forEach { (field, name) -> resources.put(name, values.getValue(field)) }
        val payload = JSONObject().put("request_id", UUID.randomUUID().toString())
            .put("observed_at", Instant.now().toString()).put("values", resources)
        val response = post("resources", payload, token(credential))
        "Zapisano w bocie: ${response.getString("name")}"
        }
    }

    fun warStatus(callback:(Result<String>)->Unit) {
        val credential=settings.getString("credential",null)
        execute(callback) { post("war-status",JSONObject(),token(credential)).toString() }
    }
    fun sendWar(payload:JSONObject,callback:(Result<String>)->Unit) {
        val credential=settings.getString("credential",null)
        execute(callback) { post(if(payload.optBoolean("test_mode"))"war-test" else "war-list",payload,token(credential)).toString() }
    }
    fun disconnect() { synchronized(settings) { pairingEpoch.incrementAndGet(); settings.edit().clear().apply() } }

    private fun token(credential: String?): String {
        val bytes = Base64.decode(requireNotNull(credential) { "Najpierw połącz telefon z Discordem." }, Base64.NO_WRAP)
        require(bytes.size > 28)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(KEY, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    private fun post(route: String, payload: JSONObject, credential: String?): JSONObject {
        check(!demoMode()) { "Tryb DEMO blokuje połączenia z serwerem bota." }
        testTransport?.let { return it(route, payload, credential) }
        val connection = URL("https://komars.eu/discord/botyslaw-smith/mobile/$route").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"; connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000; connection.readTimeout = 10000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            if (credential != null) connection.setRequestProperty("Authorization", "Bearer $credential")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = java.io.ByteArrayOutputStream()
            stream?.use { input ->
                val buffer = ByteArray(1024)
                while (true) {
                    val length = input.read(buffer)
                    if (length < 0) break
                    require(bytes.size() + length <= 8192) { "Odpowiedź serwera jest za duża." }
                    bytes.write(buffer, 0, length)
                }
            }
            val response = runCatching { JSONObject(bytes.toString("UTF-8")) }.getOrElse { JSONObject() }
            check(status in 200..299) {
                if(status==404 && route in listOf("war-test","war-list","war-status"))
                    "Raport ${if(route=="war-test") "testowy " else "wojenny "}nie jest dostępny na serwerze (HTTP 404). Listy są zachowane w tej sesji. Po aktualizacji bota wybierz Kontynuuj — bez ponownego skanu."
                else response.optString("error", "Nie udało się połączyć z serwerem (HTTP $status).")
            }
            return response
        } finally { connection.disconnect() }
    }

    private fun execute(callback: (Result<String>) -> Unit, operation: () -> String) {
        worker.execute {
            val result = runCatching(operation)
            main.post { callback(result) }
        }
    }
    companion object { private const val KEY = "botyslaw_device_sync_v1" }
}
