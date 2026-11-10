package eu.komars.forgescanner.model

/** Screen identity is separate from per-phone button geometry. No purchasing actions belong here. */
object GameNavigationGraph {
    enum class Screen { UNKNOWN, HOME, PROFILE, DETAILS, MOUNTS, RESEARCH, SKILLS, PETS, TECHNOLOGY, TREE, RESEARCH_DETAILS, CLAN, WAR, ROSTER, RIVAL_BONUS, WAR_RANKING }
    enum class Action { OPEN_MEMBER_PROFILE, CLOSE_MEMBER_PROFILE, OPEN_PROFILE, CLOSE_PROFILE, OPEN_DETAILS, CLOSE_DETAILS, OPEN_MOUNTS, CLOSE_MOUNTS, OPEN_RESEARCH, SKILLS, PETS, TECHNOLOGY, CLAN_TECH, OPEN_RESEARCH_DETAILS, CLOSE_RESEARCH_DETAILS, CLOSE_RESEARCH, OPEN_CLAN, WAR, OPEN_ROSTER, CLOSE_ROSTER, OPEN_RIVAL_BONUS, CLOSE_RIVAL_BONUS, CLOSE_CLAN, OPEN_WAR_RANKING, CLOSE_WAR_RANKING }
    data class Transition(val from:Set<Screen>,val to:Screen)
    private val research=setOf(Screen.RESEARCH,Screen.SKILLS,Screen.PETS,Screen.TECHNOLOGY,Screen.TREE)
    val routes:Map<Action,Transition> = mapOf(
        Action.OPEN_MEMBER_PROFILE to Transition(setOf(Screen.ROSTER),Screen.PROFILE),
        Action.CLOSE_MEMBER_PROFILE to Transition(setOf(Screen.PROFILE),Screen.ROSTER),
        Action.OPEN_PROFILE to Transition(setOf(Screen.HOME),Screen.PROFILE),
        Action.CLOSE_PROFILE to Transition(setOf(Screen.PROFILE),Screen.HOME),
        Action.OPEN_DETAILS to Transition(setOf(Screen.PROFILE),Screen.DETAILS),
        Action.CLOSE_DETAILS to Transition(setOf(Screen.DETAILS),Screen.PROFILE),
        Action.OPEN_MOUNTS to Transition(setOf(Screen.HOME),Screen.MOUNTS),
        Action.CLOSE_MOUNTS to Transition(setOf(Screen.MOUNTS),Screen.HOME),
        Action.OPEN_RESEARCH to Transition(setOf(Screen.HOME),Screen.RESEARCH),
        Action.SKILLS to Transition(research,Screen.SKILLS),
        Action.PETS to Transition(research,Screen.PETS),
        Action.TECHNOLOGY to Transition(research,Screen.TECHNOLOGY),
        Action.CLAN_TECH to Transition(setOf(Screen.TECHNOLOGY),Screen.TREE),
        Action.OPEN_RESEARCH_DETAILS to Transition(setOf(Screen.TREE),Screen.RESEARCH_DETAILS),
        Action.CLOSE_RESEARCH_DETAILS to Transition(setOf(Screen.RESEARCH_DETAILS),Screen.TREE),
        Action.CLOSE_RESEARCH to Transition(research,Screen.HOME),
        Action.OPEN_CLAN to Transition(setOf(Screen.HOME),Screen.CLAN),
        Action.WAR to Transition(setOf(Screen.CLAN,Screen.WAR),Screen.WAR),
        Action.OPEN_ROSTER to Transition(setOf(Screen.WAR),Screen.ROSTER),
        Action.CLOSE_ROSTER to Transition(setOf(Screen.ROSTER),Screen.WAR),
        Action.OPEN_RIVAL_BONUS to Transition(setOf(Screen.WAR),Screen.RIVAL_BONUS),
        Action.CLOSE_RIVAL_BONUS to Transition(setOf(Screen.RIVAL_BONUS),Screen.WAR),
        Action.CLOSE_CLAN to Transition(setOf(Screen.CLAN,Screen.WAR),Screen.HOME),
        Action.OPEN_WAR_RANKING to Transition(setOf(Screen.WAR),Screen.WAR_RANKING),
        Action.CLOSE_WAR_RANKING to Transition(setOf(Screen.WAR_RANKING),Screen.WAR)
    )
    class Cursor {
        var screen=Screen.UNKNOWN;private set
        fun observe(value:Screen) {screen=value}
        fun reset() {screen=Screen.UNKNOWN}
        fun allows(action:Action)=screen==Screen.UNKNOWN || screen in routes.getValue(action).from
        /** Called only after a successful gesture. A failed/cancelled gesture never advances this cursor. */
        fun completed(action:Action) {check(allows(action));screen=routes.getValue(action).to}
    }
    fun resourceAction(name:String):Action? = when(name) {
        "OPEN_MOUNTS"->Action.OPEN_MOUNTS;"CLOSE_MOUNTS"->Action.CLOSE_MOUNTS
        "OPEN_RESEARCH"->Action.OPEN_RESEARCH;"OPEN_SKILLS"->Action.SKILLS
        "OPEN_PETS"->Action.PETS;"OPEN_TECH"->Action.TECHNOLOGY;"CLOSE_RESEARCH"->Action.CLOSE_RESEARCH;else->null
    }
    fun resourceScreen(screen:Screen):GameScreenKind = when(screen) {
        Screen.HOME->GameScreenKind.HOME;Screen.MOUNTS->GameScreenKind.MOUNTS
        Screen.SKILLS->GameScreenKind.SKILLS;Screen.PETS->GameScreenKind.PETS
        Screen.TECHNOLOGY,Screen.TREE->GameScreenKind.TECHNOLOGY;else->GameScreenKind.UNKNOWN
    }
    fun fromResource(screen:GameScreenKind):Screen = when(screen) {
        GameScreenKind.HOME->Screen.HOME;GameScreenKind.MOUNTS->Screen.MOUNTS
        GameScreenKind.SKILLS->Screen.SKILLS;GameScreenKind.PETS->Screen.PETS
        GameScreenKind.TECHNOLOGY->Screen.TECHNOLOGY;else->Screen.UNKNOWN
    }
    fun warAction(key:String):Action? = when(key) {
        "memberAvatar"->Action.OPEN_MEMBER_PROFILE;"memberProfileClose"->Action.CLOSE_MEMBER_PROFILE;
        "science"->Action.OPEN_RESEARCH;"technology"->Action.TECHNOLOGY;"clanTechnologyCategory"->Action.CLAN_TECH
        "damageNode","healthNode","clanTechNode"->Action.OPEN_RESEARCH_DETAILS;"researchClose"->Action.CLOSE_RESEARCH_DETAILS
        "clan"->Action.OPEN_CLAN;"warTab"->Action.WAR;"ourFlag","enemyFlag"->Action.OPEN_ROSTER
        "rosterClose"->Action.CLOSE_ROSTER;"rivalInfo"->Action.OPEN_RIVAL_BONUS;"infoOk"->Action.CLOSE_RIVAL_BONUS;"ourMvp","enemyMvp"->Action.OPEN_WAR_RANKING;"rankingClose"->Action.CLOSE_WAR_RANKING;else->null
    }
}
