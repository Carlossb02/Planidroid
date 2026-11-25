package es.planidroid.app.ui
import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

internal data class RecipeLibrary(val recipes:List<SavedRecipe>,val model:PlanidroidModel?)
internal val LocalRecipeLibrary=compositionLocalOf{RecipeLibrary(emptyList(),null)}

internal fun readRecipes(body:org.json.JSONObject,source:String):List<SavedRecipe>{
    val recipes=body.getJSONArray("recipes")
    return (0 until minOf(recipes.length(),5)).mapNotNull{i->
        val recipe=recipes.optJSONObject(i)?:return@mapNotNull null
        val items=recipe.optJSONArray("ingredients")?:return@mapNotNull null
        val steps=recipe.optJSONArray("steps")
        val ingredients=(0 until minOf(items.length(),60)).mapNotNull{j->
            val v=items.optJSONObject(j)?:return@mapNotNull null
            val name=v.optString("name").trim().take(180);val raw=v.optDouble("quantity",0.0)
            val unit=SpanishUnits.canonical(v.optString("unit"))
            if(name.isBlank()||!raw.isFinite()||raw<0||raw>100000)null
            else if(source!="photo"&&(raw<=0||unit.isNullOrBlank()))null
            else RecipeIngredient(name,if(unit==null)0.0 else raw,unit.orEmpty())
        }
        val result=SavedRecipe(title=recipe.optString("title").trim().take(180),servings=recipe.optInt("servings",0).coerceIn(0,100),ingredients=ingredients,steps=if(steps==null)emptyList()else (0 until minOf(steps.length(),30)).map{steps.optString(it).take(2000)}.filter{it.isNotBlank()},source=source)
        if(runCatching{RecipeBook.validate(result)}.isSuccess&&ingredients.size==items.length())result else null
    }
}

