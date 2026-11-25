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

@Composable fun ShoppingScreen(shopping:List<Shopping>,history:List<Purchase>,inventory:List<Item>,model:PlanidroidModel,listId:String="",listName:String="Mi compra",back:()->Unit={},share:(()->Unit)?=null){
    var scan by remember{mutableStateOf(false)};var quick by remember{mutableStateOf("")};var view by remember{mutableStateOf("Lista")};var edit by remember{mutableStateOf<Shopping?>(null)};var remove by remember{mutableStateOf<Shopping?>(null)};var undo by remember{mutableStateOf<Purchase?>(null)}
    var selecting by remember(listId){mutableStateOf(false)};var selectedIds by remember(listId){mutableStateOf(emptySet<String>())};var confirmDelete by remember{mutableStateOf(false)}
    var recipes by remember{mutableStateOf(false)}
    val uid by model.scope.collectAsStateWithLifecycle()
    val pending=shopping.filter{ShoppingLogic.needed(it,inventory.find{p->p.id==it.itemId})>0}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            TextButton(onClick=back){Text("‹ Listas")}
            Row(verticalAlignment=Alignment.CenterVertically){Text(listName,Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium);if(share!=null)IconButton(onClick=share){Icon(Icons.Default.Share,"Compartir lista")}}
            Text(pending.size.toString()+" pendientes",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(quick,{quick=it},Modifier.weight(1f),singleLine=true,placeholder={Text("Añadir producto…")},shape=RoundedCornerShape(18.dp))
                FilledIconButton(onClick={model.saveShopping(ShoppingText.parse(quick,listId));quick=""},enabled=quick.isNotBlank()){Icon(Icons.Default.Add,"Añadir")}
                IconButton(onClick={scan=true}){Icon(Icons.Default.CameraAlt,"Leer pizarra")}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Lista","Rutina","Historial").forEach{v->FilterChip(view==v,{view=v;selecting=false;selectedIds=emptySet()},label={Text(v)})}}
            if(view!="Historial")Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={selecting=!selecting;selectedIds=emptySet()}){Text(if(selecting)"Cancelar selección" else "Seleccionar productos")};if(selecting)TextButton(onClick={selectedIds=(if(view=="Rutina")shopping.filter{it.routine}else pending).map{it.id}.toSet()}){Text("Todos")};if(selecting)IconButton(enabled=selectedIds.isNotEmpty(),onClick={confirmDelete=true}){Icon(Icons.Default.Delete,"Eliminar seleccionados",tint=MaterialTheme.colorScheme.error)}}
            if(view!="Historial")OutlinedButton(onClick={recipes=true}){Icon(Icons.Default.AutoAwesome,null);Text(" Recetas con Gemini")}
            if(view!="Historial")TextButton(onClick={edit=Shopping(name="",unit="",listId=listId,quantitySpecified=false)}){Text("Añadir con detalles")}
        }
        if(view=="Historial") {if(history.isEmpty())item{Text("Aún no hay compras registradas.")};history.sortedByDescending{it.boughtAt}.groupBy{sessionDate(it.boughtAt)}.forEach{(day,list)->item{Text(day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy",java.util.Locale("es","ES"))),style=MaterialTheme.typography.titleMedium)};items(list,key={it.id}){p->GlassCard(tint=Mint){Row(verticalAlignment=Alignment.CenterVertically){ProductGlyph(p.name,Modifier.size(24.dp));Spacer(Modifier.width(8.dp));Text(p.name,style=MaterialTheme.typography.titleMedium)};Text((if(p.quantitySpecified)amountText(p.quantity)+" "+p.unit+" · " else "")+time(p.boughtAt)+(if(p.undone)" · Anulada" else ""));Row{TextButton(onClick={model.repeatPurchase(p,false)}){Text("Volver a añadir")};if(!p.undone)TextButton(onClick={undo=p}){Text("Deshacer compra")}}}}}}
        else {val shown=if(view=="Rutina")shopping.filter{it.routine} else pending;if(shown.isEmpty())item{GlassCard(tint=Mint){Text(if(view=="Rutina")"Crea tus compras habituales" else "Todo al día",style=MaterialTheme.typography.titleMedium);Text("Las reposiciones aparecen cuando el stock baja del mínimo.")}};items(shown.sortedBy{it.name.lowercase()},key={it.id}){s->val i=inventory.find{it.id==s.itemId};val n=ShoppingLogic.needed(s,i);GlassCard(tint=Mint){Row(verticalAlignment=Alignment.CenterVertically){if(selecting)Checkbox(s.id in selectedIds,{v->selectedIds=if(v)selectedIds+s.id else selectedIds-s.id})else if(view=="Lista")Checkbox(false,{if(it)model.buy(s.id)});ProductGlyph(s.name,Modifier.size(28.dp));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(s.name,style=MaterialTheme.typography.titleMedium);if(s.quantitySpecified||(s.routine&&i!=null&&i.amount<s.minimum)) Text((amountText(if(n>0)n else s.quantity)+" "+s.unit).trim(),style=MaterialTheme.typography.labelMedium);if(i!=null)Text("Stock ${amountText(i.amount)} · mínimo ${amountText(s.minimum)}",style=MaterialTheme.typography.bodySmall)};if(!selecting)IconButton(onClick={edit=s}){Icon(Icons.Default.Edit,"Editar")}};Row{if(view=="Rutina"&&n<=0)TextButton(onClick={model.save(s.copy(pending=true))}){Text("Añadir a la lista")};if(!selecting)TextButton(onClick={remove=s}){Text("Eliminar")}}}}}
    }
    if(confirmDelete)Confirm("Eliminar "+selectedIds.size+" productos","Se quitarán de esta lista sin registrar compras. El historial anterior se conserva.",{confirmDelete=false},{model.deleteShopping(selectedIds,listId);selectedIds=emptySet();selecting=false})
    if(recipes)RecipeSuggestions(uid,pending,listId,{recipes=false},{model.saveShopping(it)})
    if(scan)ShoppingScan(listId,{scan=false},{model.saveShopping(it);scan=false})
    edit?.let{s->ShoppingEditor(s,inventory,{edit=null},{model.save(it);edit=null})};remove?.let{s->Confirm("Quitar ${s.name}","Se conserva el historial de compras.",{remove=null},{model.delete(s.id,Kind.SHOPPING)})};undo?.let{p->Confirm("Deshacer compra","Volverá a la lista. Se revertirá el stock si no ha cambiado después de esta compra.",{undo=null},{model.repeatPurchase(p,true)})}
}
@Composable fun ShoppingEditor(initial:Shopping,inventory:List<Item>,close:()->Unit,save:(Shopping)->Unit){var s by remember(initial){mutableStateOf(initial)};var q by remember{mutableStateOf(if(s.quantitySpecified)amountText(s.quantity)else "")};var min by remember{mutableStateOf(amountText(s.minimum))};var target by remember{mutableStateOf(amountText(s.target))};val n=q.replace(',','.').toDoubleOrNull();val m=min.replace(',','.').toDoubleOrNull();val t=target.replace(',','.').toDoubleOrNull();val ok=s.name.isNotBlank()&&(q.isBlank()||(n!=null&&n.isFinite()&&n>0))&&m!=null&&m.isFinite()&&m>=0&&t!=null&&t.isFinite()&&t>=m
    AlertDialog(onDismissRequest=close,title={Text("Producto de compra")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Field(s.name,{s=s.copy(name=it,icon=guessIcon(it))},"Nombre");Field(q,{q=it},"Cantidad");Field(s.unit,{s=s.copy(unit=it)},"Unidad");Row(verticalAlignment=Alignment.CenterVertically){Switch(s.routine,{s=s.copy(routine=it)});Text(" Compra de rutina")};if(s.routine){Choice("Vincular al inventario",listOf("")+inventory.map{it.id},s.itemId,{id->inventory.find{it.id==id}?.name?:"Sin vincular"}){id->val i=inventory.find{it.id==id};s=s.copy(itemId=id,name=i?.name?:s.name,unit=i?.unit?:s.unit,icon=i?.icon?:s.icon)};if(s.itemId.isNotBlank()){Field(min,{min=it},"Stock mínimo");Field(target,{target=it},"Stock deseado");Text("Al comprar, el stock vinculado aumentará.",style=MaterialTheme.typography.bodySmall)}}}},confirmButton={TextButton(enabled=ok,onClick={save(s.copy(name=s.name.trim(),quantity=n?:1.0,quantitySpecified=q.isNotBlank(),unit=if(q.isBlank())"" else s.unit,minimum=m!!,target=t!!,itemId=if(s.routine)s.itemId else ""))}){Text("Guardar")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})
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
    fun recognize(uri:android.net.Uri){
        busy=true;error=""
        scope.launch{
            val reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try{
                draft=if(ai){val bytes=shoppingPhotoBytes(ctx,uri);try{AiConfig.provider(ctx,uid).shoppingText("Transcribe esta lista de compra manuscrita.",bytes)}finally{bytes.fill(0)}}else {val image=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){InputImage.fromFilePath(ctx,uri)};reader.process(image).await().text.take(16000)}
                if(draft.isBlank())error="No se ha detectado texto. Prueba con mejor luz o escribe los productos."
            }catch(e:CancellationException){throw e}catch(_:Exception){error="No se pudo leer la imagen. Puedes usar reconocimiento sin IA o escribir los productos."}
            finally{reader.close();busy=false;file?.delete();file=null;photoUri=null}
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
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
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
