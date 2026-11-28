package es.planidroid.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
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

@Composable internal fun FurnitureSectionHeader(section:FurnitureSection){
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
    Surface(Modifier.fillMaxWidth().padding(top=8.dp).semantics(mergeDescendants=true){heading()},
        shape=RoundedCornerShape(16.dp),color=soft(tint,.17f)){
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Surface(shape=RoundedCornerShape(10.dp),color=soft(tint,.28f)){
                Icon(glyph,null,Modifier.padding(8.dp).size(21.dp))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                Text(section.name.ifBlank{UnassignedCompartment},style=MaterialTheme.typography.titleMedium)
                val location=section.zone?.let{if(it.door)"Puerta" else "Interior"}
                val count=when(section.items.size){0->"Vacío";1->"1 producto";else->section.items.size.toString()+" productos"}
                Text(listOfNotNull(location,count).joinToString(" · "),
                    color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
