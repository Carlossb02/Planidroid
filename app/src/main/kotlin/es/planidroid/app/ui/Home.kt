package es.planidroid.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import es.planidroid.app.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanish=Locale.forLanguageTag("es-ES")
private fun dayLabel(day:LocalDate,pattern:String="EEEE d 'de' MMMM")=
    day.format(DateTimeFormatter.ofPattern(pattern,spanish)).replaceFirstChar{it.uppercase(spanish)}

@Composable internal fun Home(
    tasks:List<Task>,sessions:List<Session>,openEntry:(Session)->Unit,openTask:(Task)->Unit,
    inventory:()->Unit,shopping:()->Unit,calendar:()->Unit,taskList:()->Unit,buyCount:Int,
    focus:()->Unit,assistant:()->Unit
){
    val today=LocalDate.now()
    val now=System.currentTimeMillis()
    val taskMap=tasks.associateBy{it.id}
    val agenda=sessions.filterNot{it.isBreak}.sortedBy{it.start}
    val upcoming=agenda.filter{!it.done&&it.end>now}.take(4)
    val recent=agenda.filter{it.start<=now&&(it.done||it.end<=now)&&sessionDate(it.end-1)>=today.minusDays(7)}
        .sortedByDescending{it.end}.take(4)
    val todayEntries=agenda.filter{sessionDate(it.start)<=today&&sessionDate(it.end-1)>=today}
    val events=todayEntries.count{it.calendarTitle.isNotBlank()}
    val sessionsToday=todayEntries.size-events
    val anchors=sessions.filter{it.calendarTitle.isNotBlank()}.mapTo(mutableSetOf()){it.taskId}
    val deadlines=tasks.filter{it.id !in anchors&&it.status !in listOf(Status.DONE,Status.ARCHIVED)&&it.due.isNotBlank()}
        .sortedBy{it.due}.take(3)

    BoxWithConstraints(Modifier.fillMaxSize()){
        val wide=maxWidth>=740.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal=if(wide)28.dp else ScreenGutter,vertical=16.dp),
            verticalArrangement=Arrangement.spacedBy(22.dp)){
            Surface(onClick=calendar,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth(),
                color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer){
                Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(dayLabel(today),style=MaterialTheme.typography.headlineSmall)
                        val summary=listOfNotNull(
                            if(events>0)if(events==1)"1 evento" else "$events eventos" else null,
                            if(sessionsToday>0)if(sessionsToday==1)"1 sesión" else "$sessionsToday sesiones" else null)
                        Text(summary.joinToString(" · ").ifBlank{"Agenda despejada"},style=MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.Default.CalendarMonth,"Abrir calendario",Modifier.size(28.dp))
                }
            }
            val timeline:@Composable ()->Unit={
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("A continuación",style=MaterialTheme.typography.titleLarge)
                    if(upcoming.isEmpty())Text("No tienes nada programado.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    upcoming.forEach{entry->AgendaEntryCard(entry,taskMap[entry.taskId],today){openEntry(entry)}}
                    if(recent.isNotEmpty()){
                        Spacer(Modifier.height(6.dp))
                        Text("Recientes",style=MaterialTheme.typography.titleLarge)
                        recent.forEach{entry->AgendaEntryCard(entry,taskMap[entry.taskId],today){openEntry(entry)}}
                    }
                }
            }
            val extras:@Composable ()->Unit={
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("Accesos rápidos",style=MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        HomeShortcut("Compra",if(buyCount==0)"Al día" else "$buyCount pendientes",Icons.Default.ShoppingCart,Peach,Modifier.weight(1f),shopping)
                        HomeShortcut("Hogar","Inventario",Icons.Default.Home,Mint,Modifier.weight(1f),inventory)
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        HomeShortcut("Gemini","Asistente",Icons.Default.AutoAwesome,Lilac,Modifier.weight(1f),assistant)
                        HomeShortcut("Concentración","Temporizador",Icons.Default.Timer,Blue,Modifier.weight(1f),focus)
                    }
                    if(deadlines.isNotEmpty()){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Text("Vencimientos",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                            TextButton(onClick=taskList){Text("Todas")}
                        }
                        deadlines.forEach{task->
                            Surface(onClick={openTask(task)},shape=RoundedCornerShape(12.dp)){
                                Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                    Text(task.title,Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                                    Text(task.due,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if(wide)Row(horizontalArrangement=Arrangement.spacedBy(22.dp)){
                Box(Modifier.weight(3f)){timeline()}
                Box(Modifier.weight(2f)){extras()}
            } else {
                timeline()
                extras()
            }
        }
    }
}

private fun entryTitle(entry:Session,task:Task?)=entry.calendarTitle.ifBlank{
    if(entry.isBreak)"Descanso" else task?.title?:"Sesión"
}

private fun entryTime(entry:Session,today:LocalDate?=null):String{
    val start=sessionDate(entry.start)
    val allDay=isAllDayEvent(entry)
    val end=sessionDate(entry.end-if(allDay)1 else 0)
    val date=when(start){
        today->"Hoy"
        today?.minusDays(1)->"Ayer"
        else->dayLabel(start,if(today!=null)"EEE d MMM" else "EEEE d 'de' MMMM")
    }
    val startTime=time(entry.start)
    val endTime=time(entry.end)
    val endDate=dayLabel(end)
    return when{
        allDay&&start==end->"$date · Todo el día"
        allDay->"$date – $endDate · Todo el día"
        start==end->"$date · $startTime–$endTime"
        else->"$date, $startTime – $endDate, $endTime"
    }
}

@Composable internal fun AgendaEntryCard(entry:Session,task:Task?,today:LocalDate,open:()->Unit){
    val fixed=entry.calendarTitle.isNotBlank()
    val tint=if(fixed)MaterialTheme.colorScheme.tertiary else taskColor(task)
    Surface(onClick=open,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Surface(shape=RoundedCornerShape(12.dp),color=soft(tint,.16f)){
                Icon(if(fixed)Icons.Default.Event else if(entry.done)Icons.Default.CheckCircle else Icons.Default.Schedule,
                    null,Modifier.padding(10.dp).size(22.dp),tint=tint)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                Text(entryTitle(entry,task),style=MaterialTheme.typography.titleMedium)
                Text(entryTime(entry,today),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                val preview=if(fixed)task?.goal?.ifBlank{task.description} else task?.description
                if(!preview.isNullOrBlank())Text(preview,style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun AgendaEntryDetails(entry:Session,task:Task?,close:()->Unit,onEdit:()->Unit,onDone:(Boolean)->Unit){
    val fixed=entry.calendarTitle.isNotBlank()
    Dialog(onDismissRequest=close){
        Surface(shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(16.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick=close){Icon(Icons.Default.Close,"Cerrar detalles")}
                    Text(if(fixed)"Evento" else if(entry.isBreak)"Descanso" else "Sesión",
                        modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    if(!fixed||task!=null)IconButton(onClick=onEdit){
                        Icon(Icons.Default.Edit,if(fixed)"Editar evento" else "Editar sesión")
                    }
                }
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(8.dp),
                    verticalArrangement=Arrangement.spacedBy(14.dp)){
                    Text(entryTitle(entry,task),style=MaterialTheme.typography.headlineSmall)
                    DetailLine(Icons.Default.Schedule,entryTime(entry))
                    if(fixed&&task!=null){
                        if(task.goal.isNotBlank())DetailLine(Icons.Default.LocationOn,task.goal)
                        if(task.reminder)DetailLine(Icons.Default.Notifications,"Recordatorio activado")
                        DetailLine(Icons.Default.EventAvailable,
                            if(OverlapPolicy.calendarFree(entry,task))"Disponible durante este evento" else "Tiempo reservado")
                    }
                    task?.description?.takeIf{it.isNotBlank()}?.let{description->
                        Text("Notas",style=MaterialTheme.typography.titleMedium)
                        Text(description)
                    }
                    if(!fixed&&!entry.isBreak)FilledTonalButton(onClick={onDone(!entry.done)}){
                        Text(if(entry.done)"Marcar como pendiente" else "Marcar como hecha")
                    }
                }
            }
        }
    }
}

@Composable private fun DetailLine(icon:ImageVector,text:String){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary)
        Text(text,style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun HomeShortcut(title:String,detail:String,icon:ImageVector,tint:Color,modifier:Modifier,onClick:()->Unit){
    Surface(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(14.dp)){
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Icon(icon,null,tint=tint)
            Text(title,style=MaterialTheme.typography.titleSmall)
            Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
