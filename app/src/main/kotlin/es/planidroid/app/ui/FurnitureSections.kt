package es.planidroid.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*

internal data class FurnitureSection(val name:String,val zone:Zone?,val items:List<Item>)

internal fun furnitureSections(zones:List<Zone>,items:List<Item>,includeEmpty:Boolean=true):List<FurnitureSection>{
    val byName=items.groupBy{it.compartment}
    val byZone=zones.associateBy{it.name}
    val names=zones.sortedWith(compareBy({it.door},{it.y},{it.x})).map{it.name}+byName.keys.sorted()
    return names.distinct().mapNotNull{name->
        val products=byName[name].orEmpty()
        if(!includeEmpty&&products.isEmpty())null
        else FurnitureSection(name,byZone[name],products.sortedBy{it.name.lowercase()})
    }
}

@Composable internal fun FurnitureCompartment(
    section:FurnitureSection,add:(()->Unit)?=null,itemContent:@Composable (Item)->Unit
){
    var expanded by rememberSaveable(section.name){mutableStateOf(true)}
    val label=section.name.ifBlank{UnassignedCompartment}
    val type=section.zone?.type
    val tint=when(type){
        ZoneType.SHELF->Blue
        ZoneType.DRAWER->Lilac
        ZoneType.BASKET->Peach
        ZoneType.RACK->Mint
        ZoneType.OPEN->Blue
        null->MaterialTheme.colorScheme.outline
    }
    val glyph=when(type){
        ZoneType.SHELF->Icons.Default.ViewAgenda
        ZoneType.DRAWER->Icons.Default.Inbox
        ZoneType.BASKET->Icons.Default.ShoppingBasket
        ZoneType.RACK->Icons.Default.LocalDrink
        ZoneType.OPEN->Icons.Default.CropSquare
        null->Icons.Default.Inventory2
    }
    Surface(Modifier.fillMaxWidth().testTag("compartment:"+section.name),
        shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,soft(tint,.50f))){
        Column{
            Row(Modifier.fillMaxWidth().background(soft(tint,.20f)).padding(end=4.dp),
                verticalAlignment=Alignment.CenterVertically){
                Row(Modifier.weight(1f)
                    .clickable(role=Role.Button,onClickLabel=if(expanded)"Plegar compartimento" else "Desplegar compartimento"){
                        expanded=!expanded
                    }.semantics{heading();stateDescription=if(expanded)"Desplegado" else "Plegado"}
                    .padding(horizontal=12.dp,vertical=10.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
                    Icon(glyph,null,Modifier.size(22.dp))
                    Column(Modifier.weight(1f)){
                        Text(label,style=MaterialTheme.typography.titleMedium)
                        val location=section.zone?.let{if(it.door)"Puerta" else "Interior"}
                        val count=if(section.items.size==1)"1 producto" else section.items.size.toString()+" productos"
                        Text(listOfNotNull(location,count).joinToString(" · "),style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null,Modifier.size(20.dp))
                }
                if(add!=null)IconButton(onClick=add){Icon(Icons.Default.Add,"Añadir producto a "+label)}
            }
            if(expanded)Column(Modifier.fillMaxWidth().padding(start=12.dp,end=6.dp,top=4.dp,bottom=4.dp)){
                if(section.items.isEmpty())Text("Compartimento vacío",Modifier.padding(12.dp),
                    color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
                section.items.forEachIndexed{index,item->key(item.id){
                    if(index>0)HorizontalDivider(Modifier.padding(start=40.dp,end=8.dp))
                    itemContent(item)
                }}
            }
        }
    }
}
