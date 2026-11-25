package es.planidroid.app.ui
import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*
import es.planidroid.app.sync.AiConfig
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

internal suspend fun shoppingPhotoBytes(ctx:Context,uri:Uri):ByteArray=withContext(Dispatchers.IO){
    val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    ctx.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}
    require(options.outWidth>0&&options.outHeight>0)
    var sample=1;while(maxOf(options.outWidth,options.outHeight)/sample>1280)sample*=2
    val bitmap=ctx.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}?:error("Imagen inválida")
    try{ByteArrayOutputStream().use{out->bitmap.compress(Bitmap.CompressFormat.JPEG,75,out);out.toByteArray().also{require(it.size<=2_000_000)}}}finally{bitmap.recycle()}
}

private data class RecipeIngredient(val name:String,val amount:Double,val unit:String,val alreadyListed:Boolean=false)
private data class RecipeDraft(val title:String,val servings:Int,val steps:List<String>,val ingredients:List<RecipeIngredient>)
private fun readRecipes(body:org.json.JSONObject):List<RecipeDraft>{
    val recipes=body.getJSONArray("recipes")
    return (0 until minOf(recipes.length(),5)).map{i->
        val recipe=recipes.getJSONObject(i);val ingredients=recipe.getJSONArray("ingredients");val steps=recipe.getJSONArray("steps")
        RecipeDraft(recipe.getString("title").take(160),recipe.getInt("servings").coerceIn(1,50),
            (0 until minOf(steps.length(),20)).map{steps.getString(it).take(1000)},
            (0 until minOf(ingredients.length(),60)).map{j->
                val v=ingredients.getJSONObject(j);val n=v.getDouble("quantity");val name=v.getString("name").trim().take(180);val unit=v.getString("unit").trim().take(30)
                require(name.isNotBlank()&&n.isFinite()&&n>0&&n<=100000&&unit.isNotBlank())
                RecipeIngredient(name,n,unit)
            })
    }.filter{it.ingredients.isNotEmpty()}
}
@Composable fun RecipeSuggestions(uid:String,ingredients:List<Shopping>,listId:String,close:()->Unit,add:(List<Shopping>)->Unit){
    val ctx=LocalContext.current;val scope=rememberCoroutineScope()
    var request by remember{mutableStateOf("")}
    var recipes by remember{mutableStateOf(emptyList<RecipeDraft>())}
    var selectedRecipe by remember{mutableStateOf(0)}
    var selected by remember{mutableStateOf(emptySet<Int>())}
    var amounts by remember{mutableStateOf(emptyMap<Int,String>())}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var connect by remember{mutableStateOf(false)}
    val recipe=recipes.getOrNull(selectedRecipe)
    fun choose(index:Int,list:List<RecipeDraft> = recipes){selectedRecipe=index;val items=list[index].ingredients;selected=items.indices.toSet();amounts=items.mapIndexed{i,v->i to amountText(v.amount)}.toMap()}
    val valid=selected.all{i->amounts[i]?.replace(',','.')?.toDoubleOrNull()?.let{it.isFinite()&&it>0&&it<=100000}==true}
    AlertDialog(onDismissRequest={if(!busy)close()},title={Text("Recetas con Gemini")},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("Se enviarán los productos y sus cantidades indicadas, junto a tu petición.",style=MaterialTheme.typography.bodySmall)
        Field(request,{request=it.take(2000)},"¿Qué quieres cocinar?",false)
        Button(enabled=!busy&&request.isNotBlank(),onClick={
            if(!AiConfig.configured(ctx,uid))connect=true else{busy=true;error="";scope.launch{
                try{
                    val context=ingredients.take(100).joinToString("\n"){it.name+if(it.quantitySpecified)" · "+amountText(it.quantity)+" "+it.unit else " · cantidad no indicada"}
                    val response=AiConfig.provider(ctx,uid).recipe(context,request)
                    val result=readRecipes(response);check(result.isNotEmpty()){"No hay recetas"}
                    recipes=result;choose(0,result)
                }catch(e:CancellationException){throw e}catch(_:Exception){error="No se pudieron generar recetas. Revisa Gemini o vuelve a intentarlo."}finally{busy=false}
            }}
        }){Text("Generar recetas")}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        if(recipe!=null){
            if(recipes.size>1)Choice("Receta",recipes.indices.toList(),selectedRecipe,{recipes[it].title}){choose(it)}
            Text(recipe.title,style=MaterialTheme.typography.titleLarge)
            Text("Para "+recipe.servings+" personas",style=MaterialTheme.typography.labelLarge)
            Text("Ingredientes",style=MaterialTheme.typography.titleMedium)
            Text("Desmarca los que no quieras añadir. Si ya están en tu lista, desmárcalos para evitar duplicados.",style=MaterialTheme.typography.bodySmall)
            Row{TextButton(onClick={selected=recipe.ingredients.indices.toSet()}){Text("Marcar todos")};TextButton(onClick={selected=emptySet()}){Text("Desmarcar todos")}}
            recipe.ingredients.forEachIndexed{i,item->
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                    Checkbox(i in selected,{v->selected=if(v)selected+i else selected-i},enabled=!busy)
                    Column(Modifier.weight(1f)){Text(item.name);if(ingredients.any{it.name.equals(item.name,true)})Text("Ya en tu lista",style=MaterialTheme.typography.labelSmall)}
                    OutlinedTextField(amounts[i].orEmpty(),{v->amounts=amounts+(i to v.take(12))},Modifier.width(85.dp),singleLine=true,enabled=i in selected&&!busy)
                    Text(item.unit,Modifier.padding(start=6.dp))
                }
            }
            Text("Preparación",style=MaterialTheme.typography.titleMedium)
            recipe.steps.forEachIndexed{i,step->Text((i+1).toString()+". "+step)}
        }
    }},confirmButton={TextButton(enabled=!busy&&recipe!=null&&selected.isNotEmpty()&&valid,onClick={
        val items=recipe?.ingredients.orEmpty().mapIndexedNotNull{i,item->if(i !in selected)null else Shopping(name=item.name,quantity=amounts.getValue(i).replace(',','.').toDouble(),unit=item.unit,listId=listId,quantitySpecified=true,icon=productType(item.name).symbol)}
        add(items);close()
    }){Text("Añadir "+selected.size+" ingredientes")}},dismissButton={TextButton(enabled=!busy,onClick=close){Text("Cerrar")}})
    if(connect)PersonalGeminiDialog(uid){connect=false}
}
