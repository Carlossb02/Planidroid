package es.planidroid.app.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import es.planidroid.app.R
import es.planidroid.app.domain.*
import java.time.LocalDate
import kotlin.math.roundToInt

fun illustration(type:FurnitureType)=when(type){FurnitureType.FRIDGE->R.drawable.fridge;FurnitureType.FREEZER->R.drawable.freezer;FurnitureType.WARDROBE->R.drawable.wardrobe;FurnitureType.DRAWER->R.drawable.drawer;FurnitureType.PANTRY->R.drawable.pantry;FurnitureType.SHELF->R.drawable.shelf;FurnitureType.CABINET->R.drawable.cabinet;FurnitureType.BOX->R.drawable.box;FurnitureType.WASHER->R.drawable.washer;FurnitureType.OTHER->R.drawable.other}
@Composable fun LocalPhoto(uri:String,modifier:Modifier=Modifier){val ctx=LocalContext.current;var bitmap by remember(uri){mutableStateOf<android.graphics.Bitmap?>(null)};LaunchedEffect(uri){bitmap=withContext(Dispatchers.IO){runCatching{val u=Uri.parse(uri);val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true};ctx.contentResolver.openInputStream(u)?.use{BitmapFactory.decodeStream(it,null,opts)};var sample=1;while(maxOf(opts.outWidth,opts.outHeight)/sample>1200)sample*=2;ctx.contentResolver.openInputStream(u)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}}.getOrNull()}};bitmap?.let{Image(it.asImageBitmap(),"Fotografía local",modifier.clip(RoundedCornerShape(16.dp)),contentScale=ContentScale.Crop)}?:Text("Foto no disponible",style=MaterialTheme.typography.bodySmall)}
@Composable fun InventoryScreen(furniture:List<Furniture>,inventory:List<Item>,model:PlanidroidModel,homeId:String="",homeName:String="Mi hogar",back:()->Unit={},onShare:((Furniture)->Unit)?=null,lists:List<ShoppingList> = emptyList()){
    val uid by model.scope.collectAsState()
    var consume by remember(uid){mutableStateOf<Item?>(null)};var restock by remember(uid){mutableStateOf<Item?>(null)}
    var query by remember{mutableStateOf("")};var selected by remember{mutableStateOf<String?>(null)};var editing by remember{mutableStateOf<Furniture?>(null)};var itemEdit by remember{mutableStateOf<Item?>(null)};var removeFurniture by remember{mutableStateOf<Furniture?>(null)};var removeItem by remember{mutableStateOf<Item?>(null)};var zoneEdit by remember{mutableStateOf(false)};var mode by remember{mutableStateOf("Visual")};var chosen by remember{mutableStateOf<String?>(null)};var scan by remember{mutableStateOf(false)}
    BackHandler(selected!=null){selected=null;chosen=null}
    val f=furniture.find{it.id==selected};val soon=LocalDate.now().plusDays(7).toString();val expiring=inventory.filter{it.amount>0&&it.expiry.isNotBlank()&&it.expiry<=soon}.sortedBy{it.expiry}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        val exhausted=inventory.filter{InventoryConsumption.pending(it)&&(f==null||it.furnitureId==f.id)}
        if(exhausted.isNotEmpty())item(key="restock"){RestockBanner(exhausted,{restock=it},{model.dismissRestock(it)})}
        if(f==null){item{TextButton(onClick=back){Text("‹ Casas")};Section(homeName);Field(query,{query=it},"Buscar en casa");Button(onClick={editing=Furniture(name="",type=FurnitureType.FRIDGE,homeId=homeId)}){Icon(Icons.Default.Add,null);Text(" Añadir mueble")};if(expiring.isNotEmpty()&&query.isBlank())GlassCard(tint=Peach){Text("Caducidad próxima",style=MaterialTheme.typography.titleMedium);expiring.take(5).forEach{i->TextButton(onClick={selected=i.furnitureId}){Text("${i.name} · ${i.expiry}")}}}};items(furniture.filter{m->query.isBlank()||listOf(m.name,m.room,m.type.label).any{it.contains(query,true)}||inventory.any{it.furnitureId==m.id&&it.name.contains(query,true)}},key={it.id}){m->GlassCard(Modifier.clickable{selected=m.id;chosen=null},Mint){Row(verticalAlignment=Alignment.CenterVertically){if(m.photo.isNotBlank())LocalPhoto(m.photo,Modifier.size(90.dp))else FurnitureGlyph(m.type,Modifier.size(58.dp));Column(Modifier.weight(1f)){Text(m.name,style=MaterialTheme.typography.titleLarge);Text(m.room.ifBlank{m.type.label});Text("${inventory.count{it.furnitureId==m.id}} elementos",style=MaterialTheme.typography.labelMedium)};Icon(Icons.Default.ChevronRight,null)};if(query.isNotBlank())inventory.filter{it.furnitureId==m.id&&it.name.contains(query,true)}.forEach{Text("${it.name} · ${it.compartment}")}}}}
        else{val all=inventory.filter{it.furnitureId==f.id};val zones=LayoutGrid.zones(f);item{TextButton(onClick={selected=null;chosen=null}){Text("‹ Todos los muebles")};GlassCard(tint=Mint){Row(verticalAlignment=Alignment.CenterVertically){if(f.photo.isNotBlank())LocalPhoto(f.photo,Modifier.size(80.dp))else FurnitureGlyph(f.type,Modifier.size(56.dp));Column(Modifier.weight(1f)){Text(f.name,style=MaterialTheme.typography.headlineSmall);Text("${all.size} elementos · ${f.room.ifBlank{f.type.label}}")};IconButton(onClick={editing=f}){Icon(Icons.Default.Edit,"Editar mueble")}};Row{TextButton(onClick={zoneEdit=true}){Text("Configurar interior")};if(onShare!=null)IconButton(onClick={onShare(f)}){Icon(Icons.Default.Share,"Compartir mueble")};TextButton(onClick={removeFurniture=f}){Text("Eliminar")}}};Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("Visual","Lista","Foto").forEach{v->FilterChip(mode==v,{mode=v;chosen=null},label={Text(v)})}};if(mode=="Visual"){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Text("Interior",style=MaterialTheme.typography.titleMedium);ZoneBoard(zones.filterNot{it.door},all,false,chosen,{chosen=it.name},{})};Column(Modifier.weight(1f)){Text("Puerta",style=MaterialTheme.typography.titleMedium);ZoneBoard(zones.filter{it.door},all,false,chosen,{chosen=it.name},{})}};if(chosen!=null)TextButton(onClick={chosen=null}){Text("Mostrar todos los compartimentos")}}else if(mode=="Foto"){if(f.photo.isNotBlank())LocalPhoto(f.photo,Modifier.fillMaxWidth().height(240.dp))else Text("Añade una fotografía real desde Editar.")};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={itemEdit=Item(furnitureId=f.id,name="",compartment=chosen?:f.compartments.firstOrNull()?:"General")}){Icon(Icons.Default.Add,null);Text(" Contenido")};OutlinedButton(onClick={scan=true}){Icon(Icons.Default.CameraAlt,null);Text(" Reconocer foto")}}}
            val sections=if(chosen==null)(f.compartments+all.map{it.compartment}).distinct() else listOf(chosen!!);sections.forEach{comp->item{Text(comp,style=MaterialTheme.typography.titleLarge);if(all.none{it.compartment==comp})Text("Vacío",style=MaterialTheme.typography.bodySmall)};items(all.filter{it.compartment==comp}.sortedBy{it.name.lowercase()},key={it.id}){i->GlassCard(Modifier.clickable{itemEdit=i},Mint){Row(verticalAlignment=Alignment.CenterVertically){if(i.photo.isNotBlank())LocalPhoto(i.photo,Modifier.size(52.dp))else ProductGlyph(i.name,Modifier.size(34.dp));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(i.name,style=MaterialTheme.typography.titleMedium);Text("${amountText(i.amount)} ${i.unit}");if(i.expiry.isNotBlank())Text(i.expiry,color=if(i.expiry<=soon)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick={removeItem=i}){Icon(Icons.Default.DeleteOutline,"Quitar")}};ConsumeButton(i,{consume=i})}}}
        }
    }
    consume?.let{i->ConsumeDialog(i,{consume=null},{n,done->model.consumeItem(i,n,done)})}
    restock?.let{i->InventoryRestockDialog(uid,i,lists,{restock=null},{products,listId,done->model.restockItem(i,products,listId,finished=done)})}
    editing?.let{m->FurnitureEditor(m,{editing=null},{model.save(it);editing=null})};itemEdit?.let{i->furniture.find{it.id==i.furnitureId}?.let{m->ItemEditor(i,m,{itemEdit=null},{model.save(it);itemEdit=null})}};if(zoneEdit&&f!=null)LayoutEditor(f,inventory.filter{it.furnitureId==f.id},{zoneEdit=false},{model.save(it);zoneEdit=false;chosen=null});if(scan&&f!=null)PhotoScan(f,chosen?:f.compartments.firstOrNull()?:"General",{scan=false},{items->items.forEach{model.save(it)};scan=false});removeFurniture?.let{m->Confirm("Eliminar ${m.name}","Se eliminará también su contenido.",{removeFurniture=null},{model.delete(m.id,Kind.FURNITURE);selected=null})};removeItem?.let{i->Confirm("Eliminar ${i.name}","Se quitará del inventario.",{removeItem=null},{model.delete(i.id,Kind.ITEM)})}
}
@Composable fun ZoneBoard(zones:List<Zone>,items:List<Item>,editing:Boolean,selected:String?,tap:(Zone)->Unit,move:(Zone)->Unit){
    BoxWithConstraints(Modifier.fillMaxWidth().height(288.dp).clip(RoundedCornerShape(18.dp)).background(soft(Mint,.08f)).border(1.dp,soft(Mint,.30f),RoundedCornerShape(18.dp))){val cellW=maxWidth/LayoutGrid.WIDTH;val cellH=36.dp
        val cellWidthPx=with(LocalDensity.current){cellW.toPx()};val cellHeightPx=with(LocalDensity.current){cellH.toPx()}
        zones.forEach{z->key(z.name){var dx by remember(z){mutableFloatStateOf(0f)};var dy by remember(z){mutableFloatStateOf(0f)};val drag=if(editing)Modifier.pointerInput(z,zones,cellWidthPx,cellHeightPx){detectDragGesturesAfterLongPress(onDragStart={dx=0f;dy=0f;tap(z)},onDragCancel={dx=0f;dy=0f},onDragEnd={val candidate=z.copy(x=z.x+(dx/cellWidthPx).roundToInt(),y=z.y+(dy/cellHeightPx).roundToInt());dx=0f;dy=0f;move(candidate)}){change,delta->change.consume();dx+=delta.x;dy+=delta.y}}else Modifier
            val inside=items.filter{it.compartment==z.name};Surface(Modifier.offset(cellW*z.x,cellH*z.y).offset{IntOffset(dx.roundToInt(),dy.roundToInt())}.size(cellW*z.w,cellH*z.h).padding(2.dp).then(drag).clickable{tap(z)},shape=RoundedCornerShape(if(z.type==ZoneType.DRAWER)5.dp else 10.dp),color=soft(if(z.type==ZoneType.DRAWER)Blue else Mint,if(selected==z.name).36f else .19f),border=BorderStroke(1.dp,soft(Mint,.42f))){Column(Modifier.padding(horizontal=4.dp,vertical=2.dp),verticalArrangement=Arrangement.Center){Text(z.name,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis);if(inside.isNotEmpty())Text(inside.take(5).joinToString(""){it.icon},style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Clip)}}
        }};if(zones.isEmpty())Text("Sin zonas",Modifier.align(Alignment.Center),style=MaterialTheme.typography.labelSmall)
    }
}
@Composable private fun ZoneDimension(label:String,value:Int,limit:Int,canDecrease:Boolean,canIncrease:Boolean,change:(Int)->Unit){
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text(label,style=MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){
                    repeat(limit){index->Box(Modifier.weight(1f).height(5.dp).background(if(index<value)Mint else soft(Mint,.12f),RoundedCornerShape(3.dp)))}
                }
            }
            OutlinedIconButton(enabled=canDecrease,onClick={change(value-1)}){Icon(Icons.Default.Remove,"Reducir "+label.lowercase())}
            Text(value.toString(),style=MaterialTheme.typography.titleLarge,modifier=Modifier.width(24.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            FilledTonalIconButton(enabled=canIncrease,onClick={change(value+1)}){Icon(Icons.Default.Add,"Aumentar "+label.lowercase())}
        }
    }
}

