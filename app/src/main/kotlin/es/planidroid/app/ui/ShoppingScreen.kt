package es.planidroid.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.planidroid.app.sync.AiConfig
import es.planidroid.app.sync.Cloud
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun ShoppingScreen(shopping:List<Shopping>,history:List<Purchase>,inventory:List<Item>,model:PlanidroidModel,listId:String="",listName:String="Mi compra",back:()->Unit={},share:(()->Unit)?=null,removeList:(()->Unit)?=null,renameList:(()->Unit)?=null){
    var paste by remember(listId){mutableStateOf(false)}
    var search by remember(listId){mutableStateOf<String?>(null)}
    var scan by remember(listId){mutableStateOf(false)};var quick by remember(listId){mutableStateOf("")}
    var view by remember(listId){mutableStateOf("Lista")};var edit by remember{mutableStateOf<Shopping?>(null)};var undo by remember{mutableStateOf<Purchase?>(null)}
    var selectedIds by remember(listId){mutableStateOf(emptySet<String>())};var selecting by remember(listId){mutableStateOf(false)};var confirmDelete by remember{mutableStateOf(false)}
    var recipes by remember{mutableStateOf(false)};var shoppingAi by remember{mutableStateOf(false)};var menu by remember{mutableStateOf(false)}
    val uid by model.scope.collectAsStateWithLifecycle()
    val pending=shopping.filter{ShoppingLogic.needed(it,inventory.find{p->p.id==it.itemId})>0}
    val shown=(if(view=="Rutina")shopping.filter{it.routine}else pending).filter{matchesShopping(it.name,search)}
    val visibleIds=shown.map{it.id}.toSet();val selected=selectedIds.intersect(visibleIds)
    fun select(id:String){selecting=true;selectedIds=if(id in selectedIds)selectedIds-id else selectedIds+id}
    fun clear(){selecting=false;selectedIds=emptySet()}
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=back){Icon(Icons.Default.ArrowBack,"Volver a listas")}
            Column(Modifier.weight(1f)){Text(listName,style=MaterialTheme.typography.titleLarge);Text(pending.size.toString()+" pendientes",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(share!=null)IconButton(onClick=share){Icon(Icons.Default.Share,"Compartir")}
            Box{IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Acciones de lista")};DropdownMenu(menu,{menu=false}){
                DropdownMenuItem(text={Text("Añadir con detalles")},onClick={menu=false;edit=Shopping(name="",unit="",listId=listId,quantitySpecified=false)})
                DropdownMenuItem(text={Text("Buscar productos")},onClick={menu=false;search=""})
                DropdownMenuItem(text={Text("Pegar varios productos")},onClick={menu=false;paste=true})
                if(renameList!=null)DropdownMenuItem(text={Text("Renombrar lista")},onClick={menu=false;renameList()})
                if(removeList!=null)DropdownMenuItem(text={Text("Eliminar lista",color=MaterialTheme.colorScheme.error)},onClick={menu=false;removeList()})
                if(view!="Historial")DropdownMenuItem(text={Text("Seleccionar productos")},onClick={menu=false;selecting=true})
            }}
        }
        search?.let{ShoppingSearch(it,{value->search=value},{search=null;clear()})}
        if(search==null&&!selecting&&view=="Lista")ShoppingInput(quick,{quick=it.take(500)},{model.saveShopping(ShoppingText.parse(quick,listId));quick=""},{scan=true})
        if(search==null&&!selecting&&view=="Lista")ShoppingAiActions({recipes=true},{shoppingAi=true})
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Lista","Rutina","Historial").forEach{v->FilterChip(view==v,{view=v;clear()},label={Text(v)})}}
        if(selecting)ShoppingSelection(selected.size,shown.size,{selectedIds=if(selected==visibleIds)emptySet()else visibleIds},::clear,{confirmDelete=true})
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            if(view=="Historial"){
                if(history.isEmpty())item{Text("Todavía no hay compras.",modifier=Modifier.padding(12.dp))}
                history.filter{matchesShopping(it.name,search)}.sortedByDescending{it.boughtAt}.groupBy{sessionDate(it.boughtAt)}.forEach{(day,list)->
                    item{Text(day.format(DateTimeFormatter.ofPattern("d MMMM yyyy",java.util.Locale("es","ES"))),style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(top=10.dp,bottom=4.dp))}
                    items(list,key={it.id}){p->
                        var actions by remember(p.id){mutableStateOf(false)}
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(12.dp)).padding(8.dp),verticalAlignment=Alignment.CenterVertically){
                            ProductGlyph(p.name,Modifier.size(26.dp));Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(p.name);Text((if(p.quantitySpecified)amountText(p.quantity)+" "+p.unit+" · " else "")+time(p.boughtAt)+(if(p.undone)" · Anulada" else ""),style=MaterialTheme.typography.bodySmall)}
                            Box{IconButton(onClick={actions=true}){Icon(Icons.Default.MoreVert,"Opciones de compra")};DropdownMenu(actions,{actions=false}){DropdownMenuItem(text={Text("Volver a añadir")},onClick={actions=false;model.repeatPurchase(p,false)});if(!p.undone)DropdownMenuItem(text={Text("Deshacer compra")},onClick={actions=false;undo=p})}}
                        }
                    }
                }
            }else{
                if(shown.isEmpty())item{Text(if(!search.isNullOrBlank())"Sin coincidencias" else if(view=="Rutina")"No hay compras habituales" else "Todo al día",modifier=Modifier.padding(12.dp),style=MaterialTheme.typography.titleMedium)}
                shown.groupBy{productType(it.name)}.toSortedMap(compareBy{productLabel(it)}).forEach{(type,products)->
                    item{Text(productLabel(type),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=12.dp,bottom=4.dp,start=4.dp))}
                    items(products.sortedBy{it.name.lowercase()},key={it.id}){p->
                        val i=inventory.find{it.id==p.itemId};val n=ShoppingLogic.needed(p,i)
                        val quantity=if(p.quantitySpecified||(p.routine&&i!=null&&i.amount<p.minimum))(amountText(if(n>0)n else p.quantity)+" "+p.unit).trim()else ""
                        val detail=listOfNotNull(quantity.takeIf{it.isNotBlank()},i?.let{"Stock "+amountText(it.amount)}).joinToString(" · ")
                        ShoppingRow(p,detail,selecting,p.id in selected,buy=if(view=="Lista")({model.buy(p.id)})else null,select={select(p.id)},edit={edit=p},remove={selectedIds=setOf(p.id);selecting=true;confirmDelete=true},repeat=if(view=="Rutina"&&n<=0)({model.save(p.copy(pending=true))})else null)
                    }
                }
            }
        }
    }
    if(confirmDelete)Confirm("Eliminar "+selected.size+" productos","No se registrarán como compras; el historial anterior se conserva.",{confirmDelete=false},{model.deleteShopping(selected,listId);clear()})
    if(shoppingAi)ShoppingAssistant(uid,pending,listId,{shoppingAi=false},{model.saveShopping(it)})
    if(recipes)RecipeSuggestions(uid,pending,listId,{recipes=false},{model.saveShopping(it)})
    if(paste)ShoppingPaste(listId,{paste=false},{model.saveShopping(it);paste=false})
    if(scan)ShoppingScan(listId,{scan=false},{model.saveShopping(it);scan=false})
    edit?.let{p->ShoppingEditor(p,inventory,{edit=null},{model.save(it);edit=null})}
    undo?.let{p->Confirm("Deshacer compra","Volverá a la lista y se revertirá el stock si no ha cambiado.",{undo=null},{model.repeatPurchase(p,true)})}
}

