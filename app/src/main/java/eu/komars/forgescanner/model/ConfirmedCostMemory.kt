package eu.komars.forgescanner.model
import android.content.Context
import eu.komars.forgescanner.vision.OcrCandidate
object ConfirmedCostPolicy {
 fun choose(previous:Long?,candidates:List<OcrCandidate>):OcrCandidate? {
  if(previous==null)return null
  val exact=candidates.filter {!it.approximate && it.field?.isCost==true}
  val match=exact.filter {it.value==previous && it.confidence>=.75f}.maxByOrNull {it.confidence} ?: return null
  if(exact.any {it.value!=previous && it.confidence>=.95f})return null
  return match
 }
}
class ConfirmedCostMemory(context:Context) {
 private val prefs=context.getSharedPreferences("confirmed_resource_costs_v1",0)
 fun remember(account:String,values:Map<ResourceField,Long>) {val e=prefs.edit();values.filterKeys {it.isCost}.forEach {(k,v)->e.putLong("$account:${k.name}",v).putLong("$account:${k.name}:time",System.currentTimeMillis())};e.apply()}
 fun get(account:String,field:ResourceField):Long? {val k="$account:${field.name}";val age=System.currentTimeMillis()-prefs.getLong("$k:time",0);return if(age in 0..604800000 && prefs.contains(k))prefs.getLong(k,0)else null}
}
