package eu.komars.forgescanner.model

import eu.komars.forgescanner.automation.AutoScanStep
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class GameScreenKind { HOME, MOUNTS, SKILLS, PETS, TECHNOLOGY, UNKNOWN }
data class GameUiProposal(val region: PixelRegion, val confidence: Float, val reason: String,
    val container: PixelRegion? = null)

/** Language-independent structural proposals for the tested portrait game layout.
 * Coordinates are derived from connected UI components, not saved absolute tap positions.
 * Unknown or conflicting layouts deliberately return no proposal.
 */
class GameUiGeometryDetector(private val width: Int, private val height: Int, private val pixels: IntArray, allowTabletAspect: Boolean = false) {
    private data class Component(val box: PixelRegion, val area: Int) {
        val x get() = (box.left + box.right) / 2.0
        val y get() = (box.top + box.bottom) / 2.0
    }
    private val valid = width >= 100 && height >= width * (if (allowTabletAspect) 1.25 else 1.5) && height <= width * 2.8 &&
        width.toLong() * height <= 600_000 && pixels.size.toLong() == width.toLong() * height
    private fun red(color: Int) = channel(color, 16) > 145 && channel(color, 8) < 155 && channel(color, 0) < 180
    private fun gray(color: Int): Boolean {
        val r = channel(color,16); val g = channel(color,8); val b = channel(color,0)
        return r in 60..210 && abs(r-g)<25 && abs(r-b)<25
    }
    private fun dark(color: Int) = channel(color,16)<95 && channel(color,8)<95 && channel(color,0)<95
    private fun blue(color: Int): Boolean {
        val r=channel(color,16);val g=channel(color,8);val b=channel(color,0)
        return r<90 && g>110 && b>170 && b>g*1.08
    }
    private fun green(color: Int): Boolean {
        val r=channel(color,16);val g=channel(color,8);val b=channel(color,0)
        return g>140 && r<140 && b<150 && g>r*1.3
    }
    private fun orange(color: Int): Boolean {
        val r=channel(color,16);val g=channel(color,8);val b=channel(color,0)
        return r>150 && g in 70..220 && b<130 && r>g*1.10
    }
    private fun cyan(color: Int) = channel(color,8)>130 && channel(color,0)>130 && channel(color,16)<130
    private fun channel(color: Int, shift: Int) = color ushr shift and 255
    private val grays by lazy { if(valid) components(::gray) else emptyList() }
    private val blues by lazy { if(valid) components(::blue) else emptyList() }
    private val darks by lazy { if(valid) components(::dark) else emptyList() }
    private val greens by lazy { if(valid) components(::green) else emptyList() }
    private val oranges by lazy { if(valid) components(::orange) else emptyList() }
    private val close by lazy {
        if (!valid) null else {
            // Close controls belong to bottom chrome, never to inventory artwork.
            val chrome = pixels.copyOf()
            for (y in 0 until height) for (x in 0 until width) {
                if (x < width * .30 || x > width * .70 || y < height * .75) chrome[y * width + x] = 0xff000000.toInt()
            }
            CloseButtonPixelDetector().detect(width, height, chrome)
        }
    }
    private val tabs by lazy { detectTabs() }
    private val mountModal by lazy { detectMountModal() }
    private val summonButtons by lazy { detectSummonButtons() }
    private val ticket by lazy { counter(ticket = true) }
    private val keys by lazy { counter(ticket = false) }
    private val mountCost by lazy { cost(ticket = false) }
    private val skillCost by lazy { cost(ticket = true) }
    private val hammer by lazy { hammerCounter() }
    private val potion by lazy { potionButton() }
    private val mountCard by lazy { equipmentMountCard() }

    private val kind by lazy { classifyScreen() }
    fun screenKind(): GameScreenKind = kind

