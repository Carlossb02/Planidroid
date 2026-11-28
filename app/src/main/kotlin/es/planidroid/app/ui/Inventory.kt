package es.planidroid.app.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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

fun illustration(type:FurnitureType)=when(type){FurnitureType.FRIDGE->R.drawable.fridge;FurnitureType.FREEZER->R.drawable.freezer;FurnitureType.WARDROBE->R.drawable.wardrobe;FurnitureType.DRAWER->R.drawable.drawer;FurnitureType.PANTRY->R.drawable.pantry;FurnitureType.SHELF->R.drawable.shelf;FurnitureType.CABINET->R.drawable.cabinet;FurnitureType.BOX->R.drawable.box;FurnitureType.WASHER->R.drawable.washer;FurnitureType.OTHER->R.drawable.other}
@Composable fun LocalPhoto(uri:String,modifier:Modifier=Modifier){val ctx=LocalContext.current;var bitmap by remember(uri){mutableStateOf<android.graphics.Bitmap?>(null)};LaunchedEffect(uri){bitmap=withContext(Dispatchers.IO){runCatching{val u=Uri.parse(uri);val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true};ctx.contentResolver.openInputStream(u)?.use{BitmapFactory.decodeStream(it,null,opts)};var sample=1;while(maxOf(opts.outWidth,opts.outHeight)/sample>1200)sample*=2;ctx.contentResolver.openInputStream(u)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}}.getOrNull()}};bitmap?.let{Image(it.asImageBitmap(),"Fotografía local",modifier.clip(RoundedCornerShape(16.dp)),contentScale=ContentScale.Crop)}?:Text("Foto no disponible",style=MaterialTheme.typography.bodySmall)}
@Composable fun InventoryScreen(furniture:List<Furniture>,inventory:List<Item>,model:PlanidroidModel,homeId:String="",homeName:String="Mi hogar",back:()->Unit={},onShare:((Furniture)->Unit)?=null,lists:List<ShoppingList> = emptyList(),directId:String?=null,moveTargets:List<Furniture> = emptyList()){
    val uid by model.scope.collectAsState()
    val preferences=LocalViewPreferences.current
    var menu by remember{mutableStateOf(false)}
    var inspect by remember{mutableStateOf(false)}
    var moving by remember(uid){mutableStateOf<Item?>(null)}
    var contentQuery by rememberSaveable(directId){mutableStateOf<String?>(null)}
    var consume by remember(uid){mutableStateOf<Item?>(null)};var restock by remember(uid){mutableStateOf<Pair<Item,Boolean>?>(null)}
    var query by remember{mutableStateOf("")};var selected by remember(directId){mutableStateOf(directId)};var editing by remember{mutableStateOf<Furniture?>(null)};var itemEdit by remember{mutableStateOf<Item?>(null)};var removeFurniture by remember{mutableStateOf<Furniture?>(null)};var removeItem by remember{mutableStateOf<Item?>(null)};var zoneEdit by remember{mutableStateOf(false)};var showPhoto by remember{mutableStateOf(false)};var scan by remember{mutableStateOf(false)}
    BackHandler(selected!=null&&directId==null){selected=null}
    val f=furniture.find{it.id==selected};val soon=LocalDate.now().plusDays(preferences.expiryDays.toLong()).toString();val expiring=inventory.filter{it.amount>0&&it.expiry.isNotBlank()&&it.expiry<=soon}.sortedBy{it.expiry}
    val exhausted=inventory.filter{InventoryConsumption.pending(it)&&(f==null||it.furnitureId==f.id)}
    Column(Modifier.fillMaxSize()){
        if(exhausted.isNotEmpty())Box(Modifier.padding(horizontal=16.dp,vertical=8.dp)){RestockBanner(exhausted,{i,recipe->restock=i to recipe},{model.dismissRestock(it)})}
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        if(f==null){item{TextButton(onClick=back){Text("‹ Casas")};Section(homeName);Field(query,{query=it},"Buscar en casa");Button(onClick={editing=Furniture(name="",type=FurnitureType.FRIDGE,homeId=homeId)}){Icon(Icons.Default.Add,null);Text(" Añadir mueble")};if(expiring.isNotEmpty()&&query.isBlank())GlassCard(tint=Peach){Text("Caducidad próxima",style=MaterialTheme.typography.titleMedium);expiring.take(5).forEach{i->TextButton(onClick={selected=i.furnitureId}){Text("${i.name} · ${i.expiry}")}}}};items(furniture.filter{m->query.isBlank()||listOf(m.name,m.room,m.type.label).any{it.contains(query,true)}||inventory.any{it.furnitureId==m.id&&it.name.contains(query,true)}},key={it.id}){m->GlassCard(Modifier.clickable{selected=m.id},Mint){Row(verticalAlignment=Alignment.CenterVertically){if(m.photo.isNotBlank())LocalPhoto(m.photo,Modifier.size(90.dp))else FurnitureGlyph(m.type,Modifier.size(58.dp));Column(Modifier.weight(1f)){Text(m.name,style=MaterialTheme.typography.titleLarge);Text(m.room.ifBlank{m.type.label});Text("${inventory.count{it.furnitureId==m.id}} elementos",style=MaterialTheme.typography.labelMedium)};Icon(Icons.Default.ChevronRight,null)};if(query.isNotBlank())inventory.filter{it.furnitureId==m.id&&it.name.contains(query,true)}.forEach{Text("${it.name} · ${it.compartment}")}}}}
        else {
            val content=inventory.filter{it.furnitureId==f.id}
            val zones=LayoutGrid.zones(f)
            val visible=content.filter{matchesShopping(it.name,contentQuery)}
            item {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        IconButton(onClick={if(directId!=null)back()else{selected=null}}){
                            Icon(Icons.Default.ArrowBack,"Volver a muebles")
                        }
                        FurnitureGlyph(f.type,Modifier.size(42.dp))
                        Column(Modifier.weight(1f)){
                            Text(f.name,style=MaterialTheme.typography.titleLarge)
                            Text(f.room.ifBlank{f.type.label},style=MaterialTheme.typography.bodySmall)
                        }
                        Box {
                            IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Opciones del mueble")}
                            DropdownMenu(menu,{menu=false}){
                                DropdownMenuItem(text={Text("Buscar contenido")},onClick={menu=false;contentQuery=""})
                                if(content.any(::usablePantryItem))DropdownMenuItem(text={Text("Ideas con lo que tengo")},onClick={menu=false;inspect=true})
                                DropdownMenuItem(text={Text("Editar mueble")},onClick={menu=false;editing=f})
                                DropdownMenuItem(text={Text("Organizar compartimentos")},onClick={menu=false;zoneEdit=true})
                                DropdownMenuItem(text={Text("Reconocer una foto")},onClick={menu=false;scan=true})
                                if(f.photo.isNotBlank())DropdownMenuItem(text={Text("Ver foto")},onClick={menu=false;showPhoto=true})
                                if(onShare!=null)DropdownMenuItem(text={Text("Compartir")},onClick={menu=false;onShare(f)})
                                DropdownMenuItem(text={Text("Eliminar mueble",color=MaterialTheme.colorScheme.error)},onClick={menu=false;removeFurniture=f})
                            }
                        }
                    }
                    ActionFlow {
                        FilledTonalButton(onClick={itemEdit=Item(furnitureId=f.id,name="",compartment=zones.firstOrNull()?.name?:UnassignedCompartment)}){
                            Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Añadir contenido")
                        }
                    }
                    contentQuery?.let{ShoppingSearch(it,{value->contentQuery=value},{contentQuery=null})}
                }
            }
            val sections=furnitureSections(zones,visible,includeEmpty=contentQuery.isNullOrBlank())
            if(sections.isEmpty())item{
                Text(if(contentQuery.isNullOrBlank())"Añade el primer producto u objeto." else "Sin coincidencias",
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            sections.forEach { section ->
                item(key="compartment:"+section.name){FurnitureSectionHeader(section)}
                items(section.items,key={it.id}){item->
                    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface,
                        modifier=Modifier.fillMaxWidth()){
                        Column(Modifier.padding(horizontal=10.dp,vertical=4.dp)){
                            Row(Modifier.fillMaxWidth().clickable{itemEdit=item},verticalAlignment=Alignment.CenterVertically){
                                if(item.photo.isNotBlank())LocalPhoto(item.photo,Modifier.size(40.dp))
                                else ProductGlyph(item.name,Modifier.size(36.dp),item.icon)
                                Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                                    Text(item.name,style=MaterialTheme.typography.titleSmall)
                                    Text((amountText(item.amount)+" "+item.unit).trim(),style=MaterialTheme.typography.bodySmall)
                                    if(item.expiry.isNotBlank())Text(item.expiry,style=MaterialTheme.typography.labelSmall,
                                        color=if(item.expiry<=soon)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if(item.amount>0)IconButton(onClick={consume=item}){Icon(Icons.Default.RemoveCircleOutline,"Consumir "+item.name)}
                                var options by remember(item.id){mutableStateOf(false)}
                                Box{
                                    IconButton(onClick={options=true}){Icon(Icons.Default.MoreVert,"Opciones de "+item.name)}
                                    DropdownMenu(options,{options=false}){
                                        DropdownMenuItem(text={Text("Editar")},onClick={options=false;itemEdit=item})
                                        if(moveTargets.any{it.id!=item.furnitureId})DropdownMenuItem(text={Text("Mover a otro mueble")},onClick={options=false;moving=item})
                                        DropdownMenuItem(text={Text("Eliminar",color=MaterialTheme.colorScheme.error)},onClick={options=false;removeItem=item})
                                    }
                                }
                            }

                        }
                    }
                }
            }
        }
    }}
    moving?.let{item->MoveInventoryDialog(item,moveTargets.filter{it.id!=item.furnitureId},{moving=null}){target,zone,done->model.moveItem(item,target,zone,done)}}
    if(showPhoto&&f!=null)AlertDialog(onDismissRequest={showPhoto=false},title={Text(f.name)},
        text={LocalPhoto(f.photo,Modifier.fillMaxWidth().heightIn(max=360.dp))},
        confirmButton={TextButton(onClick={showPhoto=false}){Text("Cerrar")}})
    if(inspect&&f!=null)InventoryIdeas(uid,f.name,inventory.filter{it.furnitureId==f.id}){inspect=false}
    consume?.let{i->ConsumeDialog(i,{consume=null},{n,done->model.consumeItem(i,n,done)})}
    restock?.let{(i,recipe)->InventoryRestockDialog(uid,i,lists,{restock=null},{products,listId,done->model.restockItem(i,products,listId,finished=done)},initialRecipeMode=recipe)}
    editing?.let{m->FurnitureEditor(m,{editing=null},{model.save(it);editing=null})};itemEdit?.let{i->furniture.find{it.id==i.furnitureId}?.let{m->ItemEditor(i,m,{itemEdit=null},{model.save(it);itemEdit=null})}};if(zoneEdit&&f!=null)LayoutEditor(f,inventory.filter{it.furnitureId==f.id},{zoneEdit=false},{layout,done->model.saveFurnitureLayout(f.id,layout,done)});if(scan&&f!=null)PhotoScan(f,f.compartments.firstOrNull()?:UnassignedCompartment,{scan=false},{items->items.forEach{model.save(it)};scan=false});removeFurniture?.let{m->Confirm("Eliminar ${m.name}","Se eliminará también su contenido.",{removeFurniture=null},{model.delete(m.id,Kind.FURNITURE){if(directId!=null)back()else selected=null}})};removeItem?.let{i->Confirm("Eliminar ${i.name}","Se quitará del inventario.",{removeItem=null},{model.delete(i.id,Kind.ITEM)})}
}
@Composable internal fun ZoneBoard(zones:List<Zone>,items:List<Item>,selected:String?,tap:(Zone)->Unit){
    val rows=(zones.maxOfOrNull{it.y+it.h}?:2).coerceIn(2,LayoutGrid.HEIGHT)
    val cellHeight=32.dp
    val palette=MaterialTheme.colorScheme
    val products=items.filter{it.amount>0}.groupBy{it.compartment}
    BoxWithConstraints(Modifier.fillMaxWidth().height(cellHeight*rows).clip(RoundedCornerShape(16.dp))
        .background(palette.surfaceContainerLow)){
        val cellWidth=maxWidth/LayoutGrid.WIDTH
        zones.forEach{zone->key(zone.name){
            val inside=products[zone.name].orEmpty()
            Surface(
                onClick={tap(zone)},
                modifier=Modifier.offset(cellWidth*zone.x,cellHeight*zone.y).size(cellWidth*zone.w,cellHeight*zone.h).padding(2.dp),
                shape=RoundedCornerShape(10.dp),
                color=when{
                    selected==zone.name->palette.primaryContainer
                    zone.type==ZoneType.DRAWER->palette.tertiaryContainer
                    else->palette.surfaceContainerHighest
                },
                border=BorderStroke(1.dp,if(selected==zone.name)palette.primary else palette.outlineVariant)
            ){
                Column(Modifier.padding(horizontal=6.dp,vertical=2.dp),verticalArrangement=Arrangement.Center){
                    Text(zone.name,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(inside.isNotEmpty()&&zone.h>1)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)){
                        inside.take(minOf(zone.w,3)).forEach{ProductGlyph(it.name,Modifier.size(18.dp),it.icon)}
                        Text(inside.size.toString(),style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }}
        if(zones.isEmpty())Text("Sin compartimentos",Modifier.align(Alignment.Center),style=MaterialTheme.typography.bodySmall)
    }
}

private fun initialZones(type:FurnitureType):List<Zone> = when(type){
    FurnitureType.FRIDGE -> listOf(
        Zone("Balda 1",y=0,h=2),Zone("Balda 2",y=2,h=2),Zone("Cajón",ZoneType.DRAWER,y=4,h=2),
        Zone("Balda de puerta",door=true,y=0,h=2),Zone("Botellero",ZoneType.RACK,door=true,y=3,h=2)
    )
    FurnitureType.FREEZER -> (0..2).map{Zone("Cajón "+(it+1),ZoneType.DRAWER,y=2*it,h=2)}
    FurnitureType.PANTRY,FurnitureType.SHELF,FurnitureType.CABINET ->
        (0..2).map{Zone("Balda "+(it+1),y=2*it,h=2)}
    FurnitureType.DRAWER -> listOf(Zone("Cajón",ZoneType.DRAWER,h=2))
    else -> listOf(Zone("General",h=2))
}

@Composable fun FurnitureEditor(initial:Furniture,close:()->Unit,save:(Furniture)->Unit,allowPhoto:Boolean=true){var f by remember(initial){mutableStateOf(initial)};val ctx=LocalContext.current;val photo=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null){runCatching{ctx.contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)};f=f.copy(photo=u.toString())}};AlertDialog(onDismissRequest=close,title={Text("Mueble o electrodoméstico")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Field(f.name,{f=f.copy(name=it)},"Nombre");Choice("Tipo",FurnitureType.entries,f.type,{it.label}){f=f.copy(type=it)};if(allowPhoto&&f.photo.isNotBlank())LocalPhoto(f.photo,Modifier.fillMaxWidth().height(130.dp))else FurnitureGlyph(f.type,Modifier.size(72.dp).align(Alignment.CenterHorizontally));if(allowPhoto)TextButton(onClick={photo.launch(arrayOf("image/*"))}){Text("Seleccionar fotografía real")};Field(f.room,{f=f.copy(room=it)},"Estancia");Field(f.notes,{f=f.copy(notes=it)},"Notas",false)}},confirmButton={TextButton(enabled=f.name.isNotBlank(),onClick={val z=if(initial.name.isBlank()&&f.zones.isEmpty()&&f.compartments==listOf("General"))initialZones(f.type)else LayoutGrid.zones(f);save(f.copy(name=f.name.trim(),zones=z,compartments=z.map{it.name}))}){Text("Guardar")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})}
@Composable fun ItemEditor(initial:Item,f:Furniture,close:()->Unit,save:(Item)->Unit){var i by remember(initial){mutableStateOf(initial)};var qty by remember{mutableStateOf(amountText(i.amount))};val ctx=LocalContext.current;val photo=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null){runCatching{ctx.contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)};i=i.copy(photo=u.toString())}};val n=qty.replace(',','.').toDoubleOrNull();val valid=i.name.isNotBlank()&&n!=null&&n.isFinite()&&n>=0&&(i.expiry.isBlank()||runCatching{LocalDate.parse(i.expiry)}.isSuccess);AlertDialog(onDismissRequest=close,title={Text("Contenido de ${f.name}")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Field(i.name,{i=i.copy(name=it,icon=guessIcon(it))},"Producto u objeto");Choice("Tipo de contenido",InventoryKind.entries,i.contentKind,{if(it==InventoryKind.AUTO)"Automático · "+InventoryConsumption.kind(i.copy(contentKind=InventoryKind.AUTO)).label else it.label}){i=i.copy(contentKind=it)};Choice("Compartimento",(f.compartments+UnassignedCompartment+i.compartment).distinct(),i.compartment,{it}){i=i.copy(compartment=it)};Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(3.dp)){ProductType.entries.forEach{category->FilterChip(productIconType(i.name,i.icon)==category,{i=i.copy(icon=category.symbol)},label={ProductGlyph("",Modifier.size(30.dp).semantics{contentDescription=productLabel(category)},category.symbol)})}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Field(qty,{qty=it},"Cantidad")};Column(Modifier.weight(1f)){UnitSelector(i.unit,{i=i.copy(unit=it)},allowEmpty=false)}};DateField(i.expiry,{i=i.copy(expiry=it)},"Caducidad");Field(i.notes,{i=i.copy(notes=it)},"Notas",false);if(i.photo.isNotBlank())LocalPhoto(i.photo,Modifier.fillMaxWidth().height(100.dp));TextButton(onClick={photo.launch(arrayOf("image/*"))}){Text("Añadir fotografía")}}},confirmButton={TextButton(enabled=valid,onClick={save(i.copy(name=i.name.trim(),amount=n!!,quantity=amountText(n),unit=SpanishUnits.canonical(i.unit)?:i.unit))}){Text("Guardar")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})}
@Composable fun PhotoScan(f:Furniture,comp:String,close:()->Unit,save:(List<Item>)->Unit){val ctx=LocalContext.current;val scope=rememberCoroutineScope();var text by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var photo by remember{mutableStateOf("")};fun detect(input:InputImage){scope.launch{busy=true;error="";val labeler=ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);val reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);try{val labels=labeler.process(input).await().filter{it.confidence>=.6f}.map{it.text};val ocr=reader.process(input).await().text.lines().filter{it.isNotBlank()}.take(12);text=(labels+ocr).distinct().joinToString("\n");if(text.isBlank())error="No se identificó contenido. Puedes escribirlo manualmente."}catch(e:CancellationException){throw e}catch(e:Exception){error="No se pudo analizar la imagen."}finally{labeler.close();reader.close();busy=false}}};val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null){photo=u.toString();runCatching{InputImage.fromFilePath(ctx,u)}.onSuccess{detect(it)}.onFailure{error="Imagen no disponible"}}};val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()){b->if(b!=null)detect(InputImage.fromBitmap(b,0))};AlertDialog(onDismissRequest=close,title={Text("Reconocer contenido")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Análisis local de etiquetas visuales y texto. No identifica con certeza todos los alimentos ni sus cantidades. Edita las propuestas, una por línea.");Row{TextButton(onClick={pick.launch(arrayOf("image/*"))}){Text("Galería")};TextButton(onClick={runCatching{camera.launch(null)}.onFailure{error="Cámara no disponible"}}){Text("Cámara")}};if(photo.isNotBlank())LocalPhoto(photo,Modifier.fillMaxWidth().height(140.dp));if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());Field(text,{text=it},"Revisar productos",false);if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)}},confirmButton={TextButton(enabled=!busy&&text.isNotBlank(),onClick={save(text.lines().map{it.trim()}.filter{it.isNotBlank()}.distinct().map{Item(furnitureId=f.id,name=it,compartment=comp,icon=guessIcon(it))})}){Text("Confirmar contenido")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})}
