package es.planidroid.app.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*
internal data class CalendarEntry(val start:Long,val end:Long,val session:Session?,val external:ExternalEvent?)
private val palette=listOf(Color(0xFF3978B8),Color(0xFF8A5BB0),Color(0xFF388B76),Color(0xFFB86A42),Color(0xFFAD517C),Color(0xFF5776A9),Color(0xFF8B7938),Color(0xFF4B8B9B),Color(0xFF946544),Color(0xFF6478C0))
fun taskColor(task:Task?):Color=palette[Math.floorMod((task?.let{it.groupId.ifBlank{it.id}}?:"").hashCode(),palette.size)]
@Composable fun CalendarSessionCard(s:Session,task:Task?,edit:()->Unit,done:(Boolean)->Unit){
    val tint=taskColor(task)
    Surface(Modifier.fillMaxWidth().clickable(onClick=edit),shape=RoundedCornerShape(18.dp),color=soft(tint,.20f)){
        Row(Modifier.height(IntrinsicSize.Min),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.width(5.dp).fillMaxHeight().background(tint))
            Column(Modifier.weight(1f).padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(time(s.start)+" – "+time(s.end)+(if(sessionDate(s.end-1)!=sessionDate(s.start))" (+1 día)" else ""),style=MaterialTheme.typography.labelMedium)
                Text(task?.title?:"Tarea eliminada",style=MaterialTheme.typography.titleMedium)
                task?.phases?.find{it.id==s.phaseId}?.let{Text(it.title,style=MaterialTheme.typography.bodySmall)}
                Text(((s.end-s.start)/60000).toString()+" min",style=MaterialTheme.typography.labelSmall)
            }
            Checkbox(s.done,done,modifier=Modifier.padding(end=8.dp))
        }
    }
}
@Composable fun CalendarExternalCard(e:ExternalEvent){
    Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()){
        Row(Modifier.height(IntrinsicSize.Min)){
            Box(Modifier.width(5.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
            Column(Modifier.padding(14.dp)){Text(time(e.start)+" – "+time(e.end),style=MaterialTheme.typography.labelMedium);Text(e.title,style=MaterialTheme.typography.titleMedium);Text("Google Calendar · cita externa",style=MaterialTheme.typography.labelSmall)}
        }
    }
}
