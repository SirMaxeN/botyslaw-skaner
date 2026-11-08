package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class WarRosterProofTest {
 @Test fun readsClanAboveMetadataNotNamesBelowIt() {
  assertEquals("PolskaMOC[PL]",WarRosterProof.clan("4,869,871\nPolskaMOC[_PL_]\nPoziom-S+\nŁączna Moc: 42.8b\nTechnologie Klanu: 0.15%\nCzłonkowie: 50/50\nSirMaxeN"))
  assertEquals("Enemy[ABC]",WarRosterProof.clan("Enemy [ABC]\nLevel S+\nTotal power: 100b\nMembers: 47/50"))
  assertNull(WarRosterProof.clan("Członkowie: 50/50\nSirMaxeN"))
  assertNull(WarRosterProof.clan("Dzień 2\nCzłonkowie: 50/50"))
 }
 @Test fun identicalVisibleHeaderCannotBecomeAnotherClanThroughOcrTagNoise() {
  val w=256;val h=128;val p=IntArray(w*h){i->if(i%13==0)0xffffffff.toInt()else 0xff414a5b.toInt()}
  val a=WarRosterProof.headerSnapshot(w,h,p)
  assertTrue(WarRosterProof.sameHeader(a,WarRosterProof.headerSnapshot(w,h,p.copyOf())))
  val other=a.copyOf();other[100]=0xff000000.toInt();assertFalse(WarRosterProof.sameHeader(a,other))
  assertFalse(WarRosterProof.sameHeader(IntArray(8192){-1},IntArray(8192){0}))
  assertFalse(WarRosterProof.sameHeader(IntArray(8192){0},IntArray(8192){0}))
 }
 @Test fun sameClanIgnoresSpacingAndCaseNotFlagAppearance() {
  assertTrue(WarRosterProof.sameClan("PolskaMOC [_PL_]","polskamoc[_pl_]"))
  assertFalse(WarRosterProof.sameClan("A [PL]","B [PL]"))
 }
 @Test fun uploadNeedsTwoCompleteDifferentRosters() {
  val a=listOf(WarMember("Ally",100.0));val b=listOf(WarMember("Enemy",90.0))
  assertTrue(WarRosterProof.complete(a,b,1,1,"ClanA","ClanB"))
  assertFalse(WarRosterProof.complete(a,b,2,1,"ClanA","ClanB"))
  assertFalse(WarRosterProof.complete(a,b,1,1,"ClanA","ClanA"))
  assertFalse(WarRosterProof.complete(a,a.reversed(),1,1,"ClanA","ClanB"))
  assertFalse(WarRosterProof.complete(a,b,1,1,null,"ClanB"))
 }
 @org.junit.Test fun clanTagOcrBracketsAreRestored() {
  assertEquals("PolskaMoc[PL]",WarRosterProof.displayName("PolskaMoc(_PL_J"))
  assertEquals("PolskaMoc[PL]",WarRosterProof.displayName("PolskaMoc[_PL_]"))
  assertEquals("PolskaMoc[PL]",WarRosterProof.displayName("PolskaMoc (PL)"))
  // Closing bracket read as "J": the tag must not swallow it.
  assertEquals("PolskaMoc[PL]",WarRosterProof.displayName("PolskaMoc(PLJ"))
  assertEquals("PolskaMoc[PL]",WarRosterProof.displayName("PolskaMoc[PLJ"))
  assertEquals("LeChauvin[HEXA]",WarRosterProof.displayName("LeChauvin(HEXAJ"))
  assertEquals("LeChauvin[HEXA]",WarRosterProof.displayName("LeChauvin[HEXA]"))
  assertEquals("PolskaMOC[PL]",WarRosterProof.displayName("PolskaMOC_PL_J"))
  assertEquals("Polska_Moc",WarRosterProof.displayName("Polska_Moc"))
  assertEquals("Klan bez tagu",WarRosterProof.displayName("Klan bez tagu"))
 }
}