    private fun classifyScreen(): GameScreenKind {
        if (!valid) return GameScreenKind.UNKNOWN
        // Screen identity comes from chrome. Current items, prices and balances are content.
        if (close != null && tabs == null && mountModal != null && (keys != null || summonButtons.isNotEmpty()))
            return GameScreenKind.MOUNTS
        if (tabs != null) return when (selectedTab()) {
            0 -> GameScreenKind.SKILLS
            1 -> GameScreenKind.PETS
            2 -> GameScreenKind.TECHNOLOGY
            else -> GameScreenKind.UNKNOWN
        }
        return if (close == null && potion != null && homeForgeButtons().isNotEmpty() &&
            !CloseButtonPixelDetector().hasGameplayClose(width,height,pixels))
            GameScreenKind.HOME else GameScreenKind.UNKNOWN
    }

    fun detect(step: AutoScanStep): GameUiProposal? {
        if(!valid) return null
        val kind = screenKind()
        val box = when(step) {
            AutoScanStep.HAMMERS -> hammer.takeIf { kind==GameScreenKind.HOME }
            AutoScanStep.OPEN_MOUNTS -> mountCard.takeIf { kind==GameScreenKind.HOME }
            AutoScanStep.OPEN_RESEARCH -> potion.takeIf { kind==GameScreenKind.HOME }
            AutoScanStep.MOUNT_LIST -> mountViewport().takeIf { kind == GameScreenKind.MOUNTS }
            AutoScanStep.PET_LIST -> petsViewport().takeIf { kind == GameScreenKind.PETS }
            AutoScanStep.MOUNT_KEYS -> keys.takeIf { kind==GameScreenKind.MOUNTS }
            AutoScanStep.MOUNT_COST -> mountCost.takeIf { kind==GameScreenKind.MOUNTS }
            AutoScanStep.SKILL_TICKETS -> ticket.takeIf { kind==GameScreenKind.SKILLS }
            AutoScanStep.SKILL_COST -> skillCost.takeIf { kind==GameScreenKind.SKILLS }
            AutoScanStep.OPEN_SKILLS -> tabs?.takeIf { kind==GameScreenKind.SKILLS || kind==GameScreenKind.PETS || kind==GameScreenKind.TECHNOLOGY }?.let { tab(it,0) }
            AutoScanStep.OPEN_PETS -> tabs?.takeIf { kind==GameScreenKind.SKILLS || kind==GameScreenKind.PETS || kind==GameScreenKind.TECHNOLOGY }?.let { tab(it,1) }
            AutoScanStep.OPEN_TECH -> tabs?.takeIf { kind==GameScreenKind.SKILLS || kind==GameScreenKind.PETS || kind==GameScreenKind.TECHNOLOGY }?.let { tab(it,2) }
            AutoScanStep.CLOSE_MOUNTS -> close.takeIf { kind==GameScreenKind.MOUNTS }
            AutoScanStep.CLOSE_RESEARCH -> close.takeIf { kind==GameScreenKind.SKILLS || kind==GameScreenKind.PETS || kind==GameScreenKind.TECHNOLOGY }
            // Top-bar extras: icon + number (grey pill on the tabs screens, plain text on the home screen).
            AutoScanStep.COINS, AutoScanStep.GEMS, AutoScanStep.SHELLS, AutoScanStep.RED_VIALS, AutoScanStep.GREEN_VIALS ->
                TopCounters.forField(requireNotNull(step.target).field, kind, width, height, pixels)
            else -> null
        } ?: return null
        if (step.isInventory) return GameUiProposal(box, .84f,
            "Rozpoznano cały obszar przewijania, także puste miejsce. Sprawdź ramkę i popraw ją w razie potrzeby.", box)
        val numeric = step.target?.isInventory == false
        return GameUiProposal(box, if(numeric) .86f else .84f,
            if(numeric) "Rozpoznano pojemnik licznika i ikonę; pozostawiono miejsce na dłuższą liczbę."
            else "Rozpoznano układ przycisków i sąsiednie elementy gry. Sprawdź propozycję.",
            if(numeric) box else null)
    }

