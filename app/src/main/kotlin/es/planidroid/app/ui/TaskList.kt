package es.planidroid.app.ui
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*

@Composable fun TaskList(tasks:List<Task>,edit:(Task)->Unit,add:()->Unit,delete:(Task)->Unit){
    var query by rememberSaveable{mutableStateOf("")};var filter by rememberSaveable{mutableStateOf("Pendientes")}
    var filtersOpen by remember{mutableStateOf(false)}
    var remove by remember{mutableStateOf<Task?>(null)}
    val shown=tasks.filter{t->(query.isBlank()||listOf(t.title,t.description,t.tags.joinToString()).any{it.contains(query,true)})&&when(filter){"Pendientes"->t.status !in listOf(Status.DONE,Status.ARCHIVED);"Completadas"->t.status==Status.DONE;"Archivadas"->t.status==Status.ARCHIVED;"Atrasadas"->t.status !in listOf(Status.DONE,Status.ARCHIVED)&&t.due.isNotBlank()&&t.due<java.time.LocalDate.now().toString();"Todas"->true;else->t.category.label==filter&&t.status!=Status.ARCHIVED}}.sortedWith(compareBy<Task>{it.due.ifBlank{"9999"}}.thenByDescending{it.priority.ordinal})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{
            Row(verticalAlignment=Alignment.CenterVertically){Text("Mis tareas",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);FilledTonalIconButton(onClick=add){Icon(Icons.Default.Add,"Añadir tarea")}}
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)},placeholder={Text("Buscar")},shape=RoundedCornerShape(18.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Pendientes","Completadas","Todas").forEach{v->FilterChip(filter==v,{filter=v},label={Text(v)})};Box{IconButton(onClick={filtersOpen=true}){Icon(Icons.Default.FilterList,"Más filtros")};DropdownMenu(filtersOpen,{filtersOpen=false}){(listOf("Archivadas","Atrasadas")+Category.entries.map{it.label}).forEach{v->DropdownMenuItem(text={Text(v)},onClick={filter=v;filtersOpen=false})}}}}
            if(filter !in listOf("Pendientes","Completadas","Todas"))Text(filter,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            if(shown.isEmpty())Text("No hay tareas en esta vista.",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=12.dp))
        }
        items(shown,key={it.id}){t->
            var menu by remember(t.id){mutableStateOf(false)}
            Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface){
                Row(Modifier.fillMaxWidth().clickable{edit(t)}.padding(start=12.dp,top=10.dp,bottom=10.dp,end=2.dp),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.width(4.dp).height(38.dp).background(taskColor(t),RoundedCornerShape(4.dp)))
                    Column(Modifier.weight(1f).padding(start=12.dp)){Text(t.title,style=MaterialTheme.typography.titleMedium);Text(listOfNotNull(t.due.takeIf{it.isNotBlank()},t.status.label,t.minutes.takeIf{it>0}?.let{"$it min"}).joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Box{IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Opciones de "+t.title)};DropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text("Editar")},onClick={menu=false;edit(t)},leadingIcon={Icon(Icons.Default.Edit,null)});DropdownMenuItem(text={Text("Eliminar tarea",color=MaterialTheme.colorScheme.error)},onClick={menu=false;remove=t},leadingIcon={Icon(Icons.Default.DeleteOutline,null,tint=MaterialTheme.colorScheme.error)})}}
                }
            }
        }
    }
    remove?.let{t->Confirm("Eliminar "+t.title,"Se eliminarán también sus sesiones, descansos y recordatorios.",{remove=null},{delete(t)})}
}
