package es.planidroid.app.domain

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

@Serializable enum class InventoryKind(val label:String){
    AUTO("Automático"), INGREDIENT("Ingrediente"), RECIPE("Receta / plato preparado"), OTHER("Otro producto")
}
object InventoryConsumption {
    fun kind(item:Item):InventoryKind {
        if(item.contentKind!=InventoryKind.AUTO)return item.contentKind
        val name=fold(item.name)
        if(Regex("""\b(paella|lasa[nñ]a|guiso|estofado|croquetas?|tortilla|ensaladilla|gazpacho|salmorejo|plato preparado|sopa preparada|pizza)\b""").containsMatchIn(name))return InventoryKind.RECIPE
        return when(productType(item.name)){
            ProductType.DAIRY,ProductType.YOGURT,ProductType.CHEESE,ProductType.EGGS,ProductType.FRUIT,ProductType.VEGETABLES,ProductType.MEAT,ProductType.FISH,ProductType.BAKERY,ProductType.FROZEN,ProductType.DRINKS,ProductType.PANTRY->InventoryKind.INGREDIENT
            else->InventoryKind.OTHER
        }
    }
    fun fold(s:String)=Normalizer.normalize(s.trim().lowercase(Locale.ROOT),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").replace(Regex("\\s+")," ")
    fun unitKey(s:String)=SpanishUnits.canonical(s)?:fold(s)
    fun consume(item:Item,n:Double):Item{
        require(item.amount.isFinite()&&item.amount>0&&n.isFinite()&&n>0&&n<=item.amount){"Cantidad superior a las existencias"}
        val left=BigDecimal.valueOf(item.amount).subtract(BigDecimal.valueOf(n)).toDouble()
        return item.copy(amount=left,quantity=amountText(left),restockAmount=if(left==0.0)item.amount else 0.0,expiry=if(left==0.0)"" else item.expiry)
    }
    fun pending(item:Item)=item.amount==0.0&&item.restockAmount.isFinite()&&item.restockAmount>0
    fun sameProduct(a:Shopping,b:Shopping)=fold(a.name)==fold(b.name)&&unitKey(a.unit)==unitKey(b.unit)
    fun alreadyPending(candidate:Shopping,existing:List<Shopping>,stock:Item?=null):Boolean=existing.any{
        it.listId==candidate.listId&&(sameProduct(it,candidate)||(candidate.itemId.isNotBlank()&&it.itemId==candidate.itemId))&&ShoppingLogic.needed(it,stock?.takeIf{s->s.id==it.itemId})>0
    }
}
