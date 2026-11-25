package es.planidroid.app.domain

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable enum class ZoneType(val label:String) { SHELF("Balda"), DRAWER("Cajón"), BASKET("Cesta"), RACK("Botellero"), OPEN("Hueco") }
@Serializable data class Zone(val name:String, val type:ZoneType=ZoneType.SHELF, val door:Boolean=false, val x:Int=0, val y:Int=0, val w:Int=4, val h:Int=1)
object LayoutGrid {
    const val WIDTH=4
    const val HEIGHT=8
    fun defaultName(type:ZoneType,others:List<Zone>):String {
        val base=type.label
        var index=1
        while(true){val candidate=if(index==1)base else "$base $index";if(others.none{it.name.equals(candidate,true)})return candidate;index++}
    }
    fun fits(z:Zone, others:List<Zone>):Boolean = z.w in 1..WIDTH && z.h in 1..HEIGHT && z.x>=0 && z.y>=0 && z.x+z.w<=WIDTH && z.y+z.h<=HEIGHT && others.none { it.name!=z.name && it.door==z.door && z.x<it.x+it.w && z.x+z.w>it.x && z.y<it.y+it.h && z.y+z.h>it.y }
    fun place(name:String,type:ZoneType,door:Boolean,others:List<Zone>,w:Int=4,h:Int=1):Zone? { for(y in 0..HEIGHT-h) for(x in 0..WIDTH-w) { val z=Zone(name,type,door,x,y,w,h);if(fits(z,others))return z };return null }
    fun zones(f:Furniture):List<Zone> = f.zones.ifEmpty { f.compartments.take(8).mapIndexed { i,n->Zone(n,y=i) } }
}
@Serializable data class Shopping(val id:String=newId(),val name:String,val quantity:Double=1.0,val unit:String="ud",val routine:Boolean=false,val pending:Boolean=true,val itemId:String="",val minimum:Double=0.0,val target:Double=1.0,val icon:String="🛒",val listId:String="",val quantitySpecified:Boolean=true,val referenceUrl:String="",val referenceTitle:String="")
@Serializable data class Purchase(val id:String=newId(),val shoppingId:String,val name:String,val quantity:Double,val unit:String,val routine:Boolean,val boughtAt:Long=System.currentTimeMillis(),val itemId:String="",val stockBefore:Double?=null,val stockAfter:Double?=null,val undone:Boolean=false,val icon:String="🛒",val listId:String="",val quantitySpecified:Boolean=true)
object ShoppingLogic {
    fun needed(s:Shopping,i:Item?):Double = if(s.routine && i!=null && i.amount<s.minimum) (s.target-i.amount).coerceAtLeast(0.0) else if(s.pending) s.quantity else 0.0
    fun canUndo(p:Purchase,i:Item?):Boolean = p.stockAfter!=null && p.stockBefore!=null && i!=null && abs(i.amount-p.stockAfter)<0.000001
}
val productIcons=listOf("📦","🥛","🧀","🥚","🍎","🍌","🥦","🥕","🍅","🥩","🐟","🍗","🍞","🧊","🍦","🥤","🍾","🍚","🥫","🍝","🧴","🧻","🧽","👕","💊","🔧")
fun legacyGuessIcon(name:String):String { val n=name.lowercase();return when { listOf("leche","yogur").any{it in n}->"🥛";"queso" in n->"🧀";"huevo" in n->"🥚";"manzana" in n->"🍎";"plátano" in n->"🍌";"tomate" in n->"🍅";"pollo" in n->"🍗";"pescado" in n->"🐟";"pan" in n->"🍞";"papel" in n->"🧻";"detergente" in n->"🧴";else->"📦"} }
fun amountText(n:Double)=if(n%1.0==0.0)n.toLong().toString() else java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString()

