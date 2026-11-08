package eu.komars.forgescanner.battle

/** Recovery never calibrates a generic close field on an unrecognised frame. */
object WarReturnPlan {
 sealed class Step {object Home:Step();object Wait:Step();data class Close(val key:String):Step()}
 fun decide(states:Set<String>,fields:Set<String>):Step {
  if("home" in states)return Step.Home
  val key=when {
   "roster" in states->"rosterClose"
   "bonus" in states->"infoOk"
   "researchModal" in states->"researchClose"
   states.any {it in setOf("researchTabs","tree","categories","war","clan","profile","modal")}->"mainClose"
   else->return Step.Wait
  }
  return if(key in fields)Step.Close(key)else Step.Wait
 }
}
