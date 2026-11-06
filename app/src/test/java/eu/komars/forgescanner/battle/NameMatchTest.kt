package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test

class NameMatchTest {
 @Test fun ocrSpacingAndConfusableDigitsMatchTheRosterName() {
  assertTrue(NameMatch.same("SmokeyNagata","Smokey Nagata"))
  assertTrue(NameMatch.same("Debro","Debr0"))
  assertTrue(NameMatch.same("HuzarowskyPI","HuzarowskyPL"))
 }
 @Test fun upsideDownNicknameMatchesItsRosterSpelling() {
  assertTrue(NameMatch.same("euepz","zdana"))
  assertFalse(NameMatch.same("euepz","Leadbiz"))
 }
 @Test fun differentOrShortNamesAreStillRejected() {
  assertFalse(NameMatch.same("JKH","JKG"))
  assertFalse(NameMatch.same("Leadbiz","Tortellini"))
  assertFalse(NameMatch.same("",""))
 }
}
