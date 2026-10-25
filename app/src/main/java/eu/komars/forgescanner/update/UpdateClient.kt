package eu.komars.forgescanner.update

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Bounded HTTPS metadata check. APK download and installation belong to the browser and OS. */
class UpdateClient(private val context: Context) {
    data class Release(val code: Long, val name: String, val url: String, val sha256: String)
    private fun connection(url: String): HttpURLConnection {
        require(ReleasePolicy.trustedUrl(url)) { "Untrusted update URL" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 15000; instanceFollowRedirects = false
        }
    }
    fun latest(): Release? {
        val c = connection(BASE + "latest.json")
        try {
            check(c.responseCode == 200)
            val bytes = c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(1024)
                while (out.size() <= 16384) { val n = input.read(buffer); if (n < 0) break; out.write(buffer, 0, n) }; out.toByteArray()
            }
            require(bytes.size <= 16384)
            val j = JSONObject(String(bytes, Charsets.UTF_8))
            require(j.getString("applicationId") == context.packageName)
            val code = j.getLong("versionCode")
            if (!ReleasePolicy.newer(code, code(context.packageManager.getPackageInfo(context.packageName, 0)))) return null
            val hash = j.getString("sha256")
            require(ReleasePolicy.validHash(hash))
            val url = j.getString("url")
            require(ReleasePolicy.trustedUrl(url) && url.endsWith(".apk"))
            val name = j.getString("versionName")
            require(name.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
            return Release(code, name, url, hash)
        } finally { c.disconnect() }
    }
    @Suppress("DEPRECATION")
    private fun code(info: android.content.pm.PackageInfo): Long = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    companion object { const val BASE = ReleasePolicy.BASE }
}
