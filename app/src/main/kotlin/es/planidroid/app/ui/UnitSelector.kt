package es.planidroid.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.SpanishUnits

@Composable internal fun UnitSelector(value:String,onChange:(String)->Unit,enabled:Boolean=true,allowEmpty:Boolean=true){
    var open by remember{mutableStateOf(false)}
    val selected=SpanishUnits.canonical(value)?:value
    OutlinedButton(onClick={open=true},enabled=enabled,modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=10.dp,vertical=12.dp)){
        Column(Modifier.weight(1f)){Text("Unidad",style=MaterialTheme.typography.labelSmall);Text(if(selected.isBlank())"Sin unidad" else selected,maxLines=1,style=MaterialTheme.typography.bodyLarge)}
        Icon(Icons.Default.ExpandMore,"Elegir unidad",Modifier.size(18.dp))
    }
    if(open)AlertDialog(onDismissRequest={open=false},title={Text("Unidad de medida")},text={
        LazyColumn(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
            if(selected !in SpanishUnits.options)item{Text("Unidad anterior: "+selected+". Elige una unidad nueva solo si corresponde a la cantidad.",style=MaterialTheme.typography.bodySmall)}
            items(SpanishUnits.options.entries.filter{allowEmpty||it.key.isNotBlank()}.toList(),key={it.key}){entry->
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
                    RadioButton(selected=selected==entry.key,onClick={onChange(entry.key);open=false})
                    TextButton(onClick={onChange(entry.key);open=false},modifier=Modifier.weight(1f)){Text(entry.value+if(entry.key.isNotBlank()&&entry.key!=entry.value)" ("+entry.key+")"else "",modifier=Modifier.fillMaxWidth())}
                }
            }
        }
    },confirmButton={TextButton(onClick={open=false}){Text("Cerrar")}})
}