    private fun mountViewport(): PixelRegion? {
        val panel = mountModal ?: return null
        val button = summonButtons.singleOrNull() ?: return null
        val header = headerZone(false) ?: return null
        val inset = max(3, (width * .008).toInt())
        val top = max(header.bottom, (keys?.bottom ?: header.bottom) + 3)
        val bottom = button.box.top - max(5, (panel.height * .015).toInt())
        val box = PixelRegion(panel.left + inset, top, panel.right - inset, bottom)
        return box.takeIf { it.width >= width * .75 && it.height >= it.width * .65 }
    }

    private fun petsViewport(): PixelRegion? {
        val strip = tabs ?: return null
        if (selectedTab() != 1) return null
        val button = summonButtons.singleOrNull() ?: return null
        val equipment = darks.filter { part ->
            part.box.width >= width * .70 && part.box.height in (height * .025).toInt()..(height * .105).toInt() &&
                part.box.top > height * .30 && part.box.bottom < button.box.top &&
                part.box.bottom < strip.top && abs(part.x - width * .5) < width * .10
        }.singleOrNull() ?: return null
        val headers = grays.filter { part ->
            part.box.top in (height * .035).toInt()..(height * .10).toInt() &&
                part.box.bottom <= height * .13 && part.box.width in (width * .12).toInt()..(width * .30).toInt() &&
                part.box.height in (height * .012).toInt()..(height * .045).toInt()
        }
        val headerBottom = headers.maxOfOrNull { it.box.bottom } ?: return null
        val top = headerBottom + max(5, (width * .055).toInt())
        val side = (width * .035).toInt()
        val bottom = equipment.box.top - max(4, (width * .012).toInt())
        return PixelRegion(side, top, width - side, bottom).takeIf { it.height >= it.width * .65 }
    }

    /** Known summon/forge controls are not navigation targets and must never be auto-tapped. */
    fun forbiddenActionAreas():List<PixelRegion> {
        val kind = screenKind()
        val buttons = when (kind) {
            GameScreenKind.MOUNTS, GameScreenKind.SKILLS, GameScreenKind.PETS -> summonButtons.map { it.box }
            GameScreenKind.TECHNOLOGY -> listOfNotNull(tabs?.let { PixelRegion(0, 0, width, it.top) })
            GameScreenKind.HOME -> homeForgeButtons().map { it.box } + listOfNotNull(hammer)
            else -> summonButtons.map { it.box }
        }
        return buttons.map { box ->
            // Technology content stops at the tab strip: do not expand over safe navigation.
            if (kind == GameScreenKind.TECHNOLOGY) box else clamp(PixelRegion(box.left-4, box.top-4, box.right+4, box.bottom+5))
        }.distinct()
    }

    private fun homeForgeButtons() = blues.filter { button ->
        button.x > width * .60 && button.y in height * .70..height * .87 &&
            mountCard?.let { card -> button.x !in card.left.toDouble()..card.right.toDouble() ||
                button.y !in card.top.toDouble()..card.bottom.toDouble() } != false
    }

    private fun centralButton(button:Component)=button.x in width*.4..width*.6 && button.y in height*.45..height*.84 &&
        button.box.width in (width*.25).toInt()..(width*.48).toInt() &&
        button.box.height in (height*.04).toInt()..(height*.13).toInt() &&
        button.box.width.toDouble()/button.box.height in 1.5..4.0

    private fun detectMountModal(): PixelRegion? {
        val x = close ?: return null
        if (tabs != null) return null
        return components(::white).filter { panel ->
            panel.box.width >= width * .75 && panel.box.height >= height * .35 &&
                panel.box.top in (height * .12).toInt()..(height * .40).toInt() &&
                abs(panel.x - width * .5) <= width * .10 &&
                abs(panel.box.bottom - (x.top + x.bottom) * .5) <= height * .08
        }.maxByOrNull { it.area }?.box
    }

    private fun headerZone(ticket: Boolean): PixelRegion? {
        if (ticket) {
            if (tabs == null) return null
            return PixelRegion(0, 0, (width * .32).toInt(), (height * .13).toInt())
        }
        val panel = mountModal ?: return null
        return PixelRegion(panel.left, panel.top, panel.right,
            (panel.top + panel.height * .16).toInt())
    }

