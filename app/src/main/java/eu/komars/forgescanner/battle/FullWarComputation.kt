package eu.komars.forgescanner.battle
import org.json.*
import java.util.concurrent.CancellationException
import kotlin.random.Random

/** Clan war battles last 120 s (GuildWarConfig/PvpBaseConfig GuildWarBattleMatchTimerSeconds, game data 2026-10-08); arena is 60 s. */
private val WAR_RULES=PvpRules(seconds=120.0)

internal class FullWarComputation(private val store:FullWarStore) {
 fun run(ours:List<WarMember>,enemies:List<WarMember>,profiles:(Boolean,WarMember)->JSONObject?,own:WarBonus,rival:WarBonus,cancelled:()->Boolean,progress:(Int,Int)->Unit):JSONObject {
  val a=ours.map {profiles(false,it)};val b=enemies.map {profiles(true,it)}
  val results=Array(a.size){arrayOfNulls<JSONObject>(b.size)};val probabilities=Array(a.size){DoubleArray(b.size){Double.NaN}}
  val digestA=a.map {it?.let {FullWarStore.hash(it.toString())}};val digestB=b.map {it?.let {FullWarStore.hash(it.toString())}}
  fun compute(i:Int,j:Int,n:Int):JSONObject {
   if(cancelled())throw CancellationException()
   val key=FullWarStore.hash("${FullWarStore.MODEL}|war120|${digestA[i]}|${digestB[j]}|$own|$rival")
   store.pair(key)?.takeIf {it.optInt("trials")>=n||it.has("error")}?.let {return it}
   val out=JSONObject().put("savedAt",System.currentTimeMillis())
   if(a[i]==null||b[j]==null)return out.put("error","Niekompletny profil gracza.")
   try {
    // Season 2026-11 Tira converts health into the Damage multiplier, already in the displayed damage.
    val result=PvpBattleEngine.simulate(FullProfileCodec.profile(a[i]!!),FullProfileCodec.profile(b[j]!!),own,rival,n,Random(key.hashCode()),rules=WAR_RULES,shouldStop=cancelled)
    out.put("wins",result.wins).put("losses",result.losses).put("draws",result.draws).put("trials",result.count)
   }catch(e:CancellationException){throw e}catch(e:IllegalArgumentException){out.put("error",e.message?:"Nieobsługiwany efekt.")}
   store.pair(key,out);return out
  }
  var done=0;val total=ours.size*enemies.size
  for(i in a.indices)for(j in b.indices) {val result=compute(i,j,128);results[i][j]=result;if(!result.has("error"))probabilities[i][j]=result.getInt("wins").toDouble()/result.getInt("trials");progress(++done,total)}
  val main=WarAssignment.best(probabilities)
  val refine=a.indices.flatMap {i->(b.indices.filter {probabilities[i][it].isFinite()}.sortedByDescending {probabilities[i][it]}.take(4)+listOf(main[i]).filter {it>=0}).distinct().map {i to it}}
  for((at,pair) in refine.withIndex()) {val(i,j)=pair;val result=compute(i,j,1024);results[i][j]=result;if(!result.has("error"))probabilities[i][j]=result.getInt("wins").toDouble()/result.getInt("trials");progress(total+at+1,total+refine.size)}
  // The global assignment can change after refinement; refine its new primary targets too.
  for((i,j) in WarAssignment.best(probabilities).withIndex())if(j>=0)results[i][j]=compute(i,j,1024)
  val matrix=JSONArray();for(i in a.indices)for(j in b.indices)matrix.put(JSONObject(results[i][j]!!.toString()).put("ally",i).put("enemy",j))
  val hint=runCatching {techHint(a,b,enemies,probabilities,own,rival,cancelled)}.getOrElse {if(it is CancellationException)throw it;null}
  return JSONObject().put("model",FullWarStore.MODEL).put("matrix",matrix).put("complete_ours",a.count {it!=null}).put("complete_enemies",b.count {it!=null}).put("experimental",true)
   .apply {if(hint!=null)put("tech_hint",hint)}
 }
 /**
  * Rivals nobody beats (best simulated chance < 50%): how many clan-tech levels of war Damage, Health or both
  * (+100% each per level, max 100 levels — forgemaster.pro clan tech, game 2.11) our best three attackers would need.
  * Cheap bounded search (256 trials per probe), cached like the main matrix.
  */
 private fun techHint(a:List<JSONObject?>,b:List<JSONObject?>,enemies:List<WarMember>,p:Array<DoubleArray>,own:WarBonus,rival:WarBonus,cancelled:()->Boolean):JSONObject? {
  val damageLevel=(own.damage/TECH_STEP).toInt().coerceIn(0,TECH_MAX);val healthLevel=(own.health/TECH_STEP).toInt().coerceIn(0,TECH_MAX)
  val best=b.indices.map {j->a.indices.mapNotNull {i->p[i][j].takeIf {v->v.isFinite()}}.maxOrNull()}
  val beaten=best.count {it!=null&&it>=.5}
  val hard=b.indices.filter {best[it]!=null&&best[it]!!<.5}.sortedByDescending {best[it]!!}.take(HINT_ENEMIES)
  if(hard.isEmpty())return null
  fun chance(i:Int,j:Int,bonus:WarBonus):Double {
   if(cancelled())throw CancellationException()
   val key=FullWarStore.hash("${FullWarStore.MODEL}|war120|hint|${FullWarStore.hash(a[i].toString())}|${FullWarStore.hash(b[j].toString())}|$bonus|$rival")
   val saved=store.pair(key)?.takeIf {it.has("wins")}
   val wins=saved?.optInt("wins")?:PvpBattleEngine.simulate(FullProfileCodec.profile(a[i]!!),FullProfileCodec.profile(b[j]!!),bonus,rival,HINT_TRIALS,Random(key.hashCode()),rules=WAR_RULES,shouldStop=cancelled).wins.also {store.pair(key,JSONObject().put("wins",it).put("trials",HINT_TRIALS).put("savedAt",System.currentTimeMillis()))}
   return wins.toDouble()/HINT_TRIALS
  }
  /** Fewest extra levels (1..room) at which one of the attackers wins ≥50%; null when even the maximum is not enough. */
  fun needed(j:Int,room:Int,bonus:(Int)->WarBonus):Int? {
   if(room<=0)return null
   val attackers=a.indices.filter {p[it][j].isFinite()}.sortedByDescending {p[it][j]}.take(3)
   if(attackers.isEmpty())return null
   fun wins(levels:Int)=attackers.any {chance(it,j,bonus(levels))>=.5}
   if(!wins(room))return null
   var low=0;var high=room
   while(high-low>1){val mid=(low+high)/2;if(wins(mid))high=mid else low=mid}
   return high
  }
  val rows=JSONArray()
  for(j in hard) {
   val damageRoom=TECH_MAX-damageLevel;val healthRoom=TECH_MAX-healthLevel
   val damage=needed(j,damageRoom) {WarBonus(own.damage+TECH_STEP*it,own.health)}
   val health=needed(j,healthRoom) {WarBonus(own.damage,own.health+TECH_STEP*it)}
   val both=needed(j,minOf(damageRoom,healthRoom)) {WarBonus(own.damage+TECH_STEP*it,own.health+TECH_STEP*it)}
   rows.put(JSONObject().put("name",enemies[j].name).put("best",best[j]).put("damage",damage?:JSONObject.NULL).put("health",health?:JSONObject.NULL).put("both",both?:JSONObject.NULL))
  }
  return JSONObject().put("damage_level",damageLevel).put("health_level",healthLevel).put("max_level",TECH_MAX).put("step_percent",TECH_STEP)
   .put("beaten",beaten).put("reset_needed",minOf(RESET_DEFEATS,b.size)).put("enemies",rows)
 }
 /** Round-robin inside one clan without war bonuses; each unordered pair once (ally < enemy). */
 fun ranking(members:List<WarMember>,profiles:(WarMember)->JSONObject?,cancelled:()->Boolean,trials:Int=RANKING_TRIALS,progress:(Int,Int)->Unit):JSONObject {
  val p=members.map(profiles);val digest=p.map {it?.let {FullWarStore.hash(it.toString())}}
  val none=WarBonus(0.0,0.0);val matrix=JSONArray();val total=members.size*(members.size-1)/2;var done=0
  for(i in members.indices)for(j in i+1 until members.size) {
   if(cancelled())throw CancellationException()
   val key=FullWarStore.hash("ranking|${FullWarStore.MODEL}|war120|${digest[i]}|${digest[j]}|$trials")
   val cached=if(digest[i]!=null&&digest[j]!=null)store.pair(key)else null
   val out=cached?:JSONObject().put("savedAt",System.currentTimeMillis()).also {out->
    if(p[i]==null||p[j]==null)out.put("error","Niekompletny profil gracza.")
    else try {
     val result=PvpBattleEngine.simulate(FullProfileCodec.profile(p[i]!!),FullProfileCodec.profile(p[j]!!),none,none,trials,Random(key.hashCode()),rules=WAR_RULES,shouldStop=cancelled)
     out.put("wins",result.wins).put("losses",result.losses).put("draws",result.draws).put("trials",result.count)
    }catch(e:CancellationException){throw e}catch(e:IllegalArgumentException){out.put("error",e.message?:"Nieobsługiwany efekt.")}
    if(p[i]!=null&&p[j]!=null)store.pair(key,out)
   }
   matrix.put(JSONObject(out.toString()).apply {remove("savedAt")}.put("ally",i).put("enemy",j));progress(++done,total)
  }
  return JSONObject().put("model",FullWarStore.MODEL).put("experimental",true).put("matrix",matrix).put("complete",p.count {it!=null})
 }
 companion object {
  const val RANKING_TRIALS=512
  // Clan War Damage/Health: +100% per level, max 100 levels; reset after 45 defeated rivals (GuildWarConfig).
  const val TECH_STEP=100.0;const val TECH_MAX=100;const val RESET_DEFEATS=45
  const val HINT_TRIALS=256;const val HINT_ENEMIES=10
 }
}
