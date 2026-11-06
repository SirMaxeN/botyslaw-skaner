package eu.komars.forgescanner.battle
internal object WarResumePolicy {
 fun validCount(candidate:Int?,previous:Int):Int?=candidate?.takeIf {it in 1..50}?:previous.takeIf {it in 1..50}
 fun clanKey(s:String)=WarRosterProof.key(s.replace(Regex("(?i)(?<=_)j$"),""))
 fun sameClan(a:String,b:String)=clanKey(a).isNotEmpty()&&clanKey(a)==clanKey(b)
 fun centerDistance(viewport:Int)=minOf(viewport*.25f,maxOf(viewport*.12f,viewport/6f*1.3f))
 fun safe(box:RosterBox,viewport:Int):Boolean=box.top>=box.height*.20&&box.bottom<=viewport-box.height*.20
}