@Composable fun ShoppingEditor(initial:Shopping,inventory:List<Item>,close:()->Unit,save:(Shopping)->Unit){val ctx=LocalContext.current;var s by remember(initial){mutableStateOf(initial)};var q by remember{mutableStateOf(if(s.quantitySpecified)amountText(s.quantity)else "")};var min by remember{mutableStateOf(amountText(s.minimum))};var target by remember{mutableStateOf(amountText(s.target))};val n=q.replace(',','.').toDoubleOrNull();val m=min.replace(',','.').toDoubleOrNull();val t=target.replace(',','.').toDoubleOrNull();val ok=s.name.isNotBlank()&&((q.isBlank()&&s.itemId.isBlank())||(n!=null&&n.isFinite()&&n>0))&&m!=null&&m.isFinite()&&m>=0&&t!=null&&t.isFinite()&&t>=m
    AlertDialog(onDismissRequest=close,title={Text("Producto de compra")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(44.dp).background(soft(productAccent(productType(s.name)),.20f),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){ProductGlyph(s.name,Modifier.size(28.dp))};Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Field(s.name,{s=s.copy(name=it,icon=guessIcon(it),itemId=if(it==s.name)s.itemId else "",referenceUrl=if(it==s.name)s.referenceUrl else "",referenceTitle=if(it==s.name)s.referenceTitle else "")},"Nombre")}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Field(q,{q=it},"Cantidad")};Column(Modifier.weight(1f)){UnitSelector(s.unit,{s=s.copy(unit=it);if(it.isNotBlank()&&q.isBlank())q="1"},enabled=s.itemId.isBlank())}};if(s.referenceUrl.isNotBlank())TextButton(onClick={openShoppingReference(ctx,s.referenceUrl)}){Icon(Icons.Default.OpenInNew,null);Text(s.referenceTitle.ifBlank{"Referencia web"},maxLines=1)};Row(verticalAlignment=Alignment.CenterVertically){Switch(s.routine,{s=s.copy(routine=it)});Text(" Compra de rutina")};if(!s.routine&&s.itemId.isNotBlank())Row(verticalAlignment=Alignment.CenterVertically){Text("Vinculado al inventario",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium);TextButton(onClick={s=s.copy(itemId="")}){Text("Desvincular")}};if(s.routine){Choice("Vincular al inventario",listOf("")+inventory.map{it.id},s.itemId,{id->inventory.find{it.id==id}?.name?:"Sin vincular"}){id->val i=inventory.find{it.id==id};s=s.copy(itemId=id,name=i?.name?:s.name,unit=i?.unit?:s.unit,icon=i?.icon?:s.icon,referenceUrl=if(i==null||i.name==s.name)s.referenceUrl else "",referenceTitle=if(i==null||i.name==s.name)s.referenceTitle else "")};if(s.itemId.isNotBlank()){Field(min,{min=it},"Stock mínimo");Field(target,{target=it},"Stock deseado");Text("Al comprar, el stock vinculado aumentará.",style=MaterialTheme.typography.bodySmall)}}}},confirmButton={TextButton(enabled=ok,onClick={save(s.copy(name=s.name.trim(),quantity=n?:1.0,quantitySpecified=q.isNotBlank(),unit=if(q.isBlank()&&s.itemId.isBlank())"" else SpanishUnits.canonical(s.unit)?:s.unit,minimum=m!!,target=t!!,itemId=s.itemId))}){Text("Guardar")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})
}