@Composable fun RecipeSuggestions(uid:String,ingredients:List<Shopping>,listId:String,close:()->Unit,add:(List<Shopping>)->Unit,dishName:String=""){
    val ctx=LocalContext.current;val scope=rememberCoroutineScope();val library=LocalRecipeLibrary.current
    var request by remember{mutableStateOf("")}
    var search by remember{mutableStateOf("")}
    var current by remember{mutableStateOf<SavedRecipe?>(null)}
    var selected by remember{mutableStateOf(emptySet<Int>())}
    var amounts by remember{mutableStateOf(emptyMap<Int,String>())};var units by remember{mutableStateOf(emptyMap<Int,String>())}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var connect by remember{mutableStateOf(false)}
    var existing by remember{mutableStateOf(emptyList<SavedRecipe>())}
    var editor by remember{mutableStateOf<SavedRecipe?>(null)};var remove by remember{mutableStateOf<SavedRecipe?>(null)}
    var scan by remember{mutableStateOf(false)};var imported by remember{mutableStateOf(emptyList<SavedRecipe>())}
    var showSteps by remember{mutableStateOf(false)};var requested by remember{mutableStateOf("")}
    fun choose(recipe:SavedRecipe){
        current=recipe;selected=recipe.ingredients.indices.toSet()
        amounts=recipe.ingredients.mapIndexed{i,v->i to if(v.amount>0)amountText(v.amount)else ""}.toMap()
        units=recipe.ingredients.mapIndexed{i,v->i to v.unit}.toMap();showSteps=false;error=""
    }
    fun generate(query:String){
        if(busy)return
        if(!AiConfig.configured(ctx,uid)){connect=true;return}
        busy=true;error=""
        scope.launch{
            try{
                val context=ingredients.take(40).joinToString(";"){it.name+if(it.quantitySpecified)" "+amountText(it.quantity)+" "+it.unit else ""}
                val result=readRecipes(AiConfig.provider(ctx,uid).recipe(context,query),"gemini")
                check(result.isNotEmpty()){"No se recibió una receta completa. Revisa la petición."}
                val model=library.model?:error("Recetario no disponible")
                model.saveRecipes(result){failure->
                    busy=false
                    if(failure==null){choose(result.first());search=""}else error=failure
                }
            }catch(e:CancellationException){throw e}catch(e:Exception){error=e.localizedMessage?:"No se pudieron generar recetas";busy=false}
        }
    }
    fun ask(){
        val query=if(dishName.isBlank())request.trim()else "Receta de "+dishName+". "+request.trim()
        if(query.isBlank())return
        requested=query
        val matches=RecipeBook.matches(query,library.recipes)
        if(matches.isNotEmpty())existing=matches else generate(query)
    }
    val recipe=current
    val matching=if(dishName.isBlank())emptyList()else RecipeBook.matches(dishName,library.recipes)
    val shown=library.recipes.filter{search.isBlank()||InventoryConsumption.fold(it.title).contains(InventoryConsumption.fold(search))}.sortedByDescending{it.updatedAt}
    val valid=selected.all{i->amounts[i]?.replace(',','.')?.toDoubleOrNull()?.let{it.isFinite()&&it>0&&it<=100000}==true}
    Dialog(onDismissRequest={if(!busy)close()},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize()){
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()){
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                    IconButton(enabled=!busy,onClick={if(current!=null)current=null else close()}){Icon(if(recipe==null)Icons.Default.Close else Icons.Default.ArrowBack,"Volver")}
                    Column(Modifier.weight(1f)){Text(if(recipe==null)"Mis recetas" else recipe.title,style=MaterialTheme.typography.titleLarge);if(recipe!=null)Text(if(recipe.servings>0)"Para "+recipe.servings+" personas"else "Raciones sin especificar",style=MaterialTheme.typography.bodySmall)}
                    if(recipe!=null)IconButton(enabled=!busy,onClick={editor=recipe}){Icon(Icons.Default.Edit,"Editar receta")}
                    else IconButton(enabled=!busy,onClick={scan=true}){Icon(Icons.Default.CameraAlt,"Escanear recetario")}
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    if(recipe==null){
                        GlassCard(tint=Lilac){
                            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.AutoAwesome,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(8.dp));Text(if(dishName.isBlank())"Cocina con Gemini"else "Ingredientes de "+dishName,style=MaterialTheme.typography.titleMedium)}
                            OutlinedTextField(request,{request=it.take(1500)},Modifier.fillMaxWidth(),placeholder={Text(if(dishName.isBlank())"¿Qué quieres cocinar?"else "Preferencias (opcional)")},minLines=2,enabled=!busy,shape=RoundedCornerShape(16.dp))
                            FilledTonalButton(enabled=!busy&&(request.isNotBlank()||dishName.isNotBlank()),onClick={ask()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text("Consultar Gemini")}
                        }
                        if(matching.isNotEmpty()){
                            Text("Ya tienes estas recetas",style=MaterialTheme.typography.titleMedium)
                            matching.forEach{r->RecipeLibraryRow(r,{choose(r)},{editor=r},{remove=r})}
                        }
                        Row(verticalAlignment=Alignment.CenterVertically){Text("Recetario",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);FilledTonalIconButton(enabled=!busy,onClick={editor=SavedRecipe()}){Icon(Icons.Default.Add,"Crear receta")}}
                        if(library.recipes.isNotEmpty())OutlinedTextField(search,{search=it.take(180)},Modifier.fillMaxWidth(),placeholder={Text("Buscar receta")},singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(16.dp))
                        if(shown.isEmpty())Text(if(library.recipes.isEmpty())"Guarda tus recetas o escanea una foto." else "No hay recetas que coincidan.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        shown.filter{r->matching.none{it.id==r.id}}.forEach{r->RecipeLibraryRow(r,{choose(r)},{editor=r},{remove=r})}
                        if(imported.isNotEmpty()){
                            Text("Recetas de la foto · revisa y guarda",style=MaterialTheme.typography.titleMedium)
                            imported.forEach{r->RecipeLibraryRow(r,{editor=r},{editor=r},{imported=imported.filterNot{it.id==r.id}})}
                        }
                    }else{
                        Row(verticalAlignment=Alignment.CenterVertically){Text("Ingredientes",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick={selected=if(selected.size==recipe.ingredients.size)emptySet()else recipe.ingredients.indices.toSet()}){Text(if(selected.size==recipe.ingredients.size)"Ninguno"else "Todos")}}
                        recipe.ingredients.forEachIndexed{i,item->
                            Surface(shape=RoundedCornerShape(14.dp),color=if(i in selected)soft(Mint,.12f)else MaterialTheme.colorScheme.surface){
                                Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                                    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(i in selected,{v->selected=if(v)selected+i else selected-i},enabled=!busy);ProductGlyph(item.name,Modifier.size(25.dp));Column(Modifier.weight(1f).padding(start=8.dp)){Text(item.name,style=MaterialTheme.typography.titleMedium);if(ingredients.any{InventoryConsumption.fold(it.name)==InventoryConsumption.fold(item.name)})Text("Ya en tu lista",style=MaterialTheme.typography.labelSmall)}}
                                    if(i in selected)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Field(amounts[i].orEmpty(),{v->amounts=amounts+(i to v.take(16))},"Cantidad")};Column(Modifier.weight(1f)){UnitSelector(units[i]?:item.unit,{v->units=units+(i to v)})}}
                                }
                            }
                        }
                        TextButton(onClick={showSteps=!showSteps},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Restaurant,null);Spacer(Modifier.width(8.dp));Text("Preparación",Modifier.weight(1f));Icon(if(showSteps)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)}
                        if(showSteps){if(recipe.steps.isEmpty())Text("No hay pasos guardados.");recipe.steps.forEachIndexed{i,s->Text((i+1).toString()+". "+s)}}
                    }
                    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
                }
                Surface(tonalElevation=2.dp){
                    Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        if(recipe==null){
                            OutlinedButton(enabled=!busy,onClick={editor=SavedRecipe()},modifier=Modifier.weight(1f)){Icon(Icons.Default.Add,null);Text(" Crear")}
                            FilledTonalButton(enabled=!busy,onClick={scan=true},modifier=Modifier.weight(1f)){Icon(Icons.Default.CameraAlt,null);Text(" Escanear")}
                        }else Button(enabled=!busy&&selected.isNotEmpty()&&valid,onClick={
                            val products=recipe.ingredients.mapIndexedNotNull{i,item->if(i !in selected)null else Shopping(name=item.name,quantity=amounts.getValue(i).replace(',','.').toDouble(),unit=units[i]?:item.unit,listId=listId,quantitySpecified=true,icon=guessIcon(item.name))}
                            add(products);close()
                        },modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.AddShoppingCart,null);Spacer(Modifier.width(8.dp));Text("Añadir "+selected.size+" ingredientes")}
                    }
                }
            }
        }
    }
    if(existing.isNotEmpty())AlertDialog(onDismissRequest={existing=emptyList()},title={Text("Ya tienes una receta")},text={Column(Modifier.heightIn(max=340.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("¿Utilizar una receta guardada o generar una variante nueva?");existing.forEach{r->OutlinedButton(onClick={choose(r);existing=emptyList()},modifier=Modifier.fillMaxWidth()){Text(r.title)}}}},confirmButton={TextButton(onClick={existing=emptyList();generate(requested)}){Text("Generar otra")}},dismissButton={TextButton(onClick={existing=emptyList()}){Text("Cancelar")}})
    editor?.let{draft->RecipeEditor(draft,{editor=null},{next,done->
        val model=library.model
        if(model==null)done("Recetario no disponible")else model.saveRecipes(listOf(next)){failure->done(failure);if(failure==null){imported=imported.filterNot{it.id==next.id};choose(next);editor=null}}
    })}
    remove?.let{r->Confirm("Eliminar "+r.title,"Se eliminará del recetario, sin modificar listas de compra.",{remove=null},{library.model?.deleteRecipe(r.id)})}
    if(scan)RecipePhotoImport(uid,{scan=false},{drafts->scan=false;imported=drafts;if(drafts.size==1)editor=drafts.first()})
    if(connect)PersonalGeminiDialog(uid){connect=false}
}

