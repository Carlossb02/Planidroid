package es.planidroid.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*
import java.time.*

@Composable internal fun CalendarFileDialog(
    file:CalendarFile,close:()->Unit,save:(List<CalendarDraft>,(String?)->Unit)->Unit
){
    var events by remember(file){mutableStateOf(file.events)}
    var selected by remember(file){mutableStateOf(file.events.map{it.sourceId}.toSet())}
    var editing by remember(file){mutableStateOf<CalendarDraft?>(null)}
    var busy by remember(file){mutableStateOf(false)}
    var error by remember(file){mutableStateOf("")}

    AlertDialog(onDismissRequest={if(!busy)close()},title={Text("Revisar eventos")},text={
        LazyColumn(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(events.isEmpty())item{Text("El archivo no contiene eventos que se puedan añadir.")}
            if(file.skipped>0||file.recurrence||file.assumedTimeZone)item{
                if(file.skipped>0)Text(file.skipped.toString()+" entradas omitidas por estar canceladas, duplicadas o incompletas.",style=MaterialTheme.typography.bodySmall)
                if(file.recurrence)Text("Las series aportan solo los eventos escritos en el archivo. No se generan repeticiones.",style=MaterialTheme.typography.bodySmall)
                if(file.assumedTimeZone)Text("Algunas zonas horarias no se reconocieron. Comprueba los horarios sugeridos.",style=MaterialTheme.typography.bodySmall)
            }
            if(events.size>1)item{
                TextButton(enabled=!busy,onClick={selected=if(selected.size==events.size)emptySet()else events.map{it.sourceId}.toSet()}){
                    Text(if(selected.size==events.size)"Desmarcar todos" else "Seleccionar todos")
                }
            }
            items(events,key={it.sourceId}){ event->
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(event.sourceId in selected,{selected=if(it)selected+event.sourceId else selected-event.sourceId},enabled=!busy)
                    Column(Modifier.weight(1f)){
                        Text(event.title,style=MaterialTheme.typography.titleSmall)
                        Text(sessionDate(event.start).toString()+" · "+if(event.allDay)"Todo el día" else time(event.start)+" – "+time(event.end),
                            style=MaterialTheme.typography.bodySmall)
                        if(event.location.isNotBlank())Text(event.location,style=MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick={editing=event},enabled=!busy){Icon(Icons.Default.Edit,"Editar "+event.title)}
                }
            }
            if(error.isNotBlank())item{Text(error,color=MaterialTheme.colorScheme.error)}
            if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        }
    },confirmButton={Button(enabled=!busy&&selected.isNotEmpty(),onClick={
        busy=true;error=""
        save(events.filter{it.sourceId in selected}){failure->busy=false;if(failure==null)close()else error=failure}
    }){Text(if(selected.size==1)"Añadir evento" else "Añadir "+selected.size+" eventos")}},
        dismissButton={TextButton(enabled=!busy,onClick=close){Text("Cerrar")}})

    editing?.let{event->
        CalendarEventDialog(event,"Editar antes de añadir",{editing=null},save={updated,complete->
            events=events.map{if(it.sourceId==updated.sourceId)updated else it}
            complete(null)
        })
    }
}

@Composable internal fun CalendarEventDialog(
    initial:CalendarDraft,title:String,close:()->Unit,save:(CalendarDraft,(String?)->Unit)->Unit,
    delete:(()->Unit)?=null
){
    val zone=ZoneId.systemDefault()
    val beginning=Instant.ofEpochMilli(initial.start).atZone(zone)
    val ending=Instant.ofEpochMilli(initial.end).atZone(zone)
    var name by remember(initial){mutableStateOf(initial.title)}
    var allDay by remember(initial){mutableStateOf(initial.allDay)}
    var firstDay by remember(initial){mutableStateOf(beginning.toLocalDate().toString())}
    var lastDay by remember(initial){mutableStateOf((if(initial.allDay)ending.toLocalDate().minusDays(1)else ending.toLocalDate()).toString())}
    var from by remember(initial){mutableStateOf(time(initial.start))}
    var to by remember(initial){mutableStateOf(time(initial.end))}
    var place by remember(initial){mutableStateOf(initial.location)}
    var notes by remember(initial){mutableStateOf(initial.notes)}
    var remind by remember(initial){mutableStateOf(initial.reminder)}
    var blocks by remember(initial){mutableStateOf(initial.blocksTime)}
    var busy by remember(initial){mutableStateOf(false)}
    var error by remember(initial){mutableStateOf("")}
    var confirmDelete by remember{mutableStateOf(false)}
    fun commit(){
        val adjusted=runCatching{
            val begin=LocalDate.parse(firstDay)
            val finish=LocalDate.parse(lastDay)
            require(begin.year in 1900..2200&&finish.year in 1900..2200)
            val originalEnd=if(initial.allDay)ending.toLocalDate().minusDays(1)else ending.toLocalDate()
            val start=if(allDay==initial.allDay&&begin==beginning.toLocalDate()&&(allDay||from==time(initial.start))) initial.start
                else if(allDay)begin.atStartOfDay(zone).toInstant().toEpochMilli()
                else begin.atTime(LocalTime.parse(from)).atZone(zone).toInstant().toEpochMilli()
            val end=if(allDay==initial.allDay&&finish==originalEnd&&(allDay||to==time(initial.end))) initial.end
                else if(allDay)finish.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                else finish.atTime(LocalTime.parse(to)).atZone(zone).toInstant().toEpochMilli()
            require(end>start&&end-start<=367L*86_400_000L)
            initial.copy(title=name.trim(),start=start,end=end,allDay=allDay,location=place.trim(),notes=notes.trim(),reminder=remind,blocksTime=blocks)
        }.getOrElse{error="Comprueba las fechas y horas. El fin debe ser posterior al inicio.";return}
        busy=true
        error=""
        save(adjusted){failure->busy=false;if(failure==null)close()else error=failure}
    }
    AlertDialog(onDismissRequest={if(!busy)close()},title={Text(title)},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Field(name,{name=it.take(180)},"Título")
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Todo el día",Modifier.weight(1f))
                Switch(allDay,{allDay=it},enabled=!busy)
            }
            DateField(firstDay,{value->if(firstDay==lastDay)lastDay=value;firstDay=value},"Fecha de inicio",false)
            if(!allDay)Field(from,{from=it.take(5)},"Hora de inicio (HH:mm)")
            DateField(lastDay,{lastDay=it},"Fecha de fin",false)
            if(!allDay)Field(to,{to=it.take(5)},"Hora de fin (HH:mm)")
            Field(place,{place=it.take(300)},"Lugar")
            Field(notes,{notes=it.take(8000)},"Notas",false)
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Recordatorio al inicio",Modifier.weight(1f))
                Switch(remind,{remind=it},enabled=!busy)
            }
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Bloquear el horario",Modifier.weight(1f))
                Switch(blocks,{blocks=it},enabled=!busy)
            }
            if(delete!=null)TextButton(onClick={confirmDelete=true},enabled=!busy){Text("Eliminar evento",color=MaterialTheme.colorScheme.error)}
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    },confirmButton={Button(onClick=::commit,enabled=!busy&&name.isNotBlank()){Text("Guardar")}},
        dismissButton={TextButton(onClick=close,enabled=!busy){Text("Cancelar")}})
    if(confirmDelete&&delete!=null)Confirm("¿Eliminar evento?","Se quitará del calendario de Planidroid.",{confirmDelete=false},{delete();close()})
}
