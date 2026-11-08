package eu.komars.forgescanner.battle

data class BattleOcrLine(val text:String,val left:Int,val top:Int,val right:Int,val bottom:Int)
/** ML Kit block order can separate percentages from names; rebuild rows using their positions. */
object BattleOcrRows {
    fun text(lines:List<BattleOcrLine>):String {
        val rows=mutableListOf<MutableList<BattleOcrLine>>()
        for(line in lines.filter { it.text.isNotBlank() }.sortedBy { (it.top+it.bottom)/2 }) {
            val cy=(line.top+line.bottom)/2.0
            val row=rows.lastOrNull()?.takeIf { existing ->
                val other=existing.first();val otherY=(other.top+other.bottom)/2.0
                kotlin.math.abs(cy-otherY)<=maxOf(line.bottom-line.top,other.bottom-other.top)*.45
            }
            if(row==null) rows+=mutableListOf(line) else row+=line
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }
}