    private fun counter(ticket: Boolean): PixelRegion? {
        val zone = headerZone(ticket) ?: return null
        val markers = components(if (ticket) ::green else ::orange, zone)
        val found = components(::gray, zone).filter { bar ->
            bar.area >= bar.box.width * bar.box.height * .25 &&
                bar.box.width in (width * .10).toInt()..(width * .26).toInt() &&
                bar.box.height in (height * .012).toInt()..(height * .045).toInt() &&
                bar.box.width.toDouble() / bar.box.height in 2.0..7.0 &&
                (if (ticket) bar.x < width * .30 else bar.x in width * .38..width * .65)
        }.mapNotNull { bar ->
            val icon = markers.filter { marker ->
                marker.area >= bar.box.height * bar.box.height * (if (ticket) .2 else .08) &&
                    marker.box.height >= bar.box.height * (if (ticket) .85 else .65) &&
                    marker.box.width >= bar.box.height * (if (ticket) .75 else .3) &&
                    marker.x in bar.box.left - bar.box.height * 1.4..bar.box.left + bar.box.height * .8 &&
                    abs(marker.y - bar.y) <= bar.box.height * .8
            }.maxByOrNull { it.area } ?: return@mapNotNull null
            // The value may be zero, red, occluded or temporarily unreadable. Its shape
            // must not decide whether the screen or the currency container exists.
            val horizontal = max(3, (bar.box.height * .4).toInt())
            val vertical = max(4, (bar.box.height * .6).toInt())
            clamp(PixelRegion(min(bar.box.left, icon.box.left) - horizontal,
                min(bar.box.top, icon.box.top) - vertical,
                bar.box.right + max(horizontal, (bar.box.width * .15).toInt()),
                max(bar.box.bottom, icon.box.bottom) + vertical))
        }
        return found.singleOrNull()
    }

    private fun summonZone(): PixelRegion? {
        tabs?.let { strip ->
            val bounds = when (selectedTab()) {
                0 -> (strip.top - height * .20).toInt() to strip.top - 2
                1 -> (strip.top - height * .35).toInt() to (strip.top - height * .17).toInt()
                else -> return null
            }
            return PixelRegion((width * .22).toInt(), bounds.first.coerceAtLeast(0),
                (width * .78).toInt(), bounds.second)
        }
        val panel = mountModal ?: return null
        val x = close ?: return null
        return PixelRegion((width * .22).toInt(), (panel.bottom - panel.height * .31).toInt(),
            (width * .78).toInt(), min(panel.bottom, x.top))
    }

    private fun detectSummonButtons(): List<Component> {
        val zone = summonZone() ?: return emptyList()
        // An unaffordable summon keeps its geometry but changes blue/white to gray/red.
        return (components(::blue, zone) + components(::gray, zone)).filter(::centralButton)
    }

    private fun cost(ticket: Boolean): PixelRegion? {
        val zone = summonZone() ?: return null
        val markers = components(if (ticket) ::green else ::orange, zone)
        return summonButtons.mapNotNull { button ->
            val rowTop = (button.box.top + button.box.height * .50).toInt()
            val icon = markers.filter { marker ->
                marker.area >= 5 && marker.x in button.box.left + button.box.width * .15..button.x &&
                    marker.y in rowTop.toDouble()..button.box.bottom.toDouble() &&
                    marker.box.height >= button.box.height * .12
            }.maxByOrNull { it.area } ?: return@mapNotNull null
            val digits = PixelRegion(max(icon.box.right + 2, (button.box.left + button.box.width * .40).toInt()),
                rowTop, button.box.right - 3, button.box.bottom - 1)
            if (digits.width < button.box.width * .30 || !numericGlyphs(digits)) return@mapNotNull null
            val inset = max(2, (button.box.width * .04).toInt())
            val top = max((button.box.top + button.box.height * .30).toInt(),
                min(rowTop, icon.box.top) - max(4, (button.box.height * .15).toInt()))
            clamp(PixelRegion(button.box.left + inset, top, button.box.right - inset, button.box.bottom + 3))
        }.singleOrNull()
    }