enum class ProductType(val symbol:String) {
    DAIRY("🥛"), YOGURT("🥣"), CHEESE("🧀"), EGGS("🥚"), FRUIT("🍎"), VEGETABLES("🥦"), MEAT("🥩"), FISH("🐟"), BAKERY("🍞"), FROZEN("🧊"), DRINKS("🥤"), PANTRY("🥫"), CLEANING("🧽"), HYGIENE("🧴"), PAPER("🧻"), MEDICINE("💊"), CLOTHES("👕"), TOOLS("🔧"), OTHER("📦")
}
fun productType(name:String):ProductType {
    val n=java.text.Normalizer.normalize(name.lowercase(),java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
    fun has(vararg words:String)=words.any{Regex("\\b"+Regex.escape(it)+"(?:s|es)?\\b").containsMatchIn(n)}
    return when {
        has("detergente","lejia","limpiador","estropajo","lavavajillas","suavizante","fregona","desinfectante","quitamanchas","bolsa basura","jabon platos","jabon de platos","jabon ropa","jabon de ropa","lavadora")->ProductType.CLEANING
        has("champu","jabon","gel","pasta dientes","pasta de dientes","cepillo dientes","cepillo de dientes","desodorante","higiene","dentifrico","crema facial","crema corporal","compresa","tampon","afeitado","protector solar")->ProductType.HYGIENE
        has("papel","servilleta","panuelo")->ProductType.PAPER
        has("yogur","yogurt","yoghurt","yogures","kefir","cuajada")->ProductType.YOGURT
        has("queso","mozzarella","parmesano","cheddar","gouda","ricotta","feta","requeson","brie","mascarpone")->ProductType.CHEESE
        has("leche","nata","mantequilla","lacteo","batido","bebida avena","bebida de soja")->ProductType.DAIRY
        has("huevo")->ProductType.EGGS
        has("manzana","platano","banana","pera","naranja","mandarina","fresa","uva","kiwi","melon","sandia","fruta","limon","limones","aguacate","mango","pina","arandano","frambuesa","melocoton","nectarina","ciruela","cereza","granada","papaya","pomelo","higo","albaricoque")->ProductType.FRUIT
        has("verdura","tomate","patata","cebolla","zanahoria","lechuga","brocoli","pimiento","calabacin","ajo","espinaca","pepino","champinon","champinones","coliflor","berenjena","puerro","apio","acelga","repollo","col","calabaza","esparrago","alcachofa","guisante","judia","habichuela","lenteja","garbanzo","alubia","legumbre","soja","habas","seta","boniato","rabano","remolacha")->ProductType.VEGETABLES
        has("pollo","carne","cerdo","ternera","jamon","pavo","salchicha","chorizo","bacon","cordero","conejo","filete","solomillo","pechuga","hamburguesa","lomo","mortadela","salami","fuet","panceta")->ProductType.MEAT
        has("pescado","salmon","atun","merluza","bacalao","gamba","sardina","marisco","dorada","lubina","calamar","pulpo","mejillon","langostino","anchoa","bonito","trucha","rape","caballa","sepia","almeja")->ProductType.FISH
        has("pan","tostada","baguette","croissant","bolleria","galleta","bizcocho","magdalena","tortilla trigo")->ProductType.BAKERY
        has("congelado","helado","hielo")->ProductType.FROZEN
        has("agua","zumo","refresco","coca","cerveza","vino","bebida","cafe","te","infusion")->ProductType.DRINKS
        has("arroz","pasta","aceite","harina","azucar","sal","conserva","salsa","cereal","espagueti","macarron","bote","avena","especia","pimienta","vinagre","miel","chocolate","frutos secos")->ProductType.PANTRY
        has("medicamento","paracetamol","ibuprofeno","tiritas","farmacia")->ProductType.MEDICINE
        has("camiseta","calcetin","ropa","pantalon","zapato")->ProductType.CLOTHES
        has("tornillo","pila","bombilla","herramienta","cable")->ProductType.TOOLS
        else->ProductType.OTHER
    }
}
fun guessIcon(name:String):String=productType(name).symbol

object ShoppingText {
    private val quantity=Regex("^\\s*(\\d+(?:[.,]\\d+)?)\\s*(kg|g|l|ml|uds?|unidades?|litros?|kilos?|paquetes?)?\\s*(?:de\\s+|[x×]\\s*)?(\\S.*)$",RegexOption.IGNORE_CASE)
    private val trailing=Regex("^(.+?)\\s+(\\d+(?:[.,]\\d+)?)\\s*(kg|g|l|ml|uds?|unidades?|litros?|kilos?|paquetes?)?$",RegexOption.IGNORE_CASE)
    fun parse(text:String,listId:String=""):List<Shopping> = text.lines().map{it.replace(Regex("^\\s*[-•*☐✓]+\\s*"),"").trim()}.filter{it.isNotBlank()}.take(100).map{line->
        val a=quantity.matchEntire(line);val b=if(a==null)trailing.matchEntire(line)else null
        val name=when{a!=null->a.groupValues[3];b!=null->b.groupValues[1];else->line}.trim().take(180)
        val n=(a?.groupValues?.get(1)?:b?.groupValues?.get(2))?.replace(',','.')?.toDoubleOrNull()?.takeIf{it.isFinite()&&it>0}
        Shopping(name=name,quantity=n?:1.0,unit=if(n==null)"" else a?.groupValues?.get(2)?:b?.groupValues?.get(3).orEmpty(),quantitySpecified=n!=null,icon=guessIcon(name),listId=listId)
    }.filter{it.name.isNotBlank()}
}
