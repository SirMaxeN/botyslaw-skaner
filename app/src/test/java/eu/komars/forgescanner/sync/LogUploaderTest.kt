package eu.komars.forgescanner.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogUploaderTest {
    /** Must match the bot's DeviceLogs.safePath, or the whole batch is refused. */
    @Test fun sendsOnlyTextFilesWithServerSafePaths() {
        assertTrue(LogUploader.accepted("ext/logs/full_war-events.jsonl", 10))
        assertTrue(LogUploader.accepted("ext/logs/resource-crops/HAMMERS-1763000000.json", 10))
        assertTrue(LogUploader.accepted("ext/profiles/nasi-polskamoc-ala-1a2b3c4d.json", 10))
        assertTrue(LogUploader.accepted("cache/foreground-loss.txt", 10))
        assertFalse(LogUploader.accepted("ext/logs/resource-crops/HAMMERS-1.png", 10))
        assertFalse(LogUploader.accepted("ext/logs/clan-tech/page-1.png", 10))
        assertFalse(LogUploader.accepted("cache/.hidden.txt", 10))
        assertFalse(LogUploader.accepted("cache/a b.txt", 10))
        assertFalse(LogUploader.accepted("cache/empty.txt", 0))
        assertFalse(LogUploader.accepted("a/b/c/d/e.txt", 10))
    }
}
