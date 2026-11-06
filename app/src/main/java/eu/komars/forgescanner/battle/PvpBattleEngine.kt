package eu.komars.forgescanner.battle

import kotlin.random.Random
import kotlin.math.abs

/** Explicit experimental timing assumptions; wiki does not establish these timings. */
data class PvpRules(val seconds:Double=60.0,val meleeApproach:Double=4.0,val firstSkill:Double=.25,val droneInterval:Double=1.0) {
    init { require(seconds in 1.0..120.0 && meleeApproach in 0.0..10.0 && firstSkill in 0.0..10.0 && droneInterval in .1..10.0) }
}
object PvpBattleEngine {
    fun fifthAttackBonus(p:BattleProfile):Double = p.bonus(Bonus.FIFTH_ATTACK).takeIf {it>0}
        ?: if(p.fairy=="LORA" && p.fairyLevel in 1..20)(30.0+10.0*(p.fairyLevel-1))/100 else 0.0
    fun hpMultiplier(p:BattleProfile)=1.0+.5*p.pets+.5*p.skills.size+if(p.mounted) 2.0 else 0.0
    fun skillScale(p:BattleProfile)=(1+p.bonus(Bonus.DAMAGE))*(1+p.bonus(Bonus.SKILL_DAMAGE))*(1+p.skillMastery/100)
    fun skillHealthScale(p:BattleProfile)=(1+p.bonus(Bonus.HEALTH))*(1+p.bonus(Bonus.SKILL_DAMAGE))*(1+p.skillMastery/100)
    fun cycle(p:BattleProfile,s:BattleSkill)=s.duration+cooldown(p,s)
    fun cooldown(p:BattleProfile,s:BattleSkill)=maxOf(.1,s.cooldown*(1-p.bonus(Bonus.SKILL_COOLDOWN).coerceIn(0.0,.99)))
    fun simulate(player:BattleProfile,opponent:BattleProfile,pw:WarBonus=WarBonus(),ow:WarBonus=WarBonus(),n:Int=1000,random:Random=Random.Default,applyStatic:Boolean=false,rules:PvpRules=PvpRules(),shouldStop:()->Boolean={false}):BattleResult {
        require(n in 1..10000)
        val seasonal=listOf(Bonus.REFLECT)
        val unsupported=seasonal.filter {player.bonus(it)>0 || opponent.bonus(it)>0}
        require(unsupported.isEmpty()) {"Wykryto bonusy wróżki: ${unsupported.joinToString {it.label}}. Zapisano ich wartości, ale zasady działania nowego sezonu wymagają potwierdzenia. Symulacja nie uwzględniająca tych bonusów byłaby niewiarygodna."}
        val ps=listOf(player,opponent);val wars=listOf(pw,ow)
        ps.forEach { require(it.damage.isFinite() && it.damage>0 && it.health.isFinite() && it.health>0 && it.pets in 0..3 && it.skills.size<=3 && it.skills.map { s -> s.type }.distinct().size==it.skills.size && it.skillMastery.isFinite() && it.skillMastery in 0.0..100000.0);require(it.bonuses.values.all { v -> v.isFinite() && v in 0.0..100000.0 }) }
        wars.forEach { require(it.damage.isFinite() && it.health.isFinite() && it.damage in 0.0..100000.0 && it.health in 0.0..100000.0) }
        val multiplier=maxOf(hpMultiplier(player),hpMultiplier(opponent))
        val base=DoubleArray(2) { i -> ps[i].health*(1+wars[i].health/100)*(if(applyStatic) 1+ps[i].bonus(Bonus.HEALTH) else 1.0) }
        var wins=0;var losses=0;var draws=0
        repeat(n) {
            if(shouldStop())throw java.util.concurrent.CancellationException()
            val hp=DoubleArray(2) { base[it]*multiplier };val maxHp=hp.copyOf()
            val attacks=DoubleArray(2) { BattleEngine.interval(ps[it].bonus(Bonus.SPEED))+if(!ps[it].ranged && ps[1-it].ranged) rules.meleeApproach else 0.0 }
            val attackCounts=IntArray(2)
            val casts=Array(2) { i -> DoubleArray(ps[i].skills.size) { rules.firstSkill } }
            val expires=Array(2) { i -> DoubleArray(ps[i].skills.size) { -1.0 } }
            val drone=Array(2) { i -> DoubleArray(ps[i].skills.size) { Double.POSITIVE_INFINITY } }
            var regen=1.0;var time=0.0
            val addedDamage=DoubleArray(2);val addedHp=DoubleArray(2);val dealt=DoubleArray(2);val healed=DoubleArray(2)
            var ticks=0
            while(time<=rules.seconds+1e-7 && hp.all { it>0 }) {
                if(++ticks%64==0&&shouldStop())throw java.util.concurrent.CancellationException()
                addedDamage.fill(0.0);addedHp.fill(0.0);dealt.fill(0.0);healed.fill(0.0)
                for(i in 0..1) {
                    val p=ps[i];val scale=skillScale(p)
                    p.skills.forEachIndexed { j,e ->
                        val s=e.type
                        if(time+1e-7>=casts[i][j]) {
                            casts[i][j]=time+cycle(p,s)
                            expires[i][j]=time+s.duration
                            when(s.kind) {
                                SkillKind.HIT -> dealt[1-i]+=s.damage(e.level)*scale*(1+wars[i].damage/100)
                                SkillKind.BUFF -> healed[i]+=s.health(e.level)*skillHealthScale(p)*multiplier*(1+wars[i].health/100)
                                SkillKind.DRONE -> drone[i][j]=time
                            }
                        }
                        if(time<expires[i][j]-1e-7) {
                            if(s.kind==SkillKind.BUFF) {
                                addedDamage[i]+=s.damage(e.level)*scale*(1+wars[i].damage/100)
                                addedHp[i]+=s.health(e.level)*skillHealthScale(p)*multiplier*(1+wars[i].health/100)
                            } else if(s.kind==SkillKind.DRONE && time+1e-7>=drone[i][j]) {
                                dealt[1-i]+=s.damage(e.level)*scale*(1+wars[i].damage/100);drone[i][j]=time+rules.droneInterval
                            }
                        }
                    }
                    maxHp[i]=base[i]*multiplier+addedHp[i]
                    hp[i]=minOf(hp[i],maxHp[i])
                }
                if(time+1e-7>=regen) {
                    for(i in 0..1) healed[i]+=base[i]*ps[i].bonus(Bonus.REGEN)
                    regen+=1.0
                }
                for(i in 0..1) if(time+1e-7>=attacks[i]) {
                    attackCounts[i]++
                    val p=ps[i];val double=random.nextDouble()<p.bonus(Bonus.DOUBLE).coerceIn(0.0,1.0)
                    repeat(if(double) 2 else 1) {
                        var d=BattleEngine.hit(p,wars[i],applyStatic)+addedDamage[i]
                        if(random.nextDouble()<p.bonus(Bonus.CRIT).coerceIn(0.0,1.0)) d*=(1.2+p.bonus(Bonus.CRIT_DAMAGE))*(1-ps[1-i].bonus(Bonus.CRIT_REDUCTION).coerceIn(0.0,.70))
                        val fifth=fifthAttackBonus(p)
                        if(attackCounts[i]%5==0)d*=1+fifth
                        d*=1-ps[1-i].bonus(Bonus.BLOCK).coerceIn(0.0,1.0)
                        dealt[1-i]+=d;healed[i]+=d*p.bonus(Bonus.STEAL)
                    }
                    attacks[i]=time+BattleEngine.interval(p.bonus(Bonus.SPEED))+if(double) { if(p.bonus(Bonus.SPEED)<1) .2 else .1 } else 0.0
                }
                // Skill hits intentionally do not inherit weapon crit/double/lifesteal/block: unverified mechanics.
                for(i in 0..1) hp[i]=minOf(maxHp[i],hp[i]+healed[i])-dealt[i]
                time+=.05
            }
            val result=when {
                hp[0]<=0 && hp[1]<=0 -> 0
                hp[1]<=0 -> 1
                hp[0]<=0 -> -1
                abs(hp[0]/maxHp[0]-hp[1]/maxHp[1])<1e-12 -> 0
                hp[0]/maxHp[0]>hp[1]/maxHp[1] -> 1
                else -> -1
            }
            when(result) { 1 -> wins++;-1 -> losses++;else -> draws++ }
        }
        return BattleResult(wins,losses,draws,n)
    }
}