@Composable fun LayoutEditor(f:Furniture,items:List<Item>,close:()->Unit,save:(Furniture)->Unit){
    var zones by remember(f.id){mutableStateOf(LayoutGrid.zones(f))}
    var sel by remember(f.id){mutableStateOf<String?>(null)}
    var type by remember(f.id){mutableStateOf(ZoneType.SHELF)}
    var customName by remember(f.id){mutableStateOf<String?>(null)}
    var door by remember(f.id){mutableStateOf(false)}
    var err by remember(f.id){mutableStateOf("")}
    val defaultName=LayoutGrid.defaultName(type,zones)
    val name=customName?:defaultName
    val z=zones.find{it.name==sel}
    fun move(n:Zone){
        if(LayoutGrid.fits(n,zones)){zones=zones.map{if(it.name==n.name)n else it};err=""}
        else err="Ese espacio no está libre o supera el mueble."
    }
    androidx.compose.ui.window.Dialog(onDismissRequest=close,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize()){
            LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                item{
                    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Diseña tu mueble",style=MaterialTheme.typography.headlineMedium);Text(f.name,color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick=close){Icon(Icons.Default.Close,"Cerrar")}}
                    Text("Mantén pulsada una zona y arrástrala. Toca para cambiar su tamaño.",style=MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        Column(Modifier.weight(1f)){Text("Interior",style=MaterialTheme.typography.titleMedium);Spacer(Modifier.height(6.dp));ZoneBoard(zones.filterNot{it.door},items,true,sel,{sel=it.name;err=""},::move)}
                        Column(Modifier.weight(1f)){Text("Puerta",style=MaterialTheme.typography.titleMedium);Spacer(Modifier.height(6.dp));ZoneBoard(zones.filter{it.door},items,true,sel,{sel=it.name;err=""},::move)}
                    }
                    if(err.isNotBlank())Text(err,color=MaterialTheme.colorScheme.error)
                }
                item{
                    z?.let{a->
                        GlassCard(tint=Mint){
                            Text(a.name,style=MaterialTheme.typography.titleLarge)
                            Choice("Tipo",ZoneType.entries,a.type,{it.label}){move(a.copy(type=it))}
                            Text("Tamaño",style=MaterialTheme.typography.titleMedium)
                            ZoneDimension("Ancho",a.w,LayoutGrid.WIDTH,LayoutGrid.fits(a.copy(w=a.w-1),zones),LayoutGrid.fits(a.copy(w=a.w+1),zones)){move(a.copy(w=it))}
                            ZoneDimension("Alto",a.h,LayoutGrid.HEIGHT,LayoutGrid.fits(a.copy(h=a.h-1),zones),LayoutGrid.fits(a.copy(h=a.h+1),zones)){move(a.copy(h=it))}
                            HorizontalDivider()
                            Row(verticalAlignment=Alignment.CenterVertically){
                                Text("Ubicación: "+if(a.door)"puerta" else "interior",Modifier.weight(1f))
                                TextButton(onClick={move(a.copy(door=!a.door))},enabled=LayoutGrid.fits(a.copy(door=!a.door),zones)){Text(if(a.door)"Interior" else "Puerta")}
                            }
                            TextButton(enabled=items.none{it.compartment==a.name}&&zones.size>1,onClick={zones=zones.filterNot{it.name==a.name};sel=null;err=""}){Text("Quitar zona vacía",color=if(items.none{it.compartment==a.name}&&zones.size>1)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)}
                        }
                    }
                }
                item{
                    GlassCard{
                        Text("Nueva zona",style=MaterialTheme.typography.titleLarge)
                        Choice("Tipo",ZoneType.entries,type,{it.label}){type=it;err=""}
                        Field(name,{customName=it.take(80)},"Nombre")
                        if(customName!=null)TextButton(onClick={customName=null}){Text("Usar nombre del tipo")}
                        Row(verticalAlignment=Alignment.CenterVertically){Switch(door,{door=it});Text(" En la puerta")}
                        Button(enabled=name.isNotBlank()&&zones.none{it.name.equals(name.trim(),true)},modifier=Modifier.fillMaxWidth(),onClick={
                            val n=LayoutGrid.place(name.trim(),type,door,zones,if(type==ZoneType.BASKET)2 else 4,if(type==ZoneType.DRAWER)2 else 1)
                            if(n==null)err="No queda espacio para esa zona." else{zones=zones+n;sel=n.name;customName=null;err=""}
                        }){Icon(Icons.Default.Add,null);Text(" Añadir zona")}
                    }
                }
                item{
                    Button(modifier=Modifier.fillMaxWidth(),onClick={save(f.copy(zones=zones,compartments=(zones.map{it.name}+items.map{it.compartment}).distinct()))}){Text("Guardar distribución")}
                    TextButton(onClick=close,modifier=Modifier.fillMaxWidth()){Text("Cancelar")}
                }
            }
        }
    }
}
@Composable fun FurnitureEditor(initial:Furniture,close:()->Unit,save:(Furniture)->Unit){var f by remember(initial){mutableStateOf(initial)};val ctx=LocalContext.current;val photo=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null){runCatching{ctx.contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)};f=f.copy(photo=u.toString())}};AlertDialog(onDismissRequest=close,title={Text("Mueble o electrodoméstico")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Field(f.name,{f=f.copy(name=it)},"Nombre");Choice("Tipo",FurnitureType.entries,f.type,{it.label}){f=f.copy(type=it)};if(f.photo.isNotBlank())LocalPhoto(f.photo,Modifier.fillMaxWidth().height(130.dp))else FurnitureGlyph(f.type,Modifier.size(72.dp).align(Alignment.CenterHorizontally));TextButton(onClick={photo.launch(arrayOf("image/*"))}){Text("Seleccionar fotografía real")};Field(f.room,{f=f.copy(room=it)},"Estancia");Field(f.notes,{f=f.copy(notes=it)},"Notas",false)}},confirmButton={TextButton(enabled=f.name.isNotBlank(),onClick={val z=if(initial.name.isBlank()&&f.type==FurnitureType.FRIDGE)listOf(Zone("Balda",y=0,h=2),Zone("Balda 2",y=2,h=2),Zone("Cajón",ZoneType.DRAWER,y=4,h=2),Zone("Balda 3",door=true,y=0,h=2),Zone("Botellero",ZoneType.RACK,door=true,y=3,h=2))else if(initial.name.isBlank()&&f.zones.isEmpty()&&f.compartments==listOf("General"))listOf(Zone(ZoneType.SHELF.label))else LayoutGrid.zones(f);save(f.copy(name=f.name.trim(),zones=z,compartments=z.map{it.name}))}){Text("Guardar")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})}
@Composable fun ItemEditor(initial:Item,f:Furniture,close:()->Unit,save:(Item)->Unit){var i by remember(initial){mutableStateOf(initial)};var qty by remember{mutableStateOf(amountText(i.amount))};val ctx=LocalContext.current;val photo=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null){runCatching{ctx.contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)};i=i.copy(photo=u.toString())}};val n=qty.replace(',','.').toDoubleOrNull();val valid=i.name.isNotBlank()&&n!=null&&n.isFinite()&&n>=0&&(i.expiry.isBlank()||runCatching{LocalDate.parse(i.expiry)}.isSuccess);AlertDialog(onDismissRequest=close,title={Text("Contenido de ${f.name}")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Field(i.name,{i=i.copy(name=it,icon=guessIcon(it))},"Producto u objeto");Choice("Tipo de contenido",InventoryKind.entries,i.contentKind,{if(it==InventoryKind.AUTO)"Automático · "+InventoryConsumption.kind(i.copy(contentKind=InventoryKind.AUTO)).label else it.label}){i=i.copy(contentKind=it)};Choice("Compartimento",(f.compartments+i.compartment).distinct(),i.compartment,{it}){i=i.copy(compartment=it)};Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(3.dp)){productIcons.forEach{icon->FilterChip(i.icon==icon,{i=i.copy(icon=icon)},label={Text(icon)})}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Field(qty,{qty=it},"Cantidad")};Column(Modifier.weight(1f)){UnitSelector(i.unit,{i=i.copy(unit=it)},allowEmpty=false)}};DateField(i.expiry,{i=i.copy(expiry=it)},"Caducidad");Field(i.notes,{i=i.copy(notes=it)},"Notas",false);if(i.photo.isNotBlank())LocalPhoto(i.photo,Modifier.fillMaxWidth().height(100.dp));TextButton(onClick={photo.launch(arrayOf("image/*"))}){Text("Añadir fotografía")}}},confirmButton={TextButton(enabled=valid,onClick={save(i.copy(name=i.name.trim(),amount=n!!,quantity=amountText(n),unit=SpanishUnits.canonical(i.unit)?:i.unit))}){Text("Guardar")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})}
@Composable fun PhotoScan(f:Furniture,comp:String,close:()->Unit,save:(List<Item>)->Unit){val ctx=LocalContext.current;val scope=rememberCoroutineScope();var text by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var photo by remember{mutableStateOf("")};fun detect(input:InputImage){scope.launch{busy=true;error="";val labeler=ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);val reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);try{val labels=labeler.process(input).await().filter{it.confidence>=.6f}.map{it.text};val ocr=reader.process(input).await().text.lines().filter{it.isNotBlank()}.take(12);text=(labels+ocr).distinct().joinToString("\n");if(text.isBlank())error="No se identificó contenido. Puedes escribirlo manualmente."}catch(e:CancellationException){throw e}catch(e:Exception){error="No se pudo analizar la imagen."}finally{labeler.close();reader.close();busy=false}}};val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null){photo=u.toString();runCatching{InputImage.fromFilePath(ctx,u)}.onSuccess{detect(it)}.onFailure{error="Imagen no disponible"}}};val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()){b->if(b!=null)detect(InputImage.fromBitmap(b,0))};AlertDialog(onDismissRequest=close,title={Text("Reconocer contenido")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Análisis local de etiquetas visuales y texto. No identifica con certeza todos los alimentos ni sus cantidades. Edita las propuestas, una por línea.");Row{TextButton(onClick={pick.launch(arrayOf("image/*"))}){Text("Galería")};TextButton(onClick={runCatching{camera.launch(null)}.onFailure{error="Cámara no disponible"}}){Text("Cámara")}};if(photo.isNotBlank())LocalPhoto(photo,Modifier.fillMaxWidth().height(140.dp));if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());Field(text,{text=it},"Revisar productos",false);if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)}},confirmButton={TextButton(enabled=!busy&&text.isNotBlank(),onClick={save(text.lines().map{it.trim()}.filter{it.isNotBlank()}.distinct().map{Item(furnitureId=f.id,name=it,compartment=comp,icon=guessIcon(it))})}){Text("Confirmar contenido")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})}
