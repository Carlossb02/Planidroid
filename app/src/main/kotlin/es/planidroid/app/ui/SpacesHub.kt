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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*
@Composable fun SpacesHub(groups:List<Group>,tasks:List<Task>,save:(Group)->Unit,delete:(Group)->Unit,edit:(Task)->Unit,home:()->Unit,shopping:()->Unit,shared:()->Unit,assistant:()->Unit,tools:()->Unit){
    var group by remember{mutableStateOf<Group?>(null)}
    var remove by remember{mutableStateOf<Group?>(null)}
    var expanded by remember{mutableStateOf<String?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){SpaceTile("Hogar","Inventario",Icons.Default.Home,Mint,Modifier.weight(1f),home);SpaceTile("Compra","Mis listas",Icons.Default.ShoppingCart,Peach,Modifier.weight(1f),shopping)}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){SpaceTile("Compartido","Invitaciones",Icons.Default.People,Blue,Modifier.weight(1f),shared);SpaceTile("Gemini","Asistente",Icons.Default.AutoAwesome,Lilac,Modifier.weight(1f),assistant)}}
        item{Row(verticalAlignment=Alignment.CenterVertically){Text("Proyectos",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);FilledTonalIconButton(onClick={group=Group(name="")}){Icon(Icons.Default.Add,"Crear proyecto")}}}
        if(groups.isEmpty())item{Text("Agrupa tareas relacionadas en un proyecto.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        items(groups.sortedBy{it.name.lowercase()},key={it.id}){g->
            val list=tasks.filter{it.groupId==g.id};val done=list.count{it.status==Status.DONE};val color=taskColor(Task(title="",groupId=g.id))
            GlassCard(tint=color){
                Row(Modifier.fillMaxWidth().clickable{expanded=if(expanded==g.id)null else g.id},verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(g.name,style=MaterialTheme.typography.titleMedium);Text("$done de "+list.size+" tareas completadas",style=MaterialTheme.typography.bodySmall)}
                    Icon(if(expanded==g.id)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)
                }
                if(list.isNotEmpty())LinearProgressIndicator(progress={done.toFloat()/list.size},modifier=Modifier.fillMaxWidth(),color=color)
                if(expanded==g.id){
                    if(g.description.isNotBlank())Text(g.description)
                    list.forEach{t->TextButton(onClick={edit(t)}){Text(t.title)}}
                    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){TextButton(onClick={edit(Task(title="",groupId=g.id))}){Text("Añadir tarea")};TextButton(onClick={group=g}){Text("Editar")};IconButton(onClick={remove=g}){Icon(Icons.Default.DeleteOutline,"Eliminar proyecto",tint=MaterialTheme.colorScheme.error)}}
                }
            }
        }
        item{HorizontalDivider();TextButton(onClick=tools){Icon(Icons.Default.Settings,null);Spacer(Modifier.width(8.dp));Text("Herramientas y preferencias")}}
    }
    group?.let{g->var name by remember(g){mutableStateOf(g.name)};var desc by remember(g){mutableStateOf(g.description)};AlertDialog(onDismissRequest={group=null},title={Text(if(g.name.isBlank())"Nuevo proyecto" else "Editar proyecto")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Field(name,{name=it.take(180)},"Nombre");Field(desc,{desc=it.take(4000)},"Descripción",false)}},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={save(g.copy(name=name.trim(),description=desc));group=null}){Text("Guardar")}},dismissButton={TextButton(onClick={group=null}){Text("Cancelar")}})}
    remove?.let{g->Confirm("Eliminar proyecto","Sus tareas se conservarán sin proyecto.",{remove=null},{delete(g)})}
}
@Composable private fun SpaceTile(title:String,subtitle:String,icon:ImageVector,color:Color,modifier:Modifier,onClick:()->Unit){
    Surface(modifier.clickable(onClick=onClick),shape=RoundedCornerShape(20.dp),color=soft(color,.16f)){
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Icon(icon,null,tint=MaterialTheme.colorScheme.onSurface,modifier=Modifier.size(26.dp));Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
