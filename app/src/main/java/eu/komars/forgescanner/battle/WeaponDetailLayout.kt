package eu.komars.forgescanner.battle

import eu.komars.forgescanner.model.PixelRegion

/** The item is dismissed by tapping outside its panel, never by the profile's red X. */
object WeaponDetailLayout {
    fun readingArea(panel:PixelRegion,damage:PixelRegion,width:Int,height:Int):PixelRegion? {
        if(!valid(panel,width,height) || !valid(damage,width,height) || damage.top<=panel.top ||
            damage.left<panel.left || damage.right>panel.right || damage.bottom>panel.bottom) return null
        val padding=maxOf(2,width/150)
        return PixelRegion(panel.left+padding,panel.top+padding,panel.right-padding,damage.top-padding)
            .takeIf { it.width>=16 && it.height>=16 }
    }
    fun outsideArea(panel:PixelRegion,width:Int,height:Int):PixelRegion? {
        if(!valid(panel,width,height)) return null
        val size=maxOf(8,width/30);val margin=maxOf(2,width/100)
        val cy=(panel.top+panel.bottom)/2
        val y=(cy-size/2).coerceIn(margin,(height-size-margin).coerceAtLeast(margin))
        // Side margins avoid both the item's contents and the red profile-close control below it.
        if(panel.left>=size+2*margin) return PixelRegion(margin,y,margin+size,y+size)
        if(width-panel.right>=size+2*margin) return PixelRegion(width-margin-size,y,width-margin,y+size)
        val x=(width-size)/2
        if(height-panel.bottom>=size+4*margin) return PixelRegion(x,panel.bottom+2*margin,x+size,panel.bottom+2*margin+size)
        if(panel.top>=size+4*margin) return PixelRegion(x,panel.top-2*margin-size,x+size,panel.top-2*margin)
        return null
    }
    private fun valid(q:PixelRegion,w:Int,h:Int)=q.left>=0 && q.top>=0 && q.right<=w && q.bottom<=h && q.width>0 && q.height>0
}
