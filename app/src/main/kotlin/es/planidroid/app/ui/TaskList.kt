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

@Composable fun TaskList(tasks:List<Task>,edit:(Task)->Unit,add:()->Unit,delete:(Task)->Unit,changeStatus:(Task,Status)->Unit){
    var query by rememberSaveable{mutableStateOf("")};var filter by rememberSaveable{mutableStateOf("Pendientes")}
    var filtersOpen by remember{mutableStateOf(false)}
    var showSearch by rememberSaveable{mutableStateOf(false)}
    var remove by remember{mutableStateOf<Task?>(null)}
    val shown=tasks.filter{t->(query.isBlank()||listOf(t.title,t.description,t.tags.joinToString()).any{it.contains(query,true)})&&when(filter){"Pendientes"->t.status !in listOf(Status.DONE,Status.ARCHIVED);"Completadas"->t.status==Status.DONE;"Archivadas"->t.status==Status.ARCHIVED;"Atrasadas"->t.status !in listOf(Status.DONE,Status.ARCHIVED)&&t.due.isNotBlank()&&t.due<java.time.LocalDate.now().toString();"Todas"->true;else->t.category.label==filter&&t.status!=Status.ARCHIVED}}.sortedWith(compareBy<Task>{it.due.ifBlank{"9999"}}.thenByDescending{it.priority.ordinal})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(filter,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                Box {
                    IconButton(onClick={filtersOpen=true}){Icon(Icons.Default.FilterList,"Filtrar tareas")}
                    DropdownMenu(filtersOpen,{filtersOpen=false}){
                        (listOf("Pendientes","Completadas","Todas","Archivadas","Atrasadas")+Category.entries.map{it.label}).forEach{item->
                            DropdownMenuItem(text={Text(item)},onClick={filter=item;filtersOpen=false})
                        }
                    }
                }
                IconButton(onClick={showSearch=!showSearch;if(!showSearch)query=""}){
                    Icon(if(showSearch)Icons.Default.Close else Icons.Default.Search,if(showSearch)"Cerrar búsqueda" else "Buscar tareas")
                }
                FilledTonalIconButton(onClick=add){Icon(Icons.Default.Add,"Añadir tarea")}
            }
            if(showSearch)OutlinedTextField(query,{query=it.take(180)},Modifier.fillMaxWidth(),singleLine=true,label={Text("Buscar tareas")})
            if(shown.isEmpty())Text("No hay tareas en esta vista.",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=12.dp))
        }
        items(shown,key={it.id}){t->
            var menu by remember(t.id){mutableStateOf(false)}
            Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface){
                Row(Modifier.fillMaxWidth().clickable{edit(t)}.padding(start=12.dp,top=10.dp,bottom=10.dp,end=2.dp),verticalAlignment=Alignment.CenterVertically){
                    if(t.status!=Status.ARCHIVED)IconButton(onClick={
                        changeStatus(t,if(t.status==Status.DONE)Status.PENDING else Status.DONE)
                    }){
                        Icon(if(t.status==Status.DONE)Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            if(t.status==Status.DONE)"Reabrir tarea" else "Completar tarea",tint=taskColor(t))
                    }
                    Column(Modifier.weight(1f).padding(start=12.dp)){Text(t.title,style=MaterialTheme.typography.titleMedium);Text(listOfNotNull(t.due.takeIf{it.isNotBlank()},t.status.label,t.minutes.takeIf{it>0}?.let{"$it min"}).joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Box{IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Opciones de "+t.title)};DropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text("Editar")},onClick={menu=false;edit(t)},leadingIcon={Icon(Icons.Default.Edit,null)});DropdownMenuItem(
                        text={Text(if(t.status==Status.ARCHIVED)"Restaurar tarea" else "Archivar tarea")},
                        onClick={menu=false;changeStatus(t,if(t.status==Status.ARCHIVED)Status.PENDING else Status.ARCHIVED)},
                        leadingIcon={Icon(if(t.status==Status.ARCHIVED)Icons.Default.Unarchive else Icons.Default.Archive,null)}
                    );DropdownMenuItem(text={Text("Eliminar tarea",color=MaterialTheme.colorScheme.error)},onClick={menu=false;remove=t},leadingIcon={Icon(Icons.Default.DeleteOutline,null,tint=MaterialTheme.colorScheme.error)})}}
                }
            }
        }
    }
    remove?.let{t->Confirm("Eliminar "+t.title,"Se eliminarán también sus sesiones, descansos y recordatorios.",{remove=null},{delete(t)})}
}
