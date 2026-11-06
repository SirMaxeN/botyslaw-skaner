package eu.komars.forgescanner.battle

import kotlin.math.abs
import kotlin.math.sqrt

internal data class RosterSavedRow(val visual:RosterVisual,val reading:RosterReading)
internal data class RosterReading(val name:String?,val power:Double?)
internal data class RosterSnapshot(val width:Int,val slot:Int,val nameWidth:Int,val nameHeight:Int,val powerWidth:Int,val powerHeight:Int,val pixels:IntArray)
internal data class RosterObservation(val visual:RosterVisual,val readings:List<RosterReading>,val anchorY:Int?=null,val snapshot:RosterSnapshot?=null)
internal data class RosterRepairTarget(val index:Int,val snapshot:RosterSnapshot)
internal data class RosterVisual(val avatar:IntArray,val name:IntArray,val background:Int,val nameAspect:Double=1.0) {
 fun avatarError(other:RosterVisual):Double {
  var best=255.0
  for(dy in -1..1)for(dx in -1..1) {
   var total=0.0;var count=0
   for(y in 2 until 30)for(x in 2 until 30) {
    val a=avatar[y*32+x];val b=other.avatar[(y+dy)*32+x+dx]
    total+=(abs((a ushr 16 and 255)-(b ushr 16 and 255))+abs((a ushr 8 and 255)-(b ushr 8 and 255))+abs((a and 255)-(b and 255)))/3.0;count++
   };best=minOf(best,total/count)
  };return best
 }
 private fun nameError(other:RosterVisual):Double {
  var best=1.0
  for(dy in -2..2)for(dx in -2..2) {
   var sa=0.0;var sb=0.0;var aa=0.0;var bb=0.0;var ab=0.0;var n=0
   for(y in 2 until 30)for(x in 2 until 126) {
    val a=name[y*128+x].toDouble();val b=other.name[(y+dy)*128+x+dx].toDouble()
    sa+=a;sb+=b;aa+=a*a;bb+=b*b;ab+=a*b;n++
   }
   val denominator=sqrt(maxOf(0.0,n*aa-sa*sa)*maxOf(0.0,n*bb-sb*sb))
   if(denominator>1e-6)best=minOf(best,1.0-(n*ab-sa*sb)/denominator)
  };return best
 }
 /** Background validates the card; both the avatar and the nickname artwork identify it. */
 fun distance(other:RosterVisual):Double? {
  if(avatar.size!=1024||other.avatar.size!=1024||name.size!=4096||other.name.size!=4096)return null
  if(abs(background-other.background)>12 || abs(nameAspect-other.nameAspect)>maxOf(.10,nameAspect*.04))return null
  val avatar=avatarError(other);if(avatar>20)return null
  val name=nameError(other);if(name>.14)return null
  return name+avatar/255.0
 }
}

