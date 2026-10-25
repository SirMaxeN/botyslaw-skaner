package eu.komars.forgescanner.update

import org.junit.Assert.*
import org.junit.Test

class ReleasePolicyTest {
    @Test fun onlyOurHttpsDownloadPathIsAccepted() {
        assertTrue(ReleasePolicy.trustedUrl(ReleasePolicy.BASE + "forge-scanner-1.0.3-release.apk"))
        listOf("http://komars.eu/downloads/botyslaw-scanner/a.apk", "https://komars.eu.evil.test/downloads/botyslaw-scanner/a.apk", "https://evil.test/a.apk", ReleasePolicy.BASE + "../secret", ReleasePolicy.BASE + "a.apk#evil").forEach { assertFalse(it, ReleasePolicy.trustedUrl(it)) }
    }
    @Test fun downgradeAndSameVersionAreIgnored() {
        assertTrue(ReleasePolicy.newer(12, 11)); assertFalse(ReleasePolicy.newer(11,11)); assertFalse(ReleasePolicy.newer(10,11))
    }
    @Test fun checksumMustHaveExactly64HexCharacters() {
        assertTrue(ReleasePolicy.validHash("a1".repeat(32))); assertFalse(ReleasePolicy.validHash("a".repeat(63))); assertFalse(ReleasePolicy.validHash("g".repeat(64)))
    }
}
