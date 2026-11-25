package es.planidroid.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import es.planidroid.app.domain.*
import java.time.*

@Composable fun TaskEditor(initial:Task,groups:List<Group>,close:()->Unit,save:(Task)->Unit,delete:()->Unit,plan:(Task)->Unit,duplicate:(Task)->Unit,allTasks:List<Task> = emptyList()){
    var t by remember(initial){mutableStateOf(initial)};var minutes by remember(initial){mutableStateOf(initial.minutes.toString())};var tags by remember(initial){mutableStateOf(initial.tags.joinToString(", "))};var check by remember{mutableStateOf("")};var phase by remember{mutableStateOf("")};var advanced by remember{mutableStateOf(false)};var objectives by remember{mutableStateOf(initial.checks.isNotEmpty())};var phasesOpen by remember{mutableStateOf(initial.phases.isNotEmpty())};var remove by remember{mutableStateOf(false)};var phaseEdit by remember{mutableStateOf<Phase?>(null)};var error by remember{mutableStateOf("")}
    val ctx=LocalContext.current
    val contact=rememberLauncherForActivityResult(ActivityResultContracts.PickContact()){uri->if(uri!=null){runCatching{ctx.contentResolver.query(uri,arrayOf(ContactsContract.Contacts.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())t=t.copy(contact=it.getString(0).orEmpty())}}.onFailure{error="No se pudo leer el contacto"}}}
    fun validated(action:(Task)->Unit){val n=minutes.toIntOrNull();if(t.title.isBlank()||n==null||n !in 0..100000){error="Escribe un título y una estimación válida";return};if((t.category==Category.DEADLINE&&t.due.isBlank())||(t.due.isNotBlank()&&runCatching{LocalDate.parse(t.due)}.isFailure)){error="Introduce una fecha válida AAAA-MM-DD";return};if(t.recurrence!=Recurrence.NONE&&t.due.isBlank()){error="Indica una fecha límite para repetir";return};if(TaskConstraints.cycle(t,allTasks)){error="Las dependencias no pueden formar ciclos";return};if(t.startDate.isNotBlank()&&runCatching{LocalDate.parse(t.startDate)}.isFailure){error="Fecha de inicio inválida";return};if(t.startDate.isNotBlank()&&t.due.isNotBlank()&&t.startDate>t.due){error="El inicio debe preceder a la fecha límite";return};if(runCatching{LocalTime.parse(t.reminderTime)}.isFailure){error="Hora de aviso inválida";return};action(t.copy(title=t.title.trim(),minutes=n,tags=tags.split(',').map{it.trim()}.filter{it.isNotEmpty()}))}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxSize()){LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){Text("Detalle de tarea",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);TextButton(onClick=close){Text("Cerrar")}};Field(t.title,{t=t.copy(title=it)},"Título");DateField(t.due,{t=t.copy(due=it,category=if(it.isBlank()&&t.category==Category.DEADLINE)Category.INBOX else if(it.isNotBlank())Category.DEADLINE else t.category)},"Fecha límite");if(t.description.isNotBlank())Text(t.description,style=MaterialTheme.typography.bodyMedium);Row(verticalAlignment=Alignment.CenterVertically){Checkbox(t.status==Status.DONE,{t=t.copy(status=if(it)Status.DONE else Status.PENDING)});Text("Completada")};TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Ocultar detalles" else "Más detalles")}}
        if(advanced)item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Field(t.description,{t=t.copy(description=it)},"Descripción y notas",false);Choice("Categoría",Category.entries,t.category,{it.label}){t=t.copy(category=it)};Choice("Estado",Status.entries,t.status,{it.label}){t=t.copy(status=it)};Field(minutes,{minutes=it},"Estimación (minutos)");Field(t.actualMinutes.toString(),{v->v.toIntOrNull()?.takeIf{it in 0..100000}?.let{t=t.copy(actualMinutes=it)}},"Minutos realizados");DateField(t.startDate,{t=t.copy(startDate=it)},"Fecha de inicio");Choice("Repetir al completar",Recurrence.entries,t.recurrence,{it.label}){t=t.copy(recurrence=it)};Field(t.goal,{t=t.copy(goal=it)},"Objetivo final",false);Choice("Prioridad",Priority.entries,t.priority,{it.label}){t=t.copy(priority=it)};Field(tags,{tags=it},"Etiquetas separadas por comas");Choice("Proyecto",listOf("")+groups.map{it.id},t.groupId,{id->groups.find{it.id==id}?.name ?: "Ninguno"}){t=t.copy(groupId=it)};Row(verticalAlignment=Alignment.CenterVertically){Checkbox(t.reminder,{t=t.copy(reminder=it)});Text("Recordatorio de fecha límite")};if(t.reminder){Field(t.reminderTime,{t=t.copy(reminderTime=it)},"Hora HH:mm");Choice("Antelación",listOf(0,1,2,7),t.reminderBeforeDays,{if(it==0)"Mismo día" else "$it días antes"}){t=t.copy(reminderBeforeDays=it)}};Field(t.contact,{t=t.copy(contact=it)},"Contacto local");Field(t.phone,{t=t.copy(phone=it)},"Teléfono local");Row{TextButton(onClick={contact.launch(null)}){Text("Elegir contacto")};if(t.phone.isNotBlank())TextButton(onClick={runCatching{ctx.startActivity(Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",t.phone,null)))}.onFailure{error="No hay marcador disponible"}}){Text("Llamar")}}}}
        if(advanced)item{GlassCard{Text("Dependencias",style=MaterialTheme.typography.titleMedium);Text("No se planificará hasta completar sus requisitos.");allTasks.filter{it.id!=t.id&&it.status!=Status.ARCHIVED}.forEach{dep->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(dep.id in t.dependencies,{v->t=t.copy(dependencies=if(v)(t.dependencies+dep.id).distinct()else t.dependencies-dep.id)});Text(dep.title)}}}}
        item{TextButton(onClick={objectives=!objectives}){Text(if(objectives)"Ocultar objetivos" else "Añadir objetivos")}};if(objectives)item{GlassCard{Text("Checklist y objetivos",style=MaterialTheme.typography.titleMedium);t.checks.forEach{c->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(c.done,{v->t=t.copy(checks=t.checks.map{if(it.id==c.id)it.copy(done=v) else it})});Text(c.title,Modifier.weight(1f));TextButton(onClick={t=t.copy(checks=t.checks.filterNot{it.id==c.id})}){Text("Quitar")}}};Field(check,{check=it},"Nuevo objetivo");TextButton(enabled=check.isNotBlank(),onClick={t=t.copy(checks=t.checks+Check(title=check.trim()));check=""}){Text("Añadir objetivo")}}}
        item{TextButton(onClick={phasesOpen=!phasesOpen}){Text(if(phasesOpen)"Ocultar fases" else "Añadir fases")}};if(phasesOpen)item{GlassCard{Text("Fases",style=MaterialTheme.typography.titleMedium);t.phases.forEach{p->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(p.done,{v->t=t.copy(phases=t.phases.map{if(it.id==p.id)it.copy(done=v) else it})});Text(p.title,Modifier.weight(1f));TextButton(onClick={phaseEdit=p}){Text("Editar")};TextButton(onClick={t=t.copy(phases=t.phases.filterNot{it.id==p.id})}){Text("Quitar")}};Row(verticalAlignment=Alignment.CenterVertically){Text("${p.minutes} min",Modifier.weight(1f));TextButton(onClick={val a=t.phases.toMutableList();val i=a.indexOf(p);if(i>0){a.removeAt(i);a.add(i-1,p);t=t.copy(phases=a)}}){Text("↑")};TextButton(onClick={val a=t.phases.toMutableList();val i=a.indexOf(p);if(i<a.lastIndex){a.removeAt(i);a.add(i+1,p);t=t.copy(phases=a)}}){Text("↓")};TextButton(onClick={t=t.copy(phases=t.phases.map{if(it.id==p.id)it.copy(minutes=(it.minutes-15).coerceAtLeast(0))else it})}){Text("−15")};TextButton(onClick={t=t.copy(phases=t.phases.map{if(it.id==p.id)it.copy(minutes=(it.minutes+15).coerceAtMost(100000))else it})}){Text("+15")}}};Field(phase,{phase=it},"Nueva fase");TextButton(enabled=phase.isNotBlank(),onClick={t=t.copy(phases=t.phases+Phase(title=phase.trim()));phase=""}){Text("Añadir fase")}}}
        item{if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);Button(onClick={validated(save)},modifier=Modifier.fillMaxWidth()){Text("Guardar tarea")};OutlinedButton(enabled=TaskConstraints.unmet(t,allTasks).isEmpty(),onClick={validated(plan)},modifier=Modifier.fillMaxWidth()){Text("Guardar y planificar")};if(advanced)Row{TextButton(onClick={validated(duplicate)}){Text("Duplicar")};TextButton(onClick={remove=true}){Text("Eliminar")}}}
    }}}
    phaseEdit?.let{p->PhaseDetail(p,{phaseEdit=null},{v->t=t.copy(phases=t.phases.map{if(it.id==v.id)v else it});phaseEdit=null})}
    if(remove)Confirm("Eliminar tarea","Se eliminarán también sus sesiones.",{remove=false},delete)
}
@Composable fun PlanDialog(task:Task,existing:List<Session>,close:()->Unit,accept:(List<Session>)->Unit,external:List<Interval> = emptyList()){
    val today=LocalDate.now()
    var start by remember(task.id){mutableStateOf(task.startDate.ifBlank{today.toString()})}
    var deadline by remember(task.id){mutableStateOf(task.due.ifBlank{today.plusDays(7).toString()})}
    var from by remember(task.id){mutableStateOf("09:00")}
    var until by remember(task.id){mutableStateOf("19:00")}
    var max by remember(task.id){mutableStateOf("60")}
    var daily by remember(task.id){mutableStateOf(task.planHints.dailyMinutes.takeIf{it>0}?.toString().orEmpty())}
    var minutes by remember(task.id){mutableStateOf(TaskConstraints.remaining(task,existing).takeIf{it>0}?.toString().orEmpty())}
    var days by remember(task.id){mutableStateOf(DayOfWeek.entries.filter{it.value !in task.planHints.excludedWeekdays}.toSet())}
    var advanced by remember{mutableStateOf(false)}
    var approved by remember(task.id){mutableStateOf(false)}
    val result=remember(task,existing,external,start,deadline,from,until,max,daily,minutes,days){
        runCatching{SmartPlan.propose(task,existing,external,maxOf(today,LocalDate.parse(start)),LocalDate.parse(deadline),days,LocalTime.parse(from),LocalTime.parse(until),max.toInt(),minutes.toInt(),daily.ifBlank{"0"}.toInt())}.getOrNull()
    }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.95f).padding(10.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp)){
            LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                item{
                    Section("Tu plan",task.title)
                    if(task.planHints.explanation.isNotBlank())Text(task.planHints.explanation,style=MaterialTheme.typography.bodySmall)
                    Field(minutes,{minutes=it},"Tiempo pendiente (min)")
                    Text("Fecha límite: "+deadline,style=MaterialTheme.typography.titleMedium)
                    Text("Disponibilidad propuesta: "+from+" – "+until+". Confirma que se ajusta a tu horario.",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Ocultar horario" else "Ajustar disponibilidad")}
                    if(advanced){
                        DateField(start,{start=it},"Desde",false);DateField(deadline,{deadline=it},"Hasta",false)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Field(from,{from=it},"Desde HH:mm")};Column(Modifier.weight(1f)){Field(until,{until=it},"Hasta HH:mm")}}
                        Field(max,{max=it},"Máximo por sesión (min)");Field(daily,{daily=it},"Máximo diario (opcional)")
                        DayOfWeek.entries.chunked(4).forEach{group->Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){group.forEach{d->FilterChip(d in days,{days=if(d in days)days-d else days+d},label={Text(listOf("L","M","X","J","V","S","D")[d.value-1])})}}}
                    }
                    if(result==null)Text("Indica el tiempo y revisa la disponibilidad para obtener una propuesta.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    else GlassCard(tint=if(result.missingMinutes==0)Mint else Peach){
                        Text(if(result.missingMinutes==0)"Todo encaja" else "Faltan "+result.missingMinutes+" min",style=MaterialTheme.typography.titleLarge)
                        Text(result.intervals.size.toString()+" sesiones · "+result.intervals.sumOf{(it.end-it.start)/60000}+" min reservados")
                    }
                }
                result?.intervals?.groupBy{sessionDate(it.start)}?.forEach{(day,blocks)->item{Text(day.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM",java.util.Locale("es","ES"))),style=MaterialTheme.typography.titleMedium)};items(blocks.size){i->val s=blocks[i];GlassCard(tint=Blue){Text(time(s.start)+" – "+time(s.end),style=MaterialTheme.typography.titleMedium);Text(((s.end-s.start)/60000).toString()+" min")}}}
                item{Button(enabled=!approved&&result?.intervals?.isNotEmpty()==true,onClick={val r=result?:return@Button;approved=true;accept(r.intervals.map{Session(taskId=task.id,start=it.start,end=it.end)})},modifier=Modifier.fillMaxWidth()){Text(if(result?.missingMinutes==0)"Añadir a mi agenda" else "Aceptar plan parcial")};TextButton(onClick=close){Text("Cancelar")}}
            }
        }
    }
}
