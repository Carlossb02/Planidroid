package es.planidroid.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import es.planidroid.app.domain.*

private data class CompartmentForm(val existing:Zone?)

@Composable internal fun LayoutEditor(
    furniture:Furniture,items:List<Item>,close:()->Unit,save:(FurnitureLayout,(String?)->Unit)->Unit
){
    var layout by remember(furniture.id){mutableStateOf(FurnitureLayout(LayoutGrid.zones(furniture)))}
    var door by remember(furniture.id){mutableStateOf(false)}
    var preview by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<CompartmentForm?>(null)}
    var removing by remember{mutableStateOf<Zone?>(null)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    val displayItems=items.map(layout::item)
    val counts=displayItems.groupingBy{it.compartment}.eachCount()
    val visible=layout.zones.filter{it.door==door}.sortedWith(compareBy({it.y},{it.x}))
    val dirty=layout!=FurnitureLayout(layout.original)
    fun dismiss(){if(!busy)close()}

    Dialog(onDismissRequest=::dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(),contentAlignment=Alignment.TopCenter){
                Column(Modifier.widthIn(max=760.dp).fillMaxSize()){
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                        IconButton(enabled=!busy,onClick=::dismiss){Icon(Icons.Default.Close,"Cerrar distribución")}
                        Text("Organizar mueble",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start=20.dp,end=20.dp,top=12.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                            FurnitureGlyph(furniture.type,Modifier.size(48.dp))
                            Text(furniture.name,style=MaterialTheme.typography.headlineSmall)
                        }
                        ActionFlow {
                            FilterChip(!door,{door=false},label={Text("Interior")})
                            FilterChip(door,{door=true},label={Text("Puerta")})
                            TextButton(onClick={preview=!preview}){Text(if(preview)"Ocultar plano" else "Ver plano")}
                        }
                        if(preview&&visible.isNotEmpty())ZoneBoard(visible,displayItems,null){zone->editing=CompartmentForm(zone)}
                        if(visible.isEmpty())Text(
                            if(door)"La puerta no tiene compartimentos." else "El interior no tiene compartimentos.",
                            color=MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        visible.forEach{zone->key(zone.name){
                            CompartmentRow(zone,counts[zone.name]?:0,!busy,
                                edit={editing=CompartmentForm(zone)},remove={removing=zone})
                        }}
                        FilledTonalButton(enabled=!busy,onClick={editing=CompartmentForm(null)}){
                            Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Añadir compartimento")
                        }
                        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                        Button(enabled=dirty&&!busy,modifier=Modifier.fillMaxWidth(),onClick={
                            busy=true;error=null
                            save(layout){failure->busy=false;error=failure;if(failure==null)close()}
                        }){Text("Guardar distribución")}
                    }
                }
            }
        }
    }

    editing?.let{form->
        CompartmentEditor(form.existing,door,layout.zones,{editing=null}){zone->
            layout=layout.replace(form.existing,zone)
            door=zone.door;editing=null;error=null
        }
    }
    removing?.let{zone->
        val count=counts[zone.name]?:0
        val options=listOf(UnassignedCompartment)+layout.zones.filterNot{it==zone}
            .map{it.name}.filterNot{it==UnassignedCompartment}
        var destination by remember(zone){mutableStateOf(UnassignedCompartment)}
        AlertDialog(onDismissRequest={removing=null},title={Text("Eliminar "+zone.name)},
            text={
                Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text(if(count==0)"El compartimento se eliminará al guardar."
                        else if(count==1)"El producto se conservará en el destino que elijas."
                        else "Los "+count+" productos se conservarán en el destino que elijas.")
                    if(count>0&&options.size>1)Choice("Mover contenido a",options,destination,{it}){destination=it}
                    if(count>0&&options.size==1)Text("Destino: "+UnassignedCompartment)
                }
            },
            confirmButton={TextButton(onClick={layout=layout.remove(zone,destination);removing=null;error=null}){
                Text("Eliminar",color=MaterialTheme.colorScheme.error)
            }},
            dismissButton={TextButton(onClick={removing=null}){Text("Cancelar")}})
    }
}

