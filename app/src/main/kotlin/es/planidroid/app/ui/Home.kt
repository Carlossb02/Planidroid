package es.planidroid.app.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import es.planidroid.app.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun Home(tasks:List<Task>,sessions:List<Session>,edit:(Task)->Unit,capture:()->Unit,inventory:()->Unit,shopping:()->Unit,calendar:()->Unit,taskList:()->Unit,spaces:()->Unit,count:Int,buyCount:Int,saveSession:(Session)->Unit,focus:()->Unit,assistant:()->Unit={},openEvent:()->Unit=calendar){
    val today=LocalDate.now();val now=System.currentTimeMillis()
    val todaySessions=sessions.filter{sessionDate(it.start)<=today&&sessionDate(it.end-1)>=today}.sortedBy{it.start}
    val day=TimelineLayout.bounds(today,ZoneId.systemDefault())
    val work=todaySessions.filterNot{it.isBreak||it.calendarTitle.isNotBlank()};val done=work.count{it.done}
    val eventAnchors=sessions.filter{it.calendarTitle.isNotBlank()}.map{it.taskId}.toSet()
    val active=tasks.filter{it.id !in eventAnchors&&it.status !in listOf(Status.DONE,Status.ARCHIVED)}
    val next=todaySessions.filter{!it.done&&it.end>now}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{
            Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",java.util.Locale("es","ES"))).replaceFirstChar{it.uppercase()},style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().clickable(onClick=calendar)){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(work.isEmpty()&&next.isEmpty())"Agenda despejada" else if(work.isEmpty())"Eventos de hoy" else "$done de "+work.size+" sesiones",style=MaterialTheme.typography.titleLarge);if(work.any{!it.done})Text(work.filterNot{it.done}.sumOf{(minOf(it.end,day.end)-maxOf(it.start,day.start))/60000}.toString()+" min de actividad pendientes",style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.ChevronRight,"Abrir calendario")}
                    if(work.isNotEmpty())LinearProgressIndicator(progress={done.toFloat()/work.size},modifier=Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)),color=MaterialTheme.colorScheme.primary,trackColor=MaterialTheme.colorScheme.surface)
                }
            }
        }
        if(next.isNotEmpty())item{Text("A continuación",style=MaterialTheme.typography.titleLarge)}
        items(next.take(3),key={it.id}){entry->
            val s=entry;val t=tasks.find{it.id==s.taskId}
            val tint=if(s.isBreak)MaterialTheme.colorScheme.outline else if(s.calendarTitle.isNotBlank())Lilac else taskColor(t)
            Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().clickable{if(s.calendarTitle.isNotBlank())openEvent() else if(t!=null&&!s.isBreak)edit(t)else calendar()}){
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(time(entry.start),style=MaterialTheme.typography.labelLarge,modifier=Modifier.width(54.dp))
                    Box(Modifier.width(4.dp).height(34.dp).background(tint,RoundedCornerShape(3.dp)))
                    Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(if(s.isBreak)"Descanso" else s.calendarTitle.ifBlank{t?.title?:"Sesión"},style=MaterialTheme.typography.titleMedium);Text("Hasta "+time(entry.end),style=MaterialTheme.typography.bodySmall)}
                    if(!s.isBreak&&s.calendarTitle.isBlank())Checkbox(s.done,{saveSession(s.copy(done=it))})
                }
            }
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){HomeShortcut("Compra",if(buyCount>0)"$buyCount pendientes" else "Al día",Icons.Default.ShoppingCart,Peach,Modifier.weight(1f),shopping);HomeShortcut("Hogar","Inventario",Icons.Default.Home,Mint,Modifier.weight(1f),inventory)}
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){HomeShortcut("Gemini","Asistente",Icons.Default.AutoAwesome,Lilac,Modifier.weight(1f),assistant);HomeShortcut("Concentración","Temporizador",Icons.Default.Timer,Blue,Modifier.weight(1f),focus)}
        }
        if(active.any{it.due.isNotBlank()})item{Row(verticalAlignment=Alignment.CenterVertically){Text("Próximos vencimientos",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick=taskList){Text("Todas")}}}
        items(active.filter{it.due.isNotBlank()}.sortedBy{it.due}.take(3),key={"due-"+it.id}){t->Row(Modifier.fillMaxWidth().clickable{edit(t)}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Text(t.title,Modifier.weight(1f),maxLines=2);Text(t.due,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
    }
}
@Composable private fun HomeShortcut(title:String,detail:String,icon:ImageVector,tint:Color,modifier:Modifier,onClick:()->Unit){
    Surface(modifier.clickable(onClick=onClick),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface){
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Icon(icon,null,tint=tint,modifier=Modifier.size(22.dp))
            Column(Modifier.weight(1f)){
                Text(title,style=MaterialTheme.typography.titleSmall)
                Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
