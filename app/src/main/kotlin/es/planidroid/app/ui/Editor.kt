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
@Composable fun PlanDialog(task:Task,existing:List<Session>,close:()->Unit,accept:(Task,List<Session>,List<Session>)->Unit,external:List<Interval> = emptyList()){
    val today=LocalDate.now()
    val capturedNow=remember(task.id){System.currentTimeMillis()}
    val previous=existing.filter{it.taskId==task.id&&!it.done&&it.start>capturedNow}
    val kept=existing.filterNot{it in previous}
    val doneMinutes=maxOf(task.actualMinutes,kept.filter{it.taskId==task.id&&it.done}.sumOf{((it.end-it.start)/60000).toInt()})
    val otherReserved=kept.filter{it.taskId==task.id&&!it.done}.sumOf{((it.end-it.start)/60000).toInt()}
    var hints by remember(task.id){mutableStateOf(task.planHints)}
    var start by remember(task.id){mutableStateOf(task.startDate.ifBlank{today.toString()})}
    var deadline by remember(task.id){mutableStateOf(task.due.ifBlank{today.plusDays(7).toString()})}
    var from by remember(task.id){mutableStateOf(hints.windowStart.ifBlank{"09:00"})}
    var until by remember(task.id){mutableStateOf(hints.windowEnd.ifBlank{"19:00"})}
    var max by remember(task.id){mutableStateOf(hints.sessionLimit.coerceIn(1,1440).toString())}
    var daily by remember(task.id){mutableStateOf(hints.dailyMinutes.takeIf{it>0}?.toString().orEmpty())}
    var countText by remember(task.id){mutableStateOf(hints.topicCount.takeIf{it>0}?.toString().orEmpty())}
    var minutesText by remember(task.id){mutableStateOf(TaskConstraints.remaining(task,kept).takeIf{it>0}?.toString().orEmpty())}
    var days by remember(task.id){mutableStateOf(StudyIntent.availableDays(hints))}
    var revision by remember(task.id){mutableStateOf("")}
    var feedback by remember(task.id){mutableStateOf("")}
    var advanced by remember{mutableStateOf(false)}
    var approved by remember(task.id){mutableStateOf(false)}
    val count=StudyIntent.topicsAnswer(countText)
    val total=if(hints.minutesPerTopic>0)count?.let{runCatching{StudyIntent.studyMinutes(hints,it)}.getOrNull()} else minutesText.toIntOrNull()?.takeIf{it in 1..100000}?.let{(it.toLong()+doneMinutes+otherReserved).takeIf{n->n<=100000}?.toInt()}
    val remaining=total?.let{(it-doneMinutes-otherReserved).coerceAtLeast(0)}
    val last=runCatching{LocalDate.parse(deadline).let{if(hints.finishBeforeDeadline)it.minusDays(1)else it}}.getOrNull()
    val result=remember(task,kept,external,start,last,from,until,max,daily,remaining,days){
        if(last==null||remaining==null||remaining<=0)null
        else runCatching{SmartPlan.propose(task,kept,external,maxOf(today,LocalDate.parse(start)),last,days,LocalTime.parse(from),LocalTime.parse(until),max.toInt(),remaining,daily.ifBlank{"0"}.toInt())}.getOrNull()
    }
    val weekdayLabels=listOf("Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo")
    val shortLabels=listOf("L","M","X","J","V","S","D")
    fun applyRevision(){
        val next=StudyIntent.reviseAvailability(revision,hints)
        val n=StudyIntent.topicsAnswer(revision)
        if(next==null&&n==null){feedback="Indica los días disponibles o el número de temas.";return}
        if(next!=null){hints=next;days=StudyIntent.availableDays(next)}
        if(n!=null&&hints.minutesPerTopic>0)countText=n.toString()
        revision="";feedback="Propuesta recalculada. Revisa las sesiones."
    }
    fun toggleDay(day:DayOfWeek){
        days=if(day in days)days-day else days+day
        hints=hints.copy(availableWeekdays=days.map{it.value}.sorted(),daysPerWeek=days.size,excludedWeekdays=hints.excludedWeekdays.filterNot{it==day.value})
        feedback=""
    }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.95f).padding(10.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp)){
            LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                item{
                    Section(if(previous.isEmpty())"Tu plan" else "Reorganizar mi agenda",task.title)
                    Text("Fecha límite: "+deadline,style=MaterialTheme.typography.labelLarge)
                    if(hints.minutesPerTopic>0){
                        Text("¿Cuántos temas tienes que estudiar?",style=MaterialTheme.typography.titleLarge)
                        Text((hints.minutesPerTopic/60.0).let{if(it%1.0==0.0)it.toInt().toString() else it.toString()}+" h por tema",style=MaterialTheme.typography.bodyMedium)
                        Field(countText,{countText=it},"Número de temas")
                        if(total!=null)Text(total.toString()+" min en total · "+remaining+" min pendientes",style=MaterialTheme.typography.titleMedium)
                    }else{
                        if(hints.explanation.isNotBlank())Text(hints.explanation,style=MaterialTheme.typography.bodySmall)
                        Field(minutesText,{minutesText=it},"Tiempo pendiente (min)")
                    }
                    if(previous.isNotEmpty())Text("Se sustituirán "+previous.size+" sesiones futuras. El trabajo completado se conserva.",style=MaterialTheme.typography.bodySmall)
                }
                item{
                    GlassCard(tint=Blue){
                        Text("Cuándo puedes estudiar",style=MaterialTheme.typography.titleLarge)
                        if(hints.daysPerWeek>0&&hints.availableWeekdays.isEmpty())Text("Propuesta de "+hints.daysPerWeek+" días semanales. Puedes cambiarlos.",style=MaterialTheme.typography.bodySmall)
                        Text(days.sortedBy{it.value}.joinToString(", "){weekdayLabels[it.value-1]}.ifBlank{"Selecciona al menos un día"},style=MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                            DayOfWeek.entries.forEach{d->FilterChip(selected=d in days,onClick={toggleDay(d)},label={Text(shortLabels[d.value-1])},modifier=Modifier.weight(1f))}
                        }
                        Field(revision,{revision=it.take(300)},"Cambiar con texto")
                        OutlinedButton(onClick={applyRevision()},enabled=revision.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Recalcular")}
                        if(feedback.isNotBlank())Text(feedback,style=MaterialTheme.typography.bodySmall)
                        Text("Horario propuesto: "+from+" – "+until,style=MaterialTheme.typography.labelMedium)
                        TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Ocultar ajustes" else "Ajustar horario y duración")}
                        if(advanced){
                            DateField(start,{start=it},"Desde",false);DateField(deadline,{deadline=it},"Fecha límite",false)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Field(from,{from=it},"Desde HH:mm")};Column(Modifier.weight(1f)){Field(until,{until=it},"Hasta HH:mm")}}
                            Field(max,{max=it},"Máximo por sesión (min)");Field(daily,{daily=it},"Máximo diario (opcional)")
                        }
                    }
                }
                item{
                    when{
                        hints.minutesPerTopic>0&&total==null->Text("Indica cuántos temas hay para generar una planificación real.",style=MaterialTheme.typography.bodyMedium)
                        days.isEmpty()->Text("Selecciona tus días de estudio.",color=MaterialTheme.colorScheme.error)
                        remaining==0->Text("Ya has reservado o completado todo el tiempo necesario.")
                        result==null->Text("Revisa la fecha, la duración y el horario.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        else->GlassCard(tint=if(result.missingMinutes==0)Mint else Peach){
                            Text(if(result.missingMinutes==0)"Tu planificación" else "Faltan "+result.missingMinutes+" min",style=MaterialTheme.typography.titleLarge)
                            Text(result.intervals.map{sessionDate(it.start)}.distinct().size.toString()+" días · "+result.intervals.sumOf{(it.end-it.start)/60000}+" min reservados")
                            if(hints.finishBeforeDeadline)Text("El día del examen queda libre.",style=MaterialTheme.typography.bodySmall)
                            if(result.missingMinutes>0)Text("Amplía el horario o añade días. No se han reservado horas que no caben.",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                result?.intervals?.groupBy{sessionDate(it.start)}?.forEach{(day,blocks)->
                    item{
                        GlassCard(tint=Blue){
                            Text(day.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM",java.util.Locale("es","ES"))).replaceFirstChar{it.uppercase()},style=MaterialTheme.typography.titleMedium)
                            Text(blocks.sumOf{(it.end-it.start)/60000}.toString()+" min de estudio",style=MaterialTheme.typography.labelLarge)
                            blocks.forEach{s->Text(time(s.start)+" – "+time(s.end),style=MaterialTheme.typography.bodyMedium)}
                        }
                    }
                }
                item{
                    Button(enabled=!approved&&result?.intervals?.isNotEmpty()==true&&total!=null,onClick={
                        val r=result?:return@Button;val effort=total?:return@Button
                        approved=true
                        val updated=task.copy(minutes=effort,due=deadline,category=Category.DEADLINE,startDate=start,planHints=hints.copy(topicCount=count?:hints.topicCount,dailyMinutes=daily.ifBlank{"0"}.toInt(),availableWeekdays=days.map{it.value}.sorted(),daysPerWeek=days.size,windowStart=from,windowEnd=until,sessionLimit=max.toInt(),explanation=""))
                        accept(updated,r.intervals.map{Session(taskId=task.id,start=it.start,end=it.end)},previous)
                    },modifier=Modifier.fillMaxWidth()){Text(if(result?.missingMinutes==0)if(previous.isEmpty())"Guardar tarea y agenda" else "Guardar nueva planificación" else "Aceptar plan parcial")}
                    TextButton(onClick=close){Text("Cancelar")}
                }
            }
        }
    }
}