@Composable private fun CompartmentRow(zone:Zone,count:Int,enabled:Boolean,edit:()->Unit,remove:()->Unit){
    val tint=when(zone.type){
        ZoneType.DRAWER->Lilac
        ZoneType.BASKET->Peach
        ZoneType.RACK->Mint
        else->Blue
    }
    val icon=when(zone.type){
        ZoneType.DRAWER->Icons.Default.Inbox
        ZoneType.BASKET->Icons.Default.ShoppingBasket
        ZoneType.RACK->Icons.Default.LocalDrink
        ZoneType.OPEN->Icons.Default.CropSquare
        ZoneType.SHELF->Icons.Default.ViewAgenda
    }
    Surface(shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(start=12.dp,end=4.dp,top=6.dp,bottom=6.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Surface(shape=RoundedCornerShape(12.dp),color=soft(tint,.17f)){
                Icon(icon,null,Modifier.padding(9.dp).size(22.dp),tint=tint)
            }
            Column(Modifier.weight(1f)){
                Text(zone.name,style=MaterialTheme.typography.titleMedium)
                val contents=when(count){0->"Vacío";1->"1 producto";else->"$count productos"}
                Text(zone.type.label+" · "+contents,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(enabled=enabled,onClick=edit){Icon(Icons.Default.Edit,"Editar "+zone.name)}
            IconButton(enabled=enabled,onClick=remove){
                Icon(Icons.Default.DeleteOutline,"Eliminar "+zone.name,tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun CompartmentEditor(existing:Zone?,initialDoor:Boolean,zones:List<Zone>,close:()->Unit,save:(Zone)->Unit){
    val peers=zones.filterNot{it==existing}
    var type by remember{mutableStateOf(existing?.type?:ZoneType.SHELF)}
    var name by remember{mutableStateOf(existing?.name.orEmpty())}
    var door by remember{mutableStateOf(existing?.door?:initialDoor)}
    var manual by remember{mutableStateOf(false)}
    var row by remember{mutableStateOf(((existing?.y?:0)+1).toString())}
    var column by remember{mutableStateOf(((existing?.x?:0)+1).toString())}
    var width by remember{mutableStateOf((existing?.w?:LayoutGrid.WIDTH).toString())}
    var height by remember{mutableStateOf((existing?.h?:1).toString())}
    val label=name.trim().ifBlank{LayoutGrid.defaultName(type,peers)}
    val validName=peers.none{it.name.equals(label,true)}&&(label==existing?.name||!label.equals(UnassignedCompartment,true))
    val automatic=if(existing!=null&&existing.door==door){
        existing.copy(name=label,type=type)
    } else {
        val preferredWidth=existing?.w?:if(type==ZoneType.BASKET)2 else LayoutGrid.WIDTH
        val preferredHeight=existing?.h?:if(type==ZoneType.DRAWER)2 else 1
        LayoutGrid.place(label,type,door,peers,preferredWidth,preferredHeight)
            ?:if(existing==null)LayoutGrid.place(label,type,door,peers,1,1) else null
    }
    val candidate=if(manual){
        Zone(label,type,door,(column.toIntOrNull()?:0)-1,(row.toIntOrNull()?:0)-1,
            width.toIntOrNull()?:0,height.toIntOrNull()?:0)
    }else automatic
    val fits=candidate!=null&&LayoutGrid.fits(candidate,peers)
    AlertDialog(onDismissRequest=close,title={Text(if(existing==null)"Nuevo compartimento" else "Editar compartimento")},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Choice("Tipo",ZoneType.entries,type,{it.label}){type=it}
                OutlinedTextField(name,{name=it.take(80)},Modifier.fillMaxWidth(),label={Text("Nombre")},
                    placeholder={Text(LayoutGrid.defaultName(type,peers))},singleLine=true,isError=!validName)
                Choice("Ubicación",listOf(false,true),door,{if(it)"Puerta" else "Interior"}){door=it}
                TextButton(onClick={
                    if(!manual&&automatic!=null){
                        row=(automatic.y+1).toString();column=(automatic.x+1).toString()
                        width=automatic.w.toString();height=automatic.h.toString()
                    }
                    manual=!manual
                }){Text(if(manual)"Ocultar posición y tamaño" else "Ajustar posición y tamaño")}
                if(manual){
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        GridField("Fila",row,{row=it},Modifier.weight(1f))
                        GridField("Columna",column,{column=it},Modifier.weight(1f))
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        GridField("Ancho",width,{width=it},Modifier.weight(1f))
                        GridField("Alto",height,{height=it},Modifier.weight(1f))
                    }
                    Text("Plano de "+LayoutGrid.HEIGHT+" filas por "+LayoutGrid.WIDTH+" columnas.",style=MaterialTheme.typography.bodySmall)
                }
                if(!validName||!fits)Text(
                    if(!validName)"Elige un nombre distinto." else "El compartimento no cabe en esa posición.",
                    color=MaterialTheme.colorScheme.error)
            }
        },
        confirmButton={TextButton(enabled=validName&&fits,onClick={candidate?.let(save)}){
            Text(if(existing==null)"Añadir" else "Aplicar")
        }},
        dismissButton={TextButton(onClick=close){Text("Cancelar")}})
}

@Composable private fun GridField(label:String,value:String,change:(String)->Unit,modifier:Modifier){
    OutlinedTextField(value,{change(it.filter(Char::isDigit).take(2))},modifier,
        label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
}