@Composable fun ShoppingScan(listId:String,close:()->Unit,save:(List<Shopping>)->Unit){
    val ctx=LocalContext.current;val scope=rememberCoroutineScope()
    var draft by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    var file by remember{mutableStateOf<java.io.File?>(null)}
    var photoUri by remember{mutableStateOf<android.net.Uri?>(null)}
    val uid=Cloud.auth(ctx)?.currentUser?.uid?:"local"
    var ai by remember{mutableStateOf(false)}
    var consent by remember{mutableStateOf(false)}
    var connect by remember{mutableStateOf(false)}
    suspend fun readLocally(uri:android.net.Uri):String {
        val reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try{
            val image=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){InputImage.fromFilePath(ctx,uri)}
            return reader.process(image).await().text.take(16000)
        }finally{reader.close()}
    }
    fun recognize(uri:android.net.Uri){
        busy=true;error=""
        val useGemini=ai
        scope.launch{
            try{
                val suggested=if(useGemini)try{
                    val bytes=shoppingPhotoBytes(ctx,uri)
                    try{AiConfig.provider(ctx,uid).shoppingText("Transcribe esta lista de compra manuscrita.",bytes)}
                    finally{bytes.fill(0)}
                }catch(cancellation:CancellationException){throw cancellation}
                catch(_:Exception){""}else ""
                draft=suggested.ifBlank{readLocally(uri)}
                error=when{
                    draft.isBlank()->"No se ha detectado texto. Prueba con mejor luz o escribe los productos."
                    useGemini&&suggested.isBlank()->"Se usó el reconocimiento del teléfono. Revisa la transcripción."
                    else->""
                }
            }catch(cancellation:CancellationException){throw cancellation}
            catch(_:Exception){error="No se pudo leer la imagen. Puedes escribir los productos."}
            finally{busy=false;file?.delete();file=null;photoUri=null}
        }
    }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->
        if(ok)photoUri?.let{recognize(it)}else{file?.delete();file=null;photoUri=null}
    }
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)recognize(uri)}
    val proposed=remember(draft,listId){ShoppingText.parse(draft,listId)}
    AlertDialog(onDismissRequest={if(!busy){file?.delete();close()}},title={Text("Leer la pizarra")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("Haz una foto o elige una imagen. Revisa los productos antes de añadirlos.")
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(ai,{v->if(!v)ai=false else if(!AiConfig.configured(ctx,uid))connect=true else consent=true},enabled=!busy);Text("Reconocer con Gemini (opcional)")}
            Text(if(ai)"La imagen se enviará directamente a Gemini." else "OCR en el teléfono · sin conexión",style=MaterialTheme.typography.bodySmall)
            ActionFlow {
                Button(enabled=!busy,onClick={
                    runCatching{
                        val dir=java.io.File(ctx.filesDir,"captures").apply{mkdirs()}
                        val tmp=java.io.File.createTempFile("shopping-",".jpg",dir);file=tmp
                        val uri=FileProvider.getUriForFile(ctx,ctx.packageName+".files",tmp);photoUri=uri;camera.launch(uri)
                    }.onFailure{file?.delete();file=null;photoUri=null;error="Cámara no disponible"}
                }){Text("Cámara")}
                OutlinedButton(enabled=!busy,onClick={gallery.launch("image/*")}){Text("Galería")}
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(draft,{draft=it.take(16000)},Modifier.fillMaxWidth(),minLines=4,label={Text("Un producto por línea")},enabled=!busy)
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            proposed.take(10).forEach{p->Row(verticalAlignment=Alignment.CenterVertically){ProductGlyph(p.name,Modifier.size(22.dp));Spacer(Modifier.width(8.dp));Text(p.name,Modifier.weight(1f));if(p.quantitySpecified)Text((amountText(p.quantity)+" "+p.unit).trim())}}
            if(proposed.size>10)Text("+"+(proposed.size-10)+" productos")
        }
    },confirmButton={TextButton(enabled=!busy&&proposed.isNotEmpty(),onClick={save(proposed)}){Text("Añadir "+proposed.size+" productos")}},dismissButton={TextButton(enabled=!busy,onClick={file?.delete();close()}){Text("Cancelar")}})
    if(connect)PersonalGeminiDialog(uid){connect=false}
    if(consent)Confirm("Usar Gemini","La próxima foto se enviará a Google con tu clave personal. Comprueba que solo contiene la lista.",{consent=false},{ai=true})
}
