package eu.komars.forgescanner.battle

import android.app.AlertDialog
import android.content.Context
import android.view.ContextThemeWrapper
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.WindowManager
import android.widget.*
import eu.komars.forgescanner.ui.ScannerAppearance as Style
import org.json.JSONObject

/** Editing, saving and simulation stay above the game; no Activity navigation or new capture consent. */
class BattleOverlayDialogs(private val context:Context,private val onMinimized:(Boolean)->Unit = {},private val onSimulationCompleted:()->Unit = {},resetSessionOnCreate:Boolean=true) {
    private val dialogContext=ContextThemeWrapper(context,eu.komars.forgescanner.R.style.ScannerDialogTheme)
    private val prefs=context.getSharedPreferences("battle_profiles_v1",0)
    private var dialog:AlertDialog?=null
    private var preparedFooter:LinearLayout?=null
    private var opponent:JSONObject?=null
    private var opponentDraft:JSONObject?=null
    private var externalDraft:JSONObject?=null
    private var externalSaved:((JSONObject)->Unit)?=null
    init { if(resetSessionOnCreate)resetOpponentSession() }
    private var externalCancelled:(()->Unit)?=null
    /** [onCancelled] (optional) runs when the player leaves the editor with "Wróć do gry" without saving. */
    fun reviewExternal(value:JSONObject,onCancelled:(()->Unit)?=null,onSaved:(JSONObject)->Unit) {externalDraft=value;externalSaved=onSaved;externalCancelled=onCancelled;review("external") {}}
    fun resetOpponentSession() { opponent=null;opponentDraft=null;prefs.edit().remove("opponent").remove("draft_opponent").remove("hasCompletedSimulation").apply() }
    fun clearOpponentDraft() { opponentDraft=null }
    fun hasOpponent()=opponent!=null
    fun canRepeat()=hasOpponent() && prefs.getBoolean("hasCompletedSimulation",false)
    fun setOpponentDraft(value:JSONObject) { opponentDraft=value }
    private val handler=Handler(Looper.getMainLooper())
    private val wm=context.getSystemService(WindowManager::class.java)
    private var restoreButton:Button?=null
    private fun removeRestoreButton() {
        restoreButton?.let { runCatching { wm.removeViewImmediate(it) } };restoreButton=null
        onMinimized(false)
    }
    fun hide() { removeRestoreButton();dialog?.dismiss();dialog=null }
    private fun minimize(alert:AlertDialog) {
        if(dialog!==alert || restoreButton!=null) return
        alert.window?.decorView?.windowToken?.let { token -> context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(token,0) }
        alert.hide();onMinimized(true)
        val button=Button(dialogContext).apply {
            text="Wróć do danych · przytrzymaj, aby zakończyć";isAllCaps=false;textSize=11f;setTextColor(Style.FOREGROUND)
            background=Style.rounded(context,Style.INPUT_BACKGROUND,12,Style.ACCENT)
            setOnClickListener { removeRestoreButton();if(dialog===alert) alert.show() }
        }
        button.setOnLongClickListener { (context.applicationContext as eu.komars.forgescanner.ScannerApplication).closeScanner();true }
        val params=WindowManager.LayoutParams(Style.dp(context,180),Style.dp(context,48),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT).apply {
            gravity=android.view.Gravity.TOP or android.view.Gravity.START;x=Style.dp(context,8);y=Style.dp(context,40)
        }
        var downX=0f;var downY=0f;var startX=0;var startY=0;var moved=false;var pressedAt=0L
        button.setOnTouchListener { _,event ->
            when(event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { downX=event.rawX;downY=event.rawY;startX=params.x;startY=params.y;moved=false;pressedAt=event.eventTime;true }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if(kotlin.math.abs(event.rawX-downX)+kotlin.math.abs(event.rawY-downY)>Style.dp(context,8)) moved=true
                    if(moved) {
                        params.x=(startX+event.rawX-downX).toInt().coerceIn(0,(context.resources.displayMetrics.widthPixels-button.width).coerceAtLeast(0))
                        params.y=(startY+event.rawY-downY).toInt().coerceIn(0,(context.resources.displayMetrics.heightPixels-button.height).coerceAtLeast(0))
                        wm.updateViewLayout(button,params)
                    };true
                }
                android.view.MotionEvent.ACTION_UP -> { if(!moved) { if(event.eventTime-pressedAt>=600) button.performLongClick() else button.performClick() };true }
                android.view.MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        restoreButton=button
        try { wm.addView(button,params) } catch(_:Exception) { removeRestoreButton();alert.show() }
    }
    fun saved(side:String):JSONObject? { if(side=="opponent") return opponent;return runCatching { prefs.getString(side,null)?.let(::JSONObject) }.getOrNull() }
    private fun draft(side:String):JSONObject? {if(side=="external")return externalDraft; if(side=="opponent") return opponentDraft;return runCatching { prefs.getString("draft_$side",null)?.let(::JSONObject) }.getOrNull() }
    private fun page():Pair<ScrollView,LinearLayout> {
        val body=LinearLayout(dialogContext).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(Style.BACKGROUND);setPadding(Style.dp(context,18),8,Style.dp(context,18),12) }
        return object:ScrollView(dialogContext) {
            override fun onMeasure(w:Int,h:Int) {
                val cap=(context.resources.displayMetrics.heightPixels*.5).toInt()
                val available=if(android.view.View.MeasureSpec.getMode(h)==android.view.View.MeasureSpec.UNSPECIFIED)cap else android.view.View.MeasureSpec.getSize(h)
                super.onMeasure(w,android.view.View.MeasureSpec.makeMeasureSpec(minOf(cap,available),android.view.View.MeasureSpec.AT_MOST))
            }
        }.apply { setBackgroundColor(Style.BACKGROUND);addView(body) } to body
    }
    private fun label(body:LinearLayout,text:String) { body.addView(TextView(dialogContext).apply { this.text=text;setTextColor(Style.FOREGROUND);textSize=14f;setPadding(0,12,0,6) }) }
    private fun field(body:LinearLayout,title:String,value:String):EditText {
        label(body,title)
        return EditText(dialogContext).apply {
            setText(value);isSingleLine=true;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(Style.FOREGROUND);textSize=17f;setPadding(12,12,12,12)
            background=Style.rounded(context,Style.INPUT_BACKGROUND,10,Style.BORDER);body.addView(this)
        }
    }
    private fun editorContent(scroll:ScrollView):LinearLayout {
        val footer=LinearLayout(dialogContext).apply {orientation=LinearLayout.VERTICAL}
        preparedFooter=footer
        return LinearLayout(dialogContext).apply {
            orientation=LinearLayout.VERTICAL
            addView(scroll,LinearLayout.LayoutParams(-1,-2));addView(footer,LinearLayout.LayoutParams(-1,-2))
        }
    }
    private fun show(alert:AlertDialog) {
        val footer=preparedFooter;preparedFooter=null
        hide();dialog=alert;alert.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);alert.show()
        alert.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        alert.setOnDismissListener {if(dialog===alert){removeRestoreButton();dialog=null}}
        alert.window?.setBackgroundDrawable(ColorDrawable(Style.BACKGROUND))
        listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_NEGATIVE).forEach {id ->
            alert.getButton(id)?.let {button ->
                button.setTextColor(Style.ACCENT);button.isAllCaps=false
                if(footer!=null && button.visibility==android.view.View.VISIBLE) {
                    val old=button.parent as? android.view.ViewGroup;old?.removeView(button);old?.visibility=android.view.View.GONE
                    button.minHeight=Style.dp(context,48);button.minimumHeight=Style.dp(context,48)
                    footer.addView(button,LinearLayout.LayoutParams(-1,-2))
                }
            }
        }
    }
    fun confirmFairyLevel(candidates:List<Int>,confirmed:(Int)->Unit,cancelled:()->Unit,preview:android.graphics.Bitmap?=null,calculated:List<Int> = emptyList()) {
        val (scroll,body)=page()
        label(body,"Poziom wróżki jest niepewny. Odczyty OCR: ${candidates.joinToString().ifBlank {"brak"}}. Nie udało się rozstrzygnąć odczytu automatycznie.")
        if(calculated.isNotEmpty())label(body,"Poziomy wynikające z odczytanych bonusów: ${calculated.joinToString()}. Wyniki wymagają potwierdzenia.")
        if(preview!=null){label(body,"Powiększony kształt cyfr z gry:");body.addView(android.widget.ImageView(dialogContext).apply {setImageBitmap(preview);scaleType=android.widget.ImageView.ScaleType.FIT_CENTER;setBackgroundColor(android.graphics.Color.WHITE)},LinearLayout.LayoutParams(-1,Style.dp(context,100)))}
        val input=field(body,"Wpisz poziom widoczny w grze (1–20)","")
        val choices=LinearLayout(dialogContext)
        for(level in FairyLevelChoices.values(candidates,calculated))choices.addView(Button(dialogContext).apply {text=level.toString();setOnClickListener {input.setText(level.toString())}},LinearLayout.LayoutParams(0,-2,1f))
        if(choices.childCount>0)body.addView(choices)
        input.inputType=InputType.TYPE_CLASS_NUMBER
        val error=TextView(dialogContext).apply {setTextColor(Style.ACCENT)};body.addView(error)
        val alert=AlertDialog.Builder(dialogContext).setTitle("Potwierdź poziom wróżki")
            .setView(editorContent(scroll)).setPositiveButton("Potwierdź",null).setNeutralButton("Podejrzyj grę",null)
            .setNegativeButton("Anuluj") {_,_->cancelled()}.create()
        show(alert)
        alert.setOnCancelListener {cancelled()}
        alert.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {minimize(alert)}
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val level=input.text.toString().toIntOrNull()
            if(level==null || level !in 1..20)error.text="Wpisz poprawny poziom od 1 do 20."
            else {
                alert.window?.decorView?.windowToken?.let { context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(it,0) }
                hide();handler.postDelayed({confirmed(level)},350)
            }
        }
    }
    fun choose(actions:List<Pair<String,()->Unit>>) {
        show(AlertDialog.Builder(dialogContext).setTitle("Symulator walki").setItems(actions.map { it.first }.toTypedArray()) { _,index -> actions[index].second() }.create())
    }
    fun review(side:String,compact:Boolean=false,onSaved:()->Unit) {
        val o=draft(side) ?: saved(side)
        if(o==null) { Toast.makeText(context,"Najpierw zeskanuj postać",Toast.LENGTH_SHORT).show();return }
        val (scroll,body)=page()
        val name=field(body,"Nazwa",BattleText.playerName(o.optString("name")))
        val damage=field(body,"Łączne obrażenia",if(side=="external")runCatching {java.math.BigDecimal(o.optString("damage")).stripTrailingZeros().toPlainString()}.getOrDefault(o.optString("damage"))else o.optString("damage"))
        val health=field(body,"Łączne zdrowie",if(side=="external")runCatching {java.math.BigDecimal(o.optString("health")).stripTrailingZeros().toPlainString()}.getOrDefault(o.optString("health"))else o.optString("health"))
        if(o.optString("headerRaw").isNotBlank()) label(body,"Odczyt statystyk: ${o.optString("headerRaw")}")
        val fairy=o.optJSONObject("fairy")?:JSONObject()
        label(body,if(fairy.has("present") && !fairy.optBoolean("present"))"Wróżka: brak" else "Wróżka: ${fairy.optString("type","nieodczytana")} · poziom ${if(fairy.has("level"))fairy.optInt("level").toString() else "nieodczytany"}")
        val fairyTypes=listOf("TIRA","MIRA","LORA")
        val fairyType=Spinner(dialogContext).apply {
            adapter=ArrayAdapter(dialogContext,android.R.layout.simple_spinner_dropdown_item,listOf("Wybierz wróżkę…","Tira","Mira","Lora"))
            setSelection(fairyTypes.indexOf(fairy.optString("type")).let {if(it<0)0 else it+1});body.addView(this)
        }
        val fairyLevelInput=field(body,"Poziom wróżki (1–20)",fairy.optString("level"))
        val noFairy=CheckBox(dialogContext).apply {
            text="Brak wyposażonej wróżki";setTextColor(Style.FOREGROUND)
            isChecked=fairy.has("present") && !fairy.optBoolean("present");body.addView(this)
        }
        fairyType.isEnabled=!noFairy.isChecked
        fairyLevelInput.isEnabled=!noFairy.isChecked
        noFairy.setOnCheckedChangeListener {_,checked->fairyLevelInput.isEnabled=!checked}
        if(fairy.optString("detailRaw").isNotBlank())label(body,"Efekt wróżki: ${fairy.optString("detailRaw")}")
        val title=o.optString("weapon")
        if(title.isNotBlank()) label(body,"Odczyt broni: $title")
        label(body,"Typ broni")
        val types=RadioGroup(dialogContext)
        val melee=RadioButton(dialogContext).apply { id=android.view.View.generateViewId();text="Wręcz";setTextColor(Style.FOREGROUND) }
        val ranged=RadioButton(dialogContext).apply { id=android.view.View.generateViewId();text="Dystans";setTextColor(Style.FOREGROUND) }
        types.addView(melee);types.addView(ranged);body.addView(types)
        val detected=if(o.has("ranged")) o.optBoolean("ranged") else BattleText.weapon(title)
        if(detected!=null) types.check(if(detected) ranged.id else melee.id)
        else label(body,"Nie odczytano typu broni — wybierz go ręcznie.")
        val old=o.optJSONObject("bonuses")
        val completed=BattleText.completeBonuses(o.optString("raw"),Bonus.entries.filter { old?.has(it.name)==true && !(o.optInt("schema",1)<2 && it==Bonus.SKILL_COOLDOWN && old.optDouble(it.name,0.0)==0.0) }.associateWith { old!!.optDouble(it.name,0.0) })
        val fields=Bonus.entries.associateWith { field(body,if(it==Bonus.RANGE) it.label else "${it.label} (%)",completed.getValue(it).toString()) }
        label(body,"Bonusy niewystępujące na odczytanej liście mają wartość 0.")
        val caps=BattleText.caps(o.optString("raw"))
        if(caps.isNotEmpty()) label(body,"Limity z opisu gry: "+caps.entries.joinToString { "${it.key.label}: ${it.value}%" })
        label(body,"Założone umiejętności — wszystkie 18 dostępne do wyboru")
        val skillSource=o.optJSONArray("skills") ?: o.optJSONArray("skillScans")
        val skillInputs=(0..2).map { index ->
            val prior=skillSource?.optJSONObject(index)
            label(body,"Umiejętność ${index+1}")
            val pick=Spinner(dialogContext)
            pick.adapter=ArrayAdapter(dialogContext,android.R.layout.simple_spinner_dropdown_item,listOf("Wybierz umiejętność…","Brak umiejętności")+BattleSkill.entries.map { it.label })
            val type=runCatching { BattleSkill.valueOf(prior?.optString("type").orEmpty()) }.getOrNull()
            pick.setSelection(if(type!=null) type.ordinal+2 else if(prior?.optBoolean("empty")==true || (o.optBoolean("skillsReviewed") && prior==null)) 1 else 0)
            body.addView(pick)
            val level=field(body,"Poziom ${type?.label ?: "umiejętności ${index+1}"} (1–100)",prior?.optInt("level",0)?.takeIf { it>0 }?.toString().orEmpty())
            pick to level
        }
        val pets=field(body,"Liczba założonych zwierzaków (0–3)",if(side=="external"&&!o.has("pets"))""else o.optInt("pets",3).toString())
        val mount=CheckBox(dialogContext).apply { text="Założony wierzchowiec";isChecked=o.optBoolean("mounted",true);setTextColor(Style.FOREGROUND) };body.addView(mount)
        val mountChoice=if(side=="external"&&!o.has("mounted"))Spinner(dialogContext).apply {
            adapter=ArrayAdapter(dialogContext,android.R.layout.simple_spinner_dropdown_item,listOf("Potwierdź wierzchowca…","Brak wierzchowca","Wierzchowiec wyposażony"));body.addView(this);mount.visibility=android.view.View.GONE
        }else null
        val mastery=field(body,"Dodatkowy bonus badań do aktywnych umiejętności (%)",o.optDouble("skillMastery",0.0).toString())
        label(body,"Bonus badań wpisz, jeśli nie jest już w liście bonusów. Brak odczytu umiejętności wymaga wyboru; nie zastępujemy go zerem.")
        val error=TextView(dialogContext).apply { setTextColor(0xFFFF9B9B.toInt()) };body.addView(error)
        fun issues():LinkedHashMap<android.view.View,String> {
            val out=linkedMapOf<android.view.View,String>()
            if((BattleText.number(damage.text.toString())?:0.0)<=0)out[damage]="Uzupełnij łączne obrażenia"
            if((BattleText.number(health.text.toString())?:0.0)<=0)out[health]="Uzupełnij łączne zdrowie"
            if(!noFairy.isChecked) {
                if(fairyType.selectedItemPosition==0)out[fairyType]="Wybierz wróżkę lub zaznacz jej brak"
                if((fairyLevelInput.text.toString().toIntOrNull()?:0) !in 1..20)out[fairyLevelInput]="Wpisz poziom wróżki 1–20"
            }
            if(types.checkedRadioButtonId==-1)out[types]="Wybierz typ broni"
            fields.forEach {(_,input)->
                val value=if(input.text.isBlank())0.0 else BattleText.number(input.text.toString())
                if(value==null || value>100000)out[input]="Nieprawidłowy bonus"
            }
            val duplicate=skillInputs.map {it.first.selectedItemPosition}.filter {it>=2}.groupingBy {it}.eachCount().filterValues {it>1}.keys
            skillInputs.forEach {(pick,level)->
                if(pick.selectedItemPosition==0)out[pick]="Wybierz umiejętność albo jej brak"
                else if(pick.selectedItemPosition in duplicate)out[pick]="Ta umiejętność jest wybrana dwa razy"
                if(pick.selectedItemPosition!=1 && (level.text.toString().toIntOrNull()?:0) !in 1..100)out[level]="Uzupełnij poziom 1–100"
            }
            if(mountChoice?.selectedItemPosition==0)out[mountChoice]="Potwierdź obecność wierzchowca"
            if((pets.text.toString().toIntOrNull()?:-1) !in 0..3)out[pets]="Wpisz liczbę zwierzaków 0–3"
            val masteryValue=BattleText.number(mastery.text.toString())
            if(masteryValue==null || masteryValue>100000)out[mastery]="Nieprawidłowy bonus badań"
            return out
        }
        if(compact) {
            val keep=mutableSetOf<android.view.View>(error)
            fun retain(v:android.view.View) { keep+=v;val index=body.indexOfChild(v);if(index>0) keep+=body.getChildAt(index-1) }
            if((BattleText.number(damage.text.toString()) ?: 0.0)<=0) retain(damage)
            if((BattleText.number(health.text.toString()) ?: 0.0)<=0) retain(health)
            if(detected==null) keep+=types
            val duplicates=skillInputs.map { it.first.selectedItemPosition }.filter { it>=2 }.groupingBy { it }.eachCount().filterValues { it>1 }.keys
            skillInputs.forEach { (pick,level) ->
                if(pick.selectedItemPosition==0 || pick.selectedItemPosition in duplicates) retain(pick)
                if(pick.selectedItemPosition!=1 && (level.text.toString().toIntOrNull() ?: 0) !in 1..100) retain(level)
            }
            issues().keys.forEach(::retain)
            if(!noFairy.isChecked && (fairyType.selectedItemPosition==0 || (fairyLevelInput.text.toString().toIntOrNull()?:0) !in 1..20))retain(noFairy)
            for(i in 0 until body.childCount) if(body.getChildAt(i) !in keep) body.getChildAt(i).visibility=android.view.View.GONE
        }
        val alert=AlertDialog.Builder(dialogContext).setTitle(if(compact) "Uzupełnij brakujący odczyt" else if(side=="self") "Moja postać" else "Przeciwnik")
            .setView(editorContent(scroll)).setNeutralButton("Zminimalizuj",null).setPositiveButton(if(side=="external")"Zapisz profil"else if(side=="self") "Zapisz" else "Użyj do walki",null).setNegativeButton("Wróć do gry",null).create()
        show(alert)
        if(side=="external"&&externalCancelled!=null)alert.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            val callback=externalCancelled;externalCancelled=null;externalSaved=null;externalDraft=null;hide();callback?.invoke()
        }
        val controls=listOf<android.view.View>(damage,health,fairyType,fairyLevelInput,types,pets,mastery)+listOfNotNull(mountChoice)+fields.values+skillInputs.flatMap {listOf(it.first,it.second)}
        val backgrounds=controls.associateWith {it.background}
        fun markIssues(scrollToFirst:Boolean):Boolean {
            val invalid=issues();val red=eu.komars.forgescanner.ui.ScannerAppearance.DANGER
            controls.forEach {v ->
                v.background=backgrounds[v]
                if(v is EditText)v.error=null
                if(v is Spinner)(v.selectedView as? TextView)?.setTextColor(Style.FOREGROUND)
                val i=body.indexOfChild(v);if(i>0)(body.getChildAt(i-1) as? TextView)?.setTextColor(Style.FOREGROUND)
            }
            invalid.forEach {(v,message)->
                v.visibility=android.view.View.VISIBLE
                v.background=Style.rounded(context,Style.INPUT_BACKGROUND,10,red)
                if(v is EditText)v.error=message
                if(v is Spinner)(v.selectedView as? TextView)?.setTextColor(red)
                val i=body.indexOfChild(v)
                if(i>0)(body.getChildAt(i-1) as? TextView)?.let {it.visibility=android.view.View.VISIBLE;it.setTextColor(red)}
            }
            error.text=if(invalid.isEmpty())""else "Uzupełnij pola zaznaczone na czerwono."
            if(scrollToFirst)invalid.keys.firstOrNull()?.let {first ->scroll.post {scroll.smoothScrollTo(0,(first.top-Style.dp(context,20)).coerceAtLeast(0))}}
            return invalid.isEmpty()
        }
        controls.filterIsInstance<EditText>().forEach {input ->input.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(text:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(text:CharSequence?,start:Int,before:Int,count:Int) {markIssues(false)}
            override fun afterTextChanged(text:android.text.Editable?) {}
        })}
        controls.filterIsInstance<Spinner>().forEach {pick ->pick.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?,view:android.view.View?,position:Int,id:Long) {markIssues(false)}
            override fun onNothingSelected(parent:AdapterView<*>?) {markIssues(false)}
        }}
        types.setOnCheckedChangeListener {_,_->markIssues(false)}
        noFairy.setOnCheckedChangeListener {_,checked->fairyLevelInput.isEnabled=!checked;fairyType.isEnabled=!checked;markIssues(false)}
        scroll.post {markIssues(true)}
        alert.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { minimize(alert) }
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(!markIssues(true))return@setOnClickListener
            val d=BattleText.number(damage.text.toString());val h=BattleText.number(health.text.toString())
            if(d==null || d<=0) { damage.error="Wpisz odczytane obrażenia";damage.requestFocus();return@setOnClickListener }
            if(h==null || h<=0) { health.error="Wpisz odczytane zdrowie";health.requestFocus();return@setOnClickListener }
            if(types.checkedRadioButtonId==-1) { error.text="Wybierz typ broni";return@setOnClickListener }
            val values=fields.mapValues { (_,edit) -> if(edit.text.isBlank()) 0.0 else BattleText.number(edit.text.toString()) }
            val invalid=values.entries.firstOrNull { it.value==null || it.value!!>100000 }
            if(invalid!=null) { fields.getValue(invalid.key).error="Nieprawidłowa wartość";fields.getValue(invalid.key).requestFocus();return@setOnClickListener }
            val learned=mutableListOf<Pair<BattleSkill,JSONObject?>>()
            val equipped=org.json.JSONArray();val chosen=mutableSetOf<BattleSkill>()
            for((slot,input) in skillInputs.withIndex()) {
                val (pick,level)=input
                if(pick.selectedItemPosition==0) { error.text="Wybierz wszystkie trzy umiejętności albo Brak umiejętności";return@setOnClickListener }
                if(pick.selectedItemPosition>=2) {
                    val type=BattleSkill.entries[pick.selectedItemPosition-2];val lv=level.text.toString().toIntOrNull()
                    if(lv==null || lv !in 1..100 || !chosen.add(type)) { error.text="Sprawdź poziomy i usuń powtórzone umiejętności";return@setOnClickListener }
                    val scan=o.optJSONArray("skillScans")?.optJSONObject(slot)
                    learned+=type to scan
                    equipped.put(JSONObject().put("type",type.name).put("level",lv))
                }
            }
            val petCount=pets.text.toString().toIntOrNull();val masteryValue=BattleText.number(mastery.text.toString())
            if(petCount==null || petCount !in 0..3 || masteryValue==null || masteryValue>100000) { error.text="Sprawdź liczbę zwierzaków i bonus badań";return@setOnClickListener }
            val bonuses=JSONObject();values.forEach { (key,value) -> bonuses.put(key.name,value) }
            val limits=JSONObject();caps.forEach { (key,value) -> limits.put(key.name,value) }
            val savedFairy=JSONObject(fairy.toString())
            if(noFairy.isChecked) {
                savedFairy.put("present",false).put("type","").put("level",0)
                savedFairy.remove("fifthBonus");savedFairy.remove("criticalReduction")
            } else if(fairyLevelInput.text.isNotBlank()) {
                savedFairy.put("type",fairyTypes[fairyType.selectedItemPosition-1])
                val level=fairyLevelInput.text.toString().toIntOrNull()
                if(level==null || level !in 1..20){error.text="Poziom wróżki musi mieścić się w zakresie 1–20";return@setOnClickListener}
                savedFairy.put("present",true).put("level",level)
            }
            val result=JSONObject().put("schema",2).put("name",name.text.toString().trim().ifEmpty { if(side=="self") "Moja postać" else "Przeciwnik" })
                .put("fairy",savedFairy).put("skills",equipped).put("skillsReviewed",true).put("pets",petCount).put("mounted",mountChoice?.let {it.selectedItemPosition==2}?:mount.isChecked).put("skillMastery",masteryValue).put("damage",d).put("health",h).put("ranged",types.checkedRadioButtonId==ranged.id).put("weapon",title)
                .put("bonuses",bonuses).put("caps",limits).put("raw",o.optString("raw")).put("skillAscension",o.optInt("skillAscension",0).coerceIn(0,3))
                .put("headerRaw",o.optString("headerRaw")).put("scannedAt",o.optLong("scannedAt",System.currentTimeMillis()))
            val memory=SkillVisualMemory(context)
            learned.forEach { (type,scan) -> if(scan!=null) memory.remember(type,scan.optString("raw"),scan.optJSONObject("visual")) }
            if(side=="external") {val callback=externalSaved;externalSaved=null;externalDraft=null;externalCancelled=null;hide();callback?.invoke(result);return@setOnClickListener}
            if(side=="self") prefs.edit().putString(side,result.toString()).remove("draft_$side").apply() else { opponent=result;opponentDraft=null;prefs.edit().putString("opponent",result.toString()).putBoolean("hasCompletedSimulation",false).apply() }
            hide();onSaved()
        }
    }
    fun acceptOpponent(onReady:()->Unit) {
        review("opponent",true,onReady)
        // A complete scan uses the same validation/save path without displaying its data.
        val o=draft("opponent") ?: return
        val entries=o.optJSONArray("skillScans")
        val complete=(BattleText.number(o.optString("damage")) ?: 0.0)>0 && (BattleText.number(o.optString("health")) ?: 0.0)>0 && BattleText.weapon(o.optString("weapon"))!=null &&
            entries?.length()==3 && (0..2).all { i -> val e=entries.optJSONObject(i);e!=null && (e.optBoolean("empty") || (runCatching { BattleSkill.valueOf(e.optString("type")) }.isSuccess && e.optInt("level") in 1..100)) } &&
            (0..2).map {entries!!.getJSONObject(it)}.filterNot {it.optBoolean("empty")}.map {it.optString("type")}.let {it.distinct().size==it.size}
        if(complete) dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
    }
    fun chooseBattle() {
        if(WarPreparationModel.day()==null) {simulate(false,true);return}
        choose(listOf("Zwykła walka" to { simulate(false,true) },"Walka wojenna" to { simulate(true,true) }))
    }
    private fun profile(o:JSONObject):BattleProfile {
        val values=o.optJSONObject("bonuses")
        val raw=o.optString("raw")
        return BattleProfile(o.optString("name"),o.getDouble("damage"),o.getDouble("health"),o.getBoolean("ranged"),
            BattleText.completeBonuses(raw,Bonus.entries.filter { values?.has(it.name)==true && !(o.optInt("schema",1)<2 && it==Bonus.SKILL_COOLDOWN && values.optDouble(it.name,0.0)==0.0) }.associateWith { values!!.optDouble(it.name,0.0) }),o.optLong("scannedAt"),raw,BattleText.caps(raw),
            (0 until (o.optJSONArray("skills")?.length() ?: 0)).map { i -> val e=o.getJSONArray("skills").getJSONObject(i);EquippedSkill(BattleSkill.valueOf(e.getString("type")),e.getInt("level")) },o.optInt("pets",0),o.optBoolean("mounted",false),o.optDouble("skillMastery",0.0),o.optJSONObject("fairy")?.optString("type").orEmpty(),o.optJSONObject("fairy")?.optInt("level",0)?:0,o.optInt("skillAscension",0).coerceIn(0,3))
    }
    fun simulate(war:Boolean,autoRun:Boolean=false) {
        val warData=context.getSharedPreferences("war_preparation_v1",0)
        if(war && (WarPreparationModel.day()==null || warData.getString("day",null)!=WarPreparationModel.day())) {
            show(AlertDialog.Builder(dialogContext).setTitle("Przygotowanie wojenne").setMessage("Potrzebny aktualny skan badań i bonusów przeciwnika z tego dnia wojennego.").setPositiveButton("Przygotuj") {_,_->(context.applicationContext as eu.komars.forgescanner.ScannerApplication).warOverlay.start(false)}.setNegativeButton("Wróć",null).create());return
        }
        if(saved("self")?.optBoolean("skillsReviewed")!=true) { review("self") { simulate(war,autoRun) };return }
        if(saved("opponent")?.optBoolean("skillsReviewed")!=true) { review("opponent") { simulate(war,autoRun) };return }
        val p=runCatching { profile(saved("self")!!) }.getOrNull()
        val o=runCatching { profile(saved("opponent")!!) }.getOrNull()
        if(p==null || o==null) { Toast.makeText(context,"Najpierw zapisz moją postać i zeskanuj przeciwnika",Toast.LENGTH_LONG).show();return }
        val (scroll,body)=page()
        label(body,"${p.name} kontra ${o.name}")
        label(body,"1000 symulacji · wynik szacunkowy")
        val result=TextView(dialogContext).apply { textSize=20f;setTextColor(Style.ACCENT);setPadding(0,16,0,12) };body.addView(result)
        val alert=AlertDialog.Builder(dialogContext).setTitle(if(war) "Walka wojenna" else "Walka")
            .setView(editorContent(scroll)).setPositiveButton("Przelicz",null).setNegativeButton("Wróć do gry",null).create()
        show(alert)
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(war && WarPreparationModel.day()!=warData.getString("day",null)) { result.text="Okno wojenne zakończone lub bonusy nieaktualne";return@setOnClickListener }
            val pw=if(war) WarBonus(warData.getFloat("damage",0f).toDouble(),warData.getFloat("health",0f).toDouble()) else WarBonus()
            val ow=if(war) WarBonus(warData.getFloat("enemyDamage",0f).toDouble(),warData.getFloat("enemyHealth",0f).toDouble()) else WarBonus()
            val rules=PvpRules(seconds=if(war) 120.0 else 60.0)
            alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false;result.text="Przeliczam…"
            Thread {
                val computed=runCatching { PvpBattleEngine.simulate(p,o,pw,ow,rules=rules) }
                handler.post {
                    if(alert.isShowing) {
                        alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true
                        if(computed.isSuccess) {
                            prefs.edit().putBoolean("hasCompletedSimulation",true).apply()
                            onSimulationCompleted()
                        }
                        result.text=computed.fold({ "Szansa wygranej: %.1f%%\nWygrane %d · przegrane %d · remisy %d".format(it.wins*100.0/it.count,it.wins,it.losses,it.draws) },{ "Nie udało się przeliczyć — sprawdź zapisane dane" })
                    }
                }
            }.start()
        }
        if(autoRun) {
            alert.getButton(AlertDialog.BUTTON_POSITIVE).text="Przelicz ponownie"
            alert.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }

    }
}