    private fun selectedTab(): Int? {
        val strip = tabs ?: return null
        val blueArea = (0..2).map { index -> count(tab(strip, index), ::blue) }
        val best = blueArea.indices.maxByOrNull { blueArea[it] } ?: return null
        val area = strip.width * strip.height / 3.0
        val runnerUp = blueArea.filterIndexed { index, _ -> index != best }.maxOrNull() ?: 0
        return best.takeIf { blueArea[it] >= area * .12 && blueArea[it] > runnerUp * 1.5 }
    }

    private fun white(color: Int) = channel(color, 16) > 235 && channel(color, 8) > 235 && channel(color, 0) > 235
    private fun numericGlyphs(box: PixelRegion): Boolean {
        if (box.width <= 0 || box.height <= 0) return false
        val glyphs = count(box) { white(it) || red(it) }
        return glyphs >= max(3.0, box.width * box.height * .008) && glyphs < box.width * box.height * .6
    }

    private fun hammerCounter(): PixelRegion? {
        val forgeButtons=blues.filter { it.x>width*.60 && it.y in height*.70..height*.87 && it.box.width>=width*.15 && it.box.height>=height*.025 }
        if(forgeButtons.isEmpty()) return null
        return oranges.filter { marker ->
            marker.area>=3 && marker.x in width*.36..width*.60 && marker.y in height*.70..height*.87 &&
                marker.box.width<=width*.04 && marker.box.height<=height*.025 &&
                forgeButtons.any { it.box.left>marker.box.right && it.box.left-marker.box.right<width*.25 && abs(it.y-marker.y)<height*.07 }
        }.mapNotNull { marker ->
            val top=max(0,marker.box.top-max(7,marker.box.height*2))
            val bottom=min(height,marker.box.bottom+max(5,marker.box.height))
            val left=marker.box.right+1
            var right=left;var empty=0
            for(x in left until min(width,(width*.68).toInt())) {
                val background=listOf(top+2,bottom-2,(top+bottom)/2).any { y -> gray(pixels[y*width+x]) }
                if(background) {right=x+1;empty=0}else{empty++;if(empty>=3)break}
            }
            val digits=PixelRegion(left,top,right,bottom)
            if(digits.width<width*.08 || !whiteGlyphs(digits)) return@mapNotNull null
            val anvil=grays.filter { body ->
                body.area>=width*height*.004 && body.box.width in (width*.18).toInt()..(width*.36).toInt() &&
                    body.box.height in (height*.025).toInt()..(height*.13).toInt() &&
                    marker.x in body.box.left.toDouble()..body.box.right.toDouble() &&
                    marker.y in body.box.top.toDouble()..(body.box.bottom+3).toDouble()
            }.maxByOrNull { it.area }
            val pad=max(3,(width*.012).toInt())
            // Keep only the lower counter base, not the upper anvil artwork or adjacent forge button.
            clamp(PixelRegion((anvil?.box?.left ?: min(marker.box.left,left))-pad,
                min(top,marker.box.top-max(7,marker.box.height*2))-2,
                max(right,anvil?.box?.right?.minus(pad) ?: right)+2,
                max(bottom,anvil?.box?.bottom ?: bottom)+2))
        }.singleOrNull()
    }

    private fun potionButton(): PixelRegion? {
        if(close!=null) return null
        val neck=greens.filter { it.x in width*.43..width*.57 && it.y>height*.9 && it.box.width>=width*.025 && it.box.height>=height*.012 }.maxByOrNull { it.area } ?: return null
        val box=clamp(PixelRegion((neck.x-width*.08).toInt(),neck.box.top-3,(neck.x+width*.06).toInt(),min(height,(neck.box.bottom+height*.035).toInt())))
        val colored=count(box,::cyan)
        val background=PixelRegion(0,box.top,width,box.bottom)
        return box.takeIf { colored>=box.width*box.height*.06 && count(background) { color ->
            val r=channel(color,16);val g=channel(color,8);val b=channel(color,0);r in 35..120 && g in 40..125 && b in 50..155
        }>=background.width*background.height*.45 }
    }

