package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
class WarPreparationModelTest {
 @Test fun sundayWindowIsFixedPolishOneAmIncludingMondayAndSummer() {
  assertNull(WarPreparationModel.day(Instant.parse("2026-10-31T23:59:59Z")))
  assertEquals("2026-11-01",WarPreparationModel.day(Instant.parse("2026-11-01T00:00:00Z")))
  assertEquals("2026-11-01",WarPreparationModel.day(Instant.parse("2026-11-01T23:59:59Z")))
  assertNull(WarPreparationModel.day(Instant.parse("2026-11-02T00:00:00Z")))
  assertEquals("2026-07-05",WarPreparationModel.day(Instant.parse("2026-07-04T23:00:00Z")))
 }
 @Test fun currentResearchValueIsNotTheGreenNextLevelIncrement() {
  assertEquals(8700.0,WarPreparationModel.bonus("Obrażenia Wojny Klanów\nObrażenia +8.7k%. (+100%)")!!,.001)
  assertEquals(7700.0,WarPreparationModel.bonus("Zdrowie +7.7k%. (+100%)")!!,.001)
  assertEquals(9600.0 to 8200.0,WarPreparationModel.bonuses("+9.6k% Obrażenia i +8.2k% Zdrowie"))
  assertNull(WarPreparationModel.bonuses("Brak danych"))
  assertNull(WarPreparationModel.bonus("Nieczytelna wartość (+100%)"))
 }
 @Test fun rosterParsesNamesAndPowerWithoutRanksOrOnlineTimes() {
  val text="DzikiRoman ♂\n2.61b Lider\n3 min temu\nXlaWoK ♂\n1.41b Dowódca\nOnline\nR4 45\nMiklasz\n1.64b R4"
  val rows=WarPreparationModel.members(text)
  assertEquals(listOf("DzikiRoman","XlaWoK","Miklasz"),rows.map {it.name})
  assertEquals(2610000000.0,rows[0].power,.001)
  assertEquals(49,WarPreparationModel.memberCount("Członkowie: 49/50"))
  assertNull(WarPreparationModel.memberCount("Poz. 49/50"))
 }
 @Test fun researchRequiresTheCorrectWarNodeAndRivalStatsUseTheirLabels() {
  assertEquals(8700.0,WarPreparationModel.researchBonus("Obrażenia Wojny Klanów +8.7k% (+100%)",false)!!,.001)
  assertNull(WarPreparationModel.researchBonus("Mistrzostwo broni +100%",false))
  assertNull(WarPreparationModel.researchBonus("Zdrowie Wojny Klanów +7.7k%",false))
  assertEquals(9600.0 to 8200.0,WarPreparationModel.bonuses("+8.2k% Zdrowie i +9.6k% Obrażenia"))
  assertNull(WarPreparationModel.bonuses("+9.6k% i +8.2k%"))
 }
 @Test fun researchAllowsOcrSpacesAndMissingPlusButRejectsAmbiguousValues() {
  assertEquals(8700.0,WarPreparationModel.researchBonus("Obrażenia Wojny Klanów + 8.7 k% (+100%)",false)!!,.001)
  assertEquals(7700.0,WarPreparationModel.researchBonus("Zdrowie Wojny Klanów 7,7k%",true)!!,.001)
  assertNull(WarPreparationModel.researchBonus("Zdrowie Wojny Klanów +7k% +8k%",true))
  assertNull(WarPreparationModel.researchBonus("Obrażenia Wojny Klanów +8k%",true))
 }
 @Test fun rowOcrAllowsNameAndPowerOnTheSameLineWithoutBorrowingThePreviousPlayer() {
  val members=WarPreparationModel.members("GraczA ♂ 123m R4\nOnline\n456m R4\nGraczB\n789m")
  assertEquals(listOf(WarMember("GraczA",123e6),WarMember("GraczB",789e6)),members)
 }
 @Test fun clanCountReadsCurrentMembersAndRejectsConflictingHeaders() {
  assertEquals(49,WarPreparationModel.memberCount("Członkowie: 49/50"))
  assertEquals(31,WarPreparationModel.memberCount("Członkowie: 31 / 50"))
  assertEquals(8,WarPreparationModel.memberCount("Members 8/25"))
  assertNull(WarPreparationModel.memberCount("Członkowie 49/50 Members 50/50"))
  assertNull(WarPreparationModel.memberCount("Członkowie 51/50"))
 }
}
