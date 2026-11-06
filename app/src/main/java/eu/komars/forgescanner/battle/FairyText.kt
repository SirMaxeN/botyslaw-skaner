package eu.komars.forgescanner.battle
internal object FairyText {
 fun type(text:String)=Regex("\\b(lora|mira|tira)\\b").find(BattleText.normalize(text))?.value?.uppercase()
 fun firstPercent(text:String):Double?=Regex("[+]?\\s*([0-9]+(?:[.,][0-9]+)?)\\s*%")
  .find(text.replace(Regex("\\([^)]*\\)"),""))?.groupValues?.get(1)?.replace(',','.')?.toDoubleOrNull()
 fun current(text:String):Double? {
  val n=BattleText.normalize(text)
  val marker="aktualny bonus"
  return if(n.contains(marker))firstPercent(n.substringAfter(marker)) else null
 }
}
