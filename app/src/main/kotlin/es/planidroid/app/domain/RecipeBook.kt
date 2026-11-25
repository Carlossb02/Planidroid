package es.planidroid.app.domain

import kotlinx.serialization.Serializable

@Serializable data class RecipeIngredient(val name:String="",val amount:Double=0.0,val unit:String="")
@Serializable data class SavedRecipe(val id:String=newId(),val title:String="",val servings:Int=0,val ingredients:List<RecipeIngredient> = emptyList(),val steps:List<String> = emptyList(),val source:String="manual",val updatedAt:Long=System.currentTimeMillis())

object RecipeBook {
    fun validate(r:SavedRecipe){
        require(r.title.isNotBlank()&&r.title.length<=180&&r.servings in 0..100&&r.ingredients.size in 1..60&&r.steps.size<=30&&r.steps.all{it.length<=2000})
        require(r.ingredients.all{it.name.isNotBlank()&&it.name.length<=180&&it.amount.isFinite()&&it.amount in 0.0..100000.0&&SpanishUnits.canonical(it.unit)!=null})
        require(r.source in setOf("manual","gemini","photo"))
    }
    private val stop=setOf("una","uno","un","la","el","las","los","de","del","con","para","receta","recetas","quiero","hacer","cocinar","preparar","dame","como","se","me","por","favor","ingredientes")
    private fun words(s:String)=InventoryConsumption.fold(s).split(Regex("[^a-z0-9]+")).filter{it.length>2&&it !in stop}.map{if(it.length>4&&it.endsWith("s"))it.dropLast(1)else it}.toSet()
    fun matches(query:String,recipes:List<SavedRecipe>):List<SavedRecipe>{
        val q=words(query)
        if(q.isEmpty())return emptyList()
        return recipes.mapNotNull{r->val title=words(r.title);val common=title.intersect(q)
            if(title.isEmpty()||common.isEmpty()||!(common.size==title.size||common.size==q.size||common.size>=2&&common.size*2>=title.size))null
            else r to (common.size*10+if(title==q)100 else 0)
        }.sortedByDescending{it.second}.take(8).map{it.first}
    }
}
