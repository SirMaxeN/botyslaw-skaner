package eu.komars.forgescanner.battle

/** Profile nicknames are white; the adjacent gender marker is cyan. No nickname is hard-coded. */
object PlayerNameRegion {
    fun rightEdge(width:Int,height:Int,pixels:IntArray):Int {
        if(width<8 || height<3 || pixels.size.toLong()!=width.toLong()*height) return width
        var textSeen=false
        for(x in 0 until width) {
            var white=0;var cyan=0
            for(y in 0 until height) {
                val c=pixels[y*width+x];val r=c ushr 16 and 255;val g=c ushr 8 and 255;val b=c and 255
                if(minOf(r,g,b)>180) white++
                if(r<115 && g>140 && b>155 && b>r+55) cyan++
            }
            if(white>=2) textSeen=true
            if(textSeen && x>=8 && cyan>=2) return (x-2).coerceAtLeast(8)
        }
        return width
    }
}