@Composable private fun RecipeLibraryRow(recipe:SavedRecipe,open:()->Unit,edit:()->Unit,remove:()->Unit){
    var menu by remember(recipe.id){mutableStateOf(false)}
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().clickable(onClick=open)){
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(44.dp).background(soft(Mint,.2f),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Restaurant,null,tint=MaterialTheme.colorScheme.primary)}
            Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(recipe.title,style=MaterialTheme.typography.titleMedium);Text(recipe.ingredients.size.toString()+" ingredientes"+(if(recipe.servings>0)" · "+recipe.servings+" raciones"else ""),style=MaterialTheme.typography.labelSmall);Text(when(recipe.source){"gemini"->"Gemini";"photo"->"Recetario escaneado";else->"Receta propia"},style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Box{IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Opciones de "+recipe.title)};DropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text("Editar")},onClick={menu=false;edit()});DropdownMenuItem(text={Text("Eliminar",color=MaterialTheme.colorScheme.error)},onClick={menu=false;remove()})}}
        }
    }
}

private data class IngredientForm(val id:String=newId(),val name:String="",val quantity:String="",val unit:String="")
@Composable private fun RecipeEditor(initial:SavedRecipe,close:()->Unit,save:(SavedRecipe,(String?)->Unit)->Unit){
    var title by remember(initial.id){mutableStateOf(initial.title)}
    var servings by remember(initial.id){mutableStateOf(if(initial.servings>0)initial.servings.toString()else "")}
    var items by remember(initial.id){mutableStateOf(initial.ingredients.map{IngredientForm(name=it.name,quantity=if(it.amount>0)amountText(it.amount)else "",unit=it.unit)}.ifEmpty{listOf(IngredientForm())})}
    var steps by remember(initial.id){mutableStateOf(initial.steps.joinToString("\n"))}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    fun update(row:IngredientForm){items=items.map{if(it.id==row.id)row else it}}
    val count=servings.ifBlank{"0"}.toIntOrNull()
    val valid=title.isNotBlank()&&count!=null&&count in 0..100&&items.any{it.name.isNotBlank()}&&items.all{it.name.isBlank()||it.quantity.isBlank()||it.quantity.replace(',','.').toDoubleOrNull()?.let{n->n.isFinite()&&n>0&&n<=100000}==true}
    Dialog(onDismissRequest={if(!busy)close()},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize()){
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()){
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){IconButton(enabled=!busy,onClick=close){Icon(Icons.Default.Close,"Cerrar")};Text(if(initial.title.isBlank())"Nueva receta"else "Editar receta",style=MaterialTheme.typography.titleLarge)}
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Field(title,{title=it.take(180)},"Nombre")
                    Field(servings,{servings=it.take(3)},"Raciones (opcional)")
                    Row(verticalAlignment=Alignment.CenterVertically){Text("Ingredientes",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);IconButton(enabled=items.size<60&&!busy,onClick={items=items+IngredientForm()}){Icon(Icons.Default.Add,"Añadir ingrediente")}}
                    items.forEach{row->key(row.id){
                        Surface(shape=RoundedCornerShape(14.dp),color=soft(Mint,.1f)){
                            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                                Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Field(row.name,{update(row.copy(name=it.take(180)))},"Ingrediente")};IconButton(enabled=!busy,onClick={items=items.filterNot{it.id==row.id}}){Icon(Icons.Default.DeleteOutline,"Eliminar ingrediente")}}
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Field(row.quantity,{update(row.copy(quantity=it.take(16)))},"Cantidad")};Column(Modifier.weight(1f)){UnitSelector(row.unit,{update(row.copy(unit=it))})}}
                            }
                        }
                    }}
                    OutlinedButton(enabled=items.size<60&&!busy,onClick={items=items+IngredientForm()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Text(" Añadir ingrediente")}
                    OutlinedTextField(steps,{steps=it.take(20000)},Modifier.fillMaxWidth(),label={Text("Preparación · un paso por línea")},minLines=4)
                    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
                    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Surface(tonalElevation=2.dp){Button(enabled=valid&&!busy,onClick={
                    val next=initial.copy(title=title.trim(),servings=count!!,ingredients=items.filter{it.name.isNotBlank()}.map{RecipeIngredient(it.name.trim(),it.quantity.replace(',','.').toDoubleOrNull()?:0.0,it.unit)},steps=steps.lines().map{it.trim().take(2000)}.filter{it.isNotBlank()}.take(30))
                    busy=true;error="";save(next){failure->busy=false;if(failure!=null)error=failure}
                },modifier=Modifier.fillMaxWidth().padding(16.dp)){Icon(Icons.Default.Check,null);Spacer(Modifier.width(8.dp));Text("Guardar receta")}}
            }
        }
    }
}

