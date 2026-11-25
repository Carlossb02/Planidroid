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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import es.planidroid.app.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun Home(tasks:List<Task>,sessions:List<Session>,edit:(Task)->Unit,capture:()->Unit,inventory:()->Unit,shopping:()->Unit,calendar:()->Unit,taskList:()->Unit,spaces:()->Unit,count:Int,buyCount:Int,saveSession:(Session)->Unit,focus:()->Unit,external:List<ExternalEvent> = emptyList(),assistant:()->Unit={}){
    val today=LocalDate.now();val now=System.currentTimeMillis()
    val todaySessions=sessions.filter{sessionDate(it.start)<=today&&sessionDate(it.end-1)>=today}.sortedBy{it.start}
    val day=TimelineLayout.bounds(today,ZoneId.systemDefault())
    val work=todaySessions.filterNot{it.isBreak};val done=work.count{it.done}
    val active=tasks.filter{it.status !in listOf(Status.DONE,Status.ARCHIVED)}
    val next=(todaySessions.filter{!it.done&&it.end>now}.map{CalendarEntry(it.start,it.end,it,null)}+external.filter{sessionDate(it.start)<=today&&sessionDate(it.end-1)>=today&&it.end>now}.map{CalendarEntry(it.start,it.end,null,it)}).sortedBy{it.start}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{
            Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",java.util.Locale("es","ES"))).replaceFirstChar{it.uppercase()},style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color=Color(0xFF285A9A),contentColor=Color.White,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth().clickable(onClick=calendar)){
                Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF285A9A),Color(0xFF527FBD)))).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(work.isEmpty())"Agenda despejada" else "$done de "+work.size+" sesiones",style=MaterialTheme.typography.titleLarge);Text(work.filterNot{it.done}.sumOf{(minOf(it.end,day.end)-maxOf(it.start,day.start))/60000}.toString()+" min de actividad pendientes",style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.ChevronRight,"Abrir calendario")}
                    if(work.isNotEmpty())LinearProgressIndicator(progress={done.toFloat()/work.size},modifier=Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)),color=Color.White,trackColor=Color.White.copy(alpha=.25f))
                }
            }
        }
        item{Row(verticalAlignment=Alignment.CenterVertically){Text("A continuación",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick=calendar){Text("Ver agenda")}};if(next.isEmpty())Text("No quedan eventos para hoy.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        items(next.take(3),key={it.session?.id?:"external-"+it.external?.id}){entry->
            val s=entry.session;val t=tasks.find{it.id==s?.taskId}
            val tint=if(s?.isBreak==true)MaterialTheme.colorScheme.outline else if(s!=null)taskColor(t)else Lilac
            Surface(shape=RoundedCornerShape(16.dp),color=soft(tint,.22f),modifier=Modifier.fillMaxWidth().clickable{if(t!=null&&s?.isBreak==false)edit(t)else calendar()}){
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(time(entry.start),style=MaterialTheme.typography.labelLarge,modifier=Modifier.width(54.dp))
                    Box(Modifier.width(4.dp).height(34.dp).background(if(s?.isBreak==true)MaterialTheme.colorScheme.outline else if(s!=null)taskColor(t)else Lilac,RoundedCornerShape(3.dp)))
                    Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(if(s?.isBreak==true)"Descanso" else entry.external?.title?:t?.title?:"Sesión",style=MaterialTheme.typography.titleMedium);Text(time(entry.start)+" – "+time(entry.end),style=MaterialTheme.typography.bodySmall)}
                    if(s!=null&&!s.isBreak)Checkbox(s.done,{saveSession(s.copy(done=it))})
                }
            }
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){HomeShortcut("Compra",if(buyCount>0)"$buyCount pendientes" else "Al día",Icons.Default.ShoppingCart,Peach,Modifier.weight(1f),shopping);HomeShortcut("Hogar","Inventario",Icons.Default.Home,Mint,Modifier.weight(1f),inventory)}
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){HomeShortcut("Gemini","Asistente",Icons.Default.AutoAwesome,Lilac,Modifier.weight(1f),assistant);HomeShortcut("Concentración","Temporizador",Icons.Default.Timer,Blue,Modifier.weight(1f),focus)}
        }
        item{Row(verticalAlignment=Alignment.CenterVertically){Text("Próximos vencimientos",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick=taskList){Text("Todas")}}}
        items(active.filter{it.due.isNotBlank()}.sortedBy{it.due}.take(3),key={"due-"+it.id}){t->Row(Modifier.fillMaxWidth().clickable{edit(t)}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Text(t.title,Modifier.weight(1f),maxLines=2);Text(t.due,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
    }
}
@Composable private fun HomeShortcut(title:String,detail:String,icon:ImageVector,tint:Color,modifier:Modifier,onClick:()->Unit){
    Surface(modifier.clickable(onClick=onClick),shape=RoundedCornerShape(20.dp),color=soft(tint,.29f)){
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(40.dp).background(tint,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(icon,null,tint=Color.White,modifier=Modifier.size(23.dp))};Spacer(Modifier.weight(1f));Icon(Icons.Default.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(18.dp))}
            Text(title,style=MaterialTheme.typography.titleMedium,maxLines=1);Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
        }
    }
}
