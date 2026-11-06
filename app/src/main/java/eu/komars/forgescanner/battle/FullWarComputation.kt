package eu.komars.forgescanner.battle
import org.json.*
import java.util.concurrent.CancellationException
import kotlin.random.Random

internal class FullWarComputation(private val store:FullWarStore) {
 fun run(ours:List<WarMember>,enemies:List<WarMember>,profiles:(Boolean,WarMember)->JSONObject?,own:WarBonus,rival:WarBonus,cancelled:()->Boolean,progress:(Int,Int)->Unit):JSONObject {
  val a=ours.map {profiles(false,it)};val b=enemies.map {profiles(true,it)}
  val results=Array(a.size){arrayOfNulls<JSONObject>(b.size)};val probabilities=Array(a.size){DoubleArray(b.size){Double.NaN}}
  val digestA=a.map {it?.let {FullWarStore.hash(it.toString())}};val digestB=b.map {it?.let {FullWarStore.hash(it.toString())}}
  fun compute(i:Int,j:Int,n:Int):JSONObject {
   if(cancelled())throw CancellationException()
   val key=FullWarStore.hash("${FullWarStore.MODEL}|${digestA[i]}|${digestB[j]}|$own|$rival")
   store.pair(key)?.takeIf {it.optInt("trials")>=n||it.has("error")}?.let {return it}
   val out=JSONObject().put("savedAt",System.currentTimeMillis())
   if(a[i]==null||b[j]==null)return out.put("error","Niekompletny profil gracza.")
   try {
    for(profile in listOf(a[i]!!,b[j]!!)) {
     val fairy=profile.optJSONObject("fairy")
     require(fairy?.optString("type")!="TIRA"||(fairy.has("present")&&!fairy.optBoolean("present"))) {"Wróżka Tira: zasady odbicia niepotwierdzone. Brak wiarygodnej oceny."}
    }
    val result=PvpBattleEngine.simulate(FullProfileCodec.profile(a[i]!!),FullProfileCodec.profile(b[j]!!),own,rival,n,Random(key.hashCode()),shouldStop=cancelled)
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
  return JSONObject().put("model",FullWarStore.MODEL).put("matrix",matrix).put("complete_ours",a.count {it!=null}).put("complete_enemies",b.count {it!=null}).put("experimental",true)
 }
}
