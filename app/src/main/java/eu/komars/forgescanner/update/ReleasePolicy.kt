package eu.komars.forgescanner.update

object ReleasePolicy {
    const val BASE = "https://komars.eu/downloads/botyslaw-skaner/"
    fun trustedUrl(url: String): Boolean = url.startsWith(BASE) && !url.contains("..") && !url.contains("#") && !url.contains("?")
    fun validHash(value: String): Boolean = value.matches(Regex("[a-fA-F0-9]{64}"))
    fun newer(remote: Long, installed: Long): Boolean = remote > installed
}