@Composable private fun RecipePhotoImport(uid:String,close:()->Unit,review:(List<SavedRecipe>)->Unit){
    val ctx=LocalContext.current;val scope=rememberCoroutineScope()
    var photo by remember{mutableStateOf<Uri?>(null)};var file by remember{mutableStateOf<java.io.File?>(null)}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var consent by remember{mutableStateOf(false)};var connect by remember{mutableStateOf(false)}
    fun cleanup(){file?.delete();file=null;photo=null}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->if(!ok)cleanup()}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null){cleanup();photo=uri}}
    fun scan(){
        val uri=photo?:return
        busy=true;error=""
        scope.launch{try{
            val bytes=shoppingPhotoBytes(ctx,uri)
            val result=try{readRecipes(AiConfig.provider(ctx,uid).recipePhoto(bytes),"photo")}finally{bytes.fill(0)}
            check(result.isNotEmpty()){"No se encontró una receta legible. Prueba otra foto."}
            cleanup();review(result)
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.localizedMessage?:"No se pudo leer la receta"}finally{busy=false}}
    }
    AlertDialog(onDismissRequest={if(!busy){cleanup();close()}},title={Text("Escanear recetario")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("Fotografía una receta. Revisa ingredientes, cantidades y preparación antes de guardarla.")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilledTonalButton(enabled=!busy,onClick={runCatching{cleanup();val dir=java.io.File(ctx.filesDir,"captures").apply{mkdirs()};val temp=java.io.File.createTempFile("recipe-",".jpg",dir);file=temp;val uri=FileProvider.getUriForFile(ctx,ctx.packageName+".files",temp);photo=uri;camera.launch(uri)}.onFailure{cleanup();error="Cámara no disponible"}}){Icon(Icons.Default.CameraAlt,null);Text(" Cámara")}
            OutlinedButton(enabled=!busy,onClick={gallery.launch("image/*")}){Icon(Icons.Default.Image,null);Text(" Galería")}
        }
        photo?.let{LocalPhoto(it.toString(),Modifier.fillMaxWidth().height(150.dp))}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(enabled=photo!=null&&!busy,onClick={if(!AiConfig.configured(ctx,uid))connect=true else consent=true}){Text("Reconocer con Gemini")}},dismissButton={TextButton(enabled=!busy,onClick={cleanup();close()}){Text("Cancelar")}})
    if(consent)Confirm("Enviar foto","Se enviará la imagen de la receta a Gemini con tu clave personal, en una sola consulta. No se guardará la imagen en el recetario.",{consent=false},{scan()})
    if(connect)PersonalGeminiDialog(uid){connect=false}
}