    private fun equipmentMountCard(): PixelRegion? {
        if(close!=null) return null
        return darks.filter { card ->
            card.x>width*.65 && card.y in height*.55..height*.79 && card.box.width in (width*.28).toInt()..(width*.45).toInt() &&
                card.box.height in (height*.045).toInt()..(height*.11).toInt() && card.box.width.toDouble()/card.box.height in 1.7..2.8 &&
                darks.count { neighbor -> neighbor!==card && neighbor.box.right<card.box.left && abs(neighbor.box.top-card.box.top)<card.box.height*.12 &&
                    abs(neighbor.box.height-card.box.height)<card.box.height*.2 && neighbor.box.width in (card.box.height*.75).toInt()..(card.box.height*1.3).toInt() }>=2
        }.singleOrNull()?.box?.let { inset(it,3) }
    }

    private fun detectTabs(): PixelRegion? {
        val x=close ?: return null
        val centerY=(x.top+x.bottom)/2.0
        if(centerY<height*.88) return null
        return darks.filter { bar ->
            bar.box.width>width*.72 && bar.box.height in (height*.025).toInt()..(height*.065).toInt() &&
                abs(bar.x-width*.5)<width*.1 && centerY-bar.y in height*.04..height*.15
        }.singleOrNull()?.box
    }
    fun technologyTab(): PixelRegion? = tabs?.let { tab(it,2) }

    private fun tab(box: PixelRegion,index:Int):PixelRegion = inset(PixelRegion(box.left+box.width*index/3,box.top,box.left+box.width*(index+1)/3,box.bottom),3)
    private fun whiteGlyphs(box:PixelRegion):Boolean {
        if(box.width<=0||box.height<=0) return false
        val white=count(box) { channel(it,16)>235 && channel(it,8)>235 && channel(it,0)>235 }
        return white>=max(3.0,box.width*box.height*.012).toInt() && white<box.width*box.height*.5
    }
    private fun clamp(box:PixelRegion)=PixelRegion(box.left.coerceIn(0,width),box.top.coerceIn(0,height),box.right.coerceIn(0,width),box.bottom.coerceIn(0,height))
    private fun inset(box:PixelRegion,p:Int)=PixelRegion(box.left+p,box.top+p,box.right-p,box.bottom-p)
    private fun count(box:PixelRegion,predicate:(Int)->Boolean):Int {
        val safe=clamp(box);var total=0
        for(y in safe.top until safe.bottom) for(x in safe.left until safe.right) if(predicate(pixels[y*width+x])) total++
        return total
    }
    private fun components(predicate:(Int)->Boolean, region: PixelRegion? = null):List<Component> {
        val search = region?.let(::clamp) ?: PixelRegion(0, 0, width, height)
        if (search.width <= 0 || search.height <= 0) return emptyList()
        val visited=BooleanArray(pixels.size);val queue=IntArray(pixels.size);val output=mutableListOf<Component>()
        for (row in search.top until search.bottom) for (column in search.left until search.right) {
            val first = row * width + column
            if(visited[first]||!predicate(pixels[first]))continue
            var head=0;var tail=0;queue[tail++]=first;visited[first]=true
            var left=first%width;var right=left;var top=first/width;var bottom=top
            while(head<tail) {
                val index=queue[head++];val x=index%width;val y=index/width
                left=min(left,x);right=max(right,x);top=min(top,y);bottom=max(bottom,y)
                for(dy in -1..1)for(dx in -1..1) {
                    val nx=x+dx;val ny=y+dy
                    if(nx !in search.left until search.right||ny !in search.top until search.bottom)continue
                    val next=ny*width+nx
                    if(!visited[next]&&predicate(pixels[next])) {visited[next]=true;queue[tail++]=next}
                }
            }
            if(tail>=3)output+=Component(PixelRegion(left,top,right+1,bottom+1),tail)
        }
        return output
    }
}