/** One stable slot per card, aligned by at least two visual neighbours, never keyed by OCR spelling. */
internal class RosterRegistry {
 private class Entry(val visual:RosterVisual) {
  val sources=linkedMapOf<Long,MutableList<RosterReading>>()
  var snapshot:RosterSnapshot?=null;var snapshotSource:Long?=null
  var manual:WarMember?=null
  val names=linkedMapOf<String,Int>();val display=linkedMapOf<String,String>();val powers=linkedMapOf<Double,Int>();val confirmedNames=mutableSetOf<String>();val confirmedPowers=mutableSetOf<Double>()
  private fun <T> winner(values:Map<T,Int>):T? {val sorted=values.entries.sortedByDescending {it.value};val first=sorted.firstOrNull()?:return null;return first.key.takeIf {sorted.size==1||first.value>sorted[1].value}}
  private fun rebuild() {
   names.clear();display.clear();powers.clear();confirmedNames.clear();confirmedPowers.clear()
   for(readings in sources.values) {
    val validNames=readings.mapNotNull {it.name}.filter {it.isNotBlank()}
    winner(validNames.map(BattleText::normalize).groupingBy {it}.eachCount())?.let {key ->
     names[key]=(names[key]?:0)+1;display.putIfAbsent(key,validNames.first {BattleText.normalize(it)==key})
     if(validNames.count {BattleText.normalize(it)==key}>=2)confirmedNames+=key
    }
    winner(readings.mapNotNull {it.power}.filter {it>0&&it.isFinite()}.groupingBy {it}.eachCount())?.let {value ->
     powers[value]=(powers[value]?:0)+1;if(readings.count {it.power==value}>=2)confirmedPowers+=value
    }
   }
  }
  fun add(row:RosterObservation,source:Long) {
   sources[source]=row.readings.toMutableList()
   row.snapshot?.let {snapshot=it;snapshotSource=source}
   rebuild()
  }
  fun repair(readings:List<RosterReading>) {
   // Extra preprocessing of a cached image extends that source, never invents another frame vote.
   val source=snapshotSource?:return;sources[source]?.addAll(readings);rebuild()
  }
  fun partial():RosterReading {
   manual?.let {return RosterReading(it.name,it.power)}
   val name=winner(names)?.takeIf {it in confirmedNames||(names[it]?:0)>=2}
   val power=winner(powers)?.takeIf {it in confirmedPowers||(powers[it]?:0)>=2}
   return RosterReading(name?.let {display.getValue(it)},power)
  }
  fun member():WarMember? {val p=partial();return if(p.name!=null&&p.power!=null)WarMember(p.name,p.power)else null}
 }
 private val entries=mutableListOf<Entry>()
 private var previousStart:Int?=null
 private var currentIndices=emptyList<Int>()
 private var sourceSerial=0L
 val size get()=entries.size
 val values:List<WarMember> get() {
  val all=entries.mapNotNull {it.member()};val counts=all.groupingBy {BattleText.normalize(it.name)}.eachCount()
  return all.filter {counts[BattleText.normalize(it.name)]==1}
 }
 val resolvedCount get()=values.size
 val unresolvedIndices:List<Int> get() {
  val all=entries.map {it.member()};val names=all.filterNotNull().groupingBy {BattleText.normalize(it.name)}.eachCount()
  return all.indices.filter {i->all[i]?.let {names[BattleText.normalize(it.name)]!=1}?:true}
 }
 val repairTargets get()=unresolvedIndices.mapNotNull {i ->entries[i].snapshot?.let {RosterRepairTarget(i,it)}}
 fun partial(index:Int):RosterReading=entries[index].partial()
 fun snapshot(index:Int):RosterSnapshot?=entries[index].snapshot
 fun repair(index:Int,readings:List<RosterReading>) {entries[index].repair(readings)}
 fun setManual(index:Int,name:String,power:Double):Boolean {
  val clean=name.trim()
  if(index !in entries.indices||clean.isBlank()||!clean.any(Char::isLetterOrDigit)||!power.isFinite()||power<=0)return false
  if(entries.indices.any {it!=index&&entries[it].partial().name?.let {n->BattleText.normalize(n)==BattleText.normalize(clean)}==true})return false
  entries[index].manual=WarMember(clean,power);return true
 }
 fun clear(){entries.clear();previousStart=null;sourceSerial=0;currentIndices=emptyList()}
 fun beginPass(){previousStart=null}
 fun visibleIndices(count:Int):List<Int> = if(currentIndices.size==count)currentIndices else previousStart?.let {start->(start until start+count).toList()}?:emptyList()
 fun savedRows()=entries.map {RosterSavedRow(it.visual,it.partial())}
 fun restore(rows:List<RosterSavedRow>,repairDuplicates:Boolean=false) {
  clear()
  for(row in rows) {
   val duplicate=if(repairDuplicates&&row.reading.name!=null&&row.reading.power!=null) {
    entries.singleOrNull {entry ->
     val saved=entry.partial()
     saved.name!=null&&BattleText.normalize(saved.name)==BattleText.normalize(row.reading.name)&&
      saved.power==row.reading.power&&entry.visual.avatarError(row.visual)<=30.0
    }
   } else null
   val entry=duplicate?:Entry(row.visual).also {entries+=it}
   entry.add(RosterObservation(row.visual,listOf(row.reading,row.reading)),++sourceSerial)
  }
 }
 fun confirmedReading(visual:RosterVisual):RosterReading? {
  val match=entries.singleOrNull {it.visual.distance(visual)!=null}?:return null
  val member=match.member()?:return null
  if(entries.count {it.partial().name?.let(BattleText::normalize)==BattleText.normalize(member.name)}!=1)return null
  return RosterReading(member.name,member.power)
 }
 private fun confirmedName(row:RosterObservation):String? {
  val reads=row.readings.mapNotNull {it.name}.map(BattleText::normalize)
  return reads.firstOrNull()?.takeIf {reads.size>=2&&reads.all {name->name==it}}
 }
 /** Full scans span minutes: roles/power can reorder cards while profiles are opened. */
 fun addUnorderedView(rows:List<RosterObservation>,expected:Int,keepPower:(WarMember)->Boolean={false}):Result<Int> {
  if(rows.isEmpty()||expected !in 1..50)return Result.failure(IllegalArgumentException("Brak potwierdzonych kart lub licznika."))
  // Validate the entire frame before mutating saved identities or votes.
  if(rows.indices.any {i->(0 until i).any {j->rows[i].visual.distance(rows[j].visual)!=null}})return Result.failure(IllegalStateException("Ten sam awatar i nick występuje dwa razy w odczycie. Ponów bieżący widok."))
  val planned=mutableListOf<Int>();var added=0
  val observedNames=rows.map(::confirmedName)
  for((at,row) in rows.withIndex()) {
   val name=observedNames[at]
   if(name!=null&&observedNames.count {it==name}>1)
    return Result.failure(IllegalStateException("Dwa wiersze mają ten sam potwierdzony nick."))
   val visual=entries.indices.filter {entries[it].visual.distance(row.visual)!=null}
   val sameName=if(name==null)emptyList() else entries.indices.filter {
    entries[it].partial().name?.let(BattleText::normalize)==name
   }
   val corroborated=sameName.singleOrNull()?.takeIf {entries[it].visual.avatarError(row.visual)<=30.0}
   if((visual.size>1&&corroborated==null)||(corroborated!=null&&visual.isNotEmpty()&&corroborated !in visual)||
      (visual.isEmpty()&&sameName.isNotEmpty()&&corroborated==null))
    return Result.failure(IllegalStateException("Niejednoznaczna tożsamość karty."))
   val index=corroborated?:visual.singleOrNull()?: (entries.size+added++)
   if(index in planned||index>=expected)return Result.failure(IllegalStateException("Nie potwierdzono unikalnych członków."))
   planned+=index
  }
  val source=++sourceSerial
  rows.forEachIndexed {at,row->val index=planned[at];if(index==entries.size)entries+=Entry(row.visual)
   val entry=entries[index]
   val values=row.readings.mapNotNull {it.power}.distinct()
   val member=entry.member()
   if(member!=null&&values.size==1&&values.single()!=member.power) {
    if(keepPower(member)) {
     entry.add(row.copy(readings=row.readings.map {it.copy(power=member.power)}),source)
     return@forEachIndexed
    }
    if(row.readings.count {it.power==values.single()}>=2)entry.sources.clear()
   }
   entry.add(row,source)
  };currentIndices=planned;return Result.success(added)
 }
 /** A lone, uniquely matching tail is enough to move back safely, never enough to append players. */
 fun needsOverlapRecovery(rows:List<RosterObservation>):Boolean {
  if(entries.size<2||rows.size<2)return false
  val first=rows.first().visual
  val scores=entries.mapIndexedNotNull {i,e->e.visual.distance(first)?.let {i to it}}.sortedBy {it.second}
  val best=scores.firstOrNull()?:return false
  return best.first==entries.lastIndex && (scores.size==1||scores[1].second-best.second>=.025)
 }
 fun addView(rows:List<RosterObservation>,expected:Int):Result<Int> {
  currentIndices=emptyList()
  if(rows.isEmpty())return Result.failure(IllegalArgumentException("Brak pełnych pól graczy"))
  val source=++sourceSerial
  if(entries.isEmpty()) {
   if(rows.size>expected)return Result.failure(IllegalStateException("Więcej pól niż członków klanu"))
   rows.forEach {entries+=Entry(it.visual).apply {add(it,source)}};previousStart=0;return Result.success(rows.size)
  }
  data class Alignment(val start:Int,val score:Double)
  val candidates=mutableListOf<Alignment>()
  for(start in 0..maxOf(0,entries.size-1)) {
   if(start+rows.size>expected)continue
   val previous=previousStart
   if(previous!=null && (start<maxOf(0,previous-1) || start>previous+rows.size))continue
   val overlap=minOf(rows.size,entries.size-start)
   if(overlap<minOf(2,expected))continue
   val scores=(0 until overlap).map {entries[start+it].visual.distance(rows[it].visual)}
   // A mismatching card invalidates this offset; partial/omitted rows must not shift all later names.
   if(scores.any {it==null})continue
   candidates+=Alignment(start,scores.filterNotNull().average())
  }
  val sorted=candidates.sortedBy {it.score};val best=sorted.firstOrNull()
   ?:return Result.failure(IllegalStateException("Nie potwierdzono wspólnych awatarów i nicków. Ponawiam ten widok."))
  if(sorted.size>1 && sorted[1].score-best.score<.025)return Result.failure(IllegalStateException("Niejednoznaczne dopasowanie pól graczy. Ponawiam ten widok."))
  val added=maxOf(0,best.start+rows.size-entries.size)
  rows.forEachIndexed {i,row ->val index=best.start+i
   if(index==entries.size)entries+=Entry(row.visual)
   entries[index].add(row,source)
  }
  previousStart=best.start;return Result.success(added)
 }
}
