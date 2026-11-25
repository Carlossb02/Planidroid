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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import es.planidroid.app.domain.*
import java.time.*
import kotlinx.coroutines.launch

@Composable fun TaskEditor(initial:Task,groups:List<Group>,close:()->Unit,save:(Task)->Unit,delete:()->Unit,plan:(Task)->Unit,duplicate:(Task)->Unit,allTasks:List<Task> = emptyList()){
    var t by remember(initial){mutableStateOf(initial)};var minutes by remember(initial){mutableStateOf(initial.minutes.toString())};var tags by remember(initial){mutableStateOf(initial.tags.joinToString(", "))};var check by remember{mutableStateOf("")};var phase by remember{mutableStateOf("")};var advanced by remember{mutableStateOf(false)};var objectives by remember{mutableStateOf(initial.checks.isNotEmpty())};var phasesOpen by remember{mutableStateOf(initial.phases.isNotEmpty())};var remove by remember{mutableStateOf(false)};var phaseEdit by remember{mutableStateOf<Phase?>(null)};var error by remember{mutableStateOf("")}
    val ctx=LocalContext.current
    val contact=rememberLauncherForActivityResult(ActivityResultContracts.PickContact()){uri->if(uri!=null){runCatching{ctx.contentResolver.query(uri,arrayOf(ContactsContract.Contacts.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())t=t.copy(contact=it.getString(0).orEmpty())}}.onFailure{error="No se pudo leer el contacto"}}}
    fun validated(action:(Task)->Unit){val n=minutes.toIntOrNull();if(t.title.isBlank()||n==null||n !in 0..100000){error="Escribe un título y una estimación válida";return};if((t.category==Category.DEADLINE&&t.due.isBlank())||(t.due.isNotBlank()&&runCatching{LocalDate.parse(t.due)}.isFailure)){error="Introduce una fecha válida AAAA-MM-DD";return};if(t.recurrence!=Recurrence.NONE&&t.due.isBlank()){error="Indica una fecha límite para repetir";return};if(TaskConstraints.cycle(t,allTasks)){error="Las dependencias no pueden formar ciclos";return};if(t.startDate.isNotBlank()&&runCatching{LocalDate.parse(t.startDate)}.isFailure){error="Fecha de inicio inválida";return};if(t.startDate.isNotBlank()&&t.due.isNotBlank()&&t.startDate>t.due){error="El inicio debe preceder a la fecha límite";return};if(runCatching{LocalTime.parse(t.reminderTime)}.isFailure){error="Hora de aviso inválida";return};action(t.copy(title=t.title.trim(),minutes=n,tags=tags.split(',').map{it.trim()}.filter{it.isNotEmpty()}))}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxSize()){LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){Text("Detalle de tarea",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);if(allTasks.any{it.id==initial.id})IconButton(onClick={remove=true}){Icon(androidx.compose.material.icons.Icons.Default.DeleteOutline,"Eliminar tarea",tint=MaterialTheme.colorScheme.error)};TextButton(onClick=close){Text("Cerrar")}};Field(t.title,{t=t.copy(title=it)},"Título");DateField(t.due,{t=t.copy(due=it,category=if(it.isBlank()&&t.category==Category.DEADLINE)Category.INBOX else if(it.isNotBlank())Category.DEADLINE else t.category)},"Fecha límite");if(t.description.isNotBlank())Text(t.description,style=MaterialTheme.typography.bodyMedium);Row(verticalAlignment=Alignment.CenterVertically){Checkbox(t.status==Status.DONE,{t=t.copy(status=if(it)Status.DONE else Status.PENDING)});Text("Completada")};TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Ocultar detalles" else "Más detalles")}}
        if(advanced)item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Field(t.description,{t=t.copy(description=it)},"Descripción y notas",false);Choice("Categoría",Category.entries,t.category,{it.label}){t=t.copy(category=it)};Choice("Estado",Status.entries,t.status,{it.label}){t=t.copy(status=it)};Field(minutes,{minutes=it},"Estimación (minutos)");Field(t.actualMinutes.toString(),{v->v.toIntOrNull()?.takeIf{it in 0..100000}?.let{t=t.copy(actualMinutes=it)}},"Minutos realizados");DateField(t.startDate,{t=t.copy(startDate=it)},"Fecha de inicio");Choice("Repetir al completar",Recurrence.entries,t.recurrence,{it.label}){t=t.copy(recurrence=it)};Field(t.goal,{t=t.copy(goal=it)},"Objetivo final",false);Choice("Prioridad",Priority.entries,t.priority,{it.label}){t=t.copy(priority=it)};Field(tags,{tags=it},"Etiquetas separadas por comas");Choice("Proyecto",listOf("")+groups.map{it.id},t.groupId,{id->groups.find{it.id==id}?.name ?: "Ninguno"}){t=t.copy(groupId=it)};Row(verticalAlignment=Alignment.CenterVertically){Checkbox(t.reminder,{t=t.copy(reminder=it)});Text("Recordatorio de fecha límite")};if(t.reminder){Field(t.reminderTime,{t=t.copy(reminderTime=it)},"Hora HH:mm");Choice("Antelación",listOf(0,1,2,7),t.reminderBeforeDays,{if(it==0)"Mismo día" else "$it días antes"}){t=t.copy(reminderBeforeDays=it)}};Field(t.contact,{t=t.copy(contact=it)},"Contacto local");Field(t.phone,{t=t.copy(phone=it)},"Teléfono local");Row{TextButton(onClick={contact.launch(null)}){Text("Elegir contacto")};if(t.phone.isNotBlank())TextButton(onClick={runCatching{ctx.startActivity(Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",t.phone,null)))}.onFailure{error="No hay marcador disponible"}}){Text("Llamar")}}}}
        if(advanced)item{GlassCard{Text("Permitir solapamiento",style=MaterialTheme.typography.titleMedium);Text("Por defecto ninguna tarea se solapa. Selecciona únicamente las tareas compatibles con esta.");allTasks.filter{it.id!=t.id&&it.status !in listOf(Status.DONE,Status.ARCHIVED)}.forEach{other->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(other.id in t.overlapWith,{v->t=t.copy(overlapWith=if(v)(t.overlapWith+other.id).distinct()else t.overlapWith-other.id)});Column{Text(other.title);if(t.id in other.overlapWith)Text("También autorizado desde la otra tarea",style=MaterialTheme.typography.bodySmall)}}}}}
        if(advanced)item{GlassCard{Text("Dependencias",style=MaterialTheme.typography.titleMedium);Text("No se planificará hasta completar sus requisitos.");allTasks.filter{it.id!=t.id&&it.status!=Status.ARCHIVED}.forEach{dep->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(dep.id in t.dependencies,{v->t=t.copy(dependencies=if(v)(t.dependencies+dep.id).distinct()else t.dependencies-dep.id)});Text(dep.title)}}}}
        item{TextButton(onClick={objectives=!objectives}){Text(if(objectives)"Ocultar objetivos" else "Añadir objetivos")}};if(objectives)item{GlassCard{Text("Checklist y objetivos",style=MaterialTheme.typography.titleMedium);t.checks.forEach{c->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(c.done,{v->t=t.copy(checks=t.checks.map{if(it.id==c.id)it.copy(done=v) else it})});Text(c.title,Modifier.weight(1f));TextButton(onClick={t=t.copy(checks=t.checks.filterNot{it.id==c.id})}){Text("Quitar")}}};Field(check,{check=it},"Nuevo objetivo");TextButton(enabled=check.isNotBlank(),onClick={t=t.copy(checks=t.checks+Check(title=check.trim()));check=""}){Text("Añadir objetivo")}}}
        item{TextButton(onClick={phasesOpen=!phasesOpen}){Text(if(phasesOpen)"Ocultar fases" else "Añadir fases")}};if(phasesOpen)item{GlassCard{Text("Fases",style=MaterialTheme.typography.titleMedium);t.phases.forEach{p->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(p.done,{v->t=t.copy(phases=t.phases.map{if(it.id==p.id)it.copy(done=v) else it})});Text(p.title,Modifier.weight(1f));TextButton(onClick={phaseEdit=p}){Text("Editar")};TextButton(onClick={t=t.copy(phases=t.phases.filterNot{it.id==p.id})}){Text("Quitar")}};Row(verticalAlignment=Alignment.CenterVertically){Text("${p.minutes} min",Modifier.weight(1f));TextButton(onClick={val a=t.phases.toMutableList();val i=a.indexOf(p);if(i>0){a.removeAt(i);a.add(i-1,p);t=t.copy(phases=a)}}){Text("↑")};TextButton(onClick={val a=t.phases.toMutableList();val i=a.indexOf(p);if(i<a.lastIndex){a.removeAt(i);a.add(i+1,p);t=t.copy(phases=a)}}){Text("↓")};TextButton(onClick={t=t.copy(phases=t.phases.map{if(it.id==p.id)it.copy(minutes=(it.minutes-15).coerceAtLeast(0))else it})}){Text("−15")};TextButton(onClick={t=t.copy(phases=t.phases.map{if(it.id==p.id)it.copy(minutes=(it.minutes+15).coerceAtMost(100000))else it})}){Text("+15")}}};Field(phase,{phase=it},"Nueva fase");TextButton(enabled=phase.isNotBlank(),onClick={t=t.copy(phases=t.phases+Phase(title=phase.trim()));phase=""}){Text("Añadir fase")}}}
        item{if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);Button(onClick={validated(save)},modifier=Modifier.fillMaxWidth()){Text("Guardar tarea")};OutlinedButton(enabled=TaskConstraints.unmet(t,allTasks).isEmpty(),onClick={validated(plan)},modifier=Modifier.fillMaxWidth()){Text("Guardar y planificar")};if(allTasks.any{it.id==initial.id})Row{TextButton(onClick={validated(duplicate)}){Text("Duplicar")};TextButton(onClick={remove=true}){Text("Eliminar tarea",color=MaterialTheme.colorScheme.error)}}}
    }}}
    phaseEdit?.let{p->PhaseDetail(p,{phaseEdit=null},{v->t=t.copy(phases=t.phases.map{if(it.id==v.id)v else it});phaseEdit=null})}
    if(remove)Confirm("Eliminar tarea","Se eliminarán también sus sesiones.",{remove=false},delete)
}
@Composable fun PlanDialog(task:Task,existing:List<Session>,close:()->Unit,accept:(Task,List<Session>,List<Session>)->Unit,external:List<Interval> = emptyList(),allTasks:List<Task> = emptyList(),uid:String="local",openAgenda:(Task)->Unit={}){
    val today=LocalDate.now()
    val capturedNow=remember(task.id){System.currentTimeMillis()}
    val previous=existing.filter{it.taskId==task.id&&!it.done&&it.start>capturedNow&&it.calendarTitle.isBlank()}
    val kept=existing.filterNot{it in previous}
    val doneMinutes=maxOf(task.actualMinutes,kept.filter{it.taskId==task.id&&!it.isBreak&&it.done}.sumOf{((it.end-it.start)/60000).toInt()})
    val otherReserved=kept.filter{it.taskId==task.id&&!it.isBreak&&!it.done}.sumOf{((it.end-it.start)/60000).toInt()}
    var hints by remember(task.id){mutableStateOf(task.planHints)}
    var start by remember(task.id){mutableStateOf(task.startDate.ifBlank{today.toString()})}
    var deadline by remember(task.id){mutableStateOf(task.due.ifBlank{today.plusDays(7).toString()})}
    var from by remember(task.id){mutableStateOf(hints.windowStart.ifBlank{"09:00"})}
    var until by remember(task.id){mutableStateOf(hints.windowEnd.ifBlank{"19:00"})}
    var max by remember(task.id){mutableStateOf((if(hints.sessionLimit==60&&hints.compact)120 else hints.sessionLimit).coerceIn(1,1440).toString())}
    var pause by remember(task.id){mutableStateOf(hints.breakMinutes.toString())}
    var compact by remember(task.id){mutableStateOf(hints.compact)}
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
    val draftTask=task.copy(minutes=total?:task.minutes,due=deadline,startDate=start,category=Category.DEADLINE,planHints=hints.copy(windowStart=from,windowEnd=until,sessionLimit=max.toIntOrNull()?:120,breakMinutes=pause.toIntOrNull()?:0,compact=compact,dailyMinutes=daily.toIntOrNull()?:0,availableWeekdays=days.map{it.value}.sorted(),daysPerWeek=days.size,excludedWeekdays=emptyList(),topicCount=count?:hints.topicCount))
    val result=remember(draftTask,kept,external,start,last,from,until,max,daily,pause,compact,remaining,days){
        if(last==null||remaining==null||remaining<=0)null
        else runCatching{
            require(max.toInt() in 1..1440&&pause.toInt() in 0..60&&daily.ifBlank{"0"}.toInt() in 0..1440&&days.isNotEmpty())
            require(LocalTime.parse(from)!=LocalTime.parse(until))
            val phaseBudgets=PhaseBudgets.remaining(draftTask,kept)
            AgendaPlanner.plan(listOf(AgendaInput(task.id,task.title,remaining,last,task.priority.ordinal,phaseBudgets)),maxOf(today,LocalDate.parse(start)),last,days,LocalTime.MIDNIGHT,LocalTime.MIDNIGHT,max.toInt(),external,existing=kept,tasks=allTasks.filterNot{it.id==task.id}+draftTask)
        }.getOrNull()
    }
    val missing=result?.deficits?.sumOf{it.minutes}?:0
    val weekdayLabels=listOf("Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo")
    val shortLabels=listOf("L","M","X","J","V","S","D")
    fun applyRevision(){
        val next=StudyIntent.reviseAvailability(revision,hints)
        val n=StudyIntent.topicsAnswer(revision)
        val window=PlanningPreferences.explicitWindow(revision)
        if(window!=null){from=window.first;until=window.second}
        if(next==null&&n==null&&window==null){feedback="Indica días, horario (por ejemplo, de 22:00 a 04:00) o número de temas.";return}
        if(next!=null){hints=next;days=StudyIntent.availableDays(next)}
        if(n!=null&&hints.minutesPerTopic>0)countText=n.toString()
        revision="";feedback="Propuesta recalculada. Revisa las sesiones."
    }
    val ctx=LocalContext.current;val coroutine=rememberCoroutineScope()
    var keepBreaks by remember{mutableStateOf(true)}
    var aiBusy by remember{mutableStateOf(false)};var aiConsent by remember{mutableStateOf(false)};var connect by remember{mutableStateOf(false)}
    fun reviseWithGemini(){
        val request=revision.trim()
        aiBusy=true
        coroutine.launch{
            try{
                val current=draftTask.copy(attachments=emptyList(),contact="",phone="")
                val response=es.planidroid.app.sync.AiConfig.provider(ctx,uid).planWithContext(current,request.ifBlank{"Optimiza bloques y descansos conservando mis preferencias"},AgendaContext.availability(kept,external,today,last?:today.plusDays(7))).tasks.singleOrNull()?:error("Gemini no devolvió una tarea")
                val merged=PlanningPreferences.merge(response.planHints,SmartCapture.hints(request,deadline,today),request)
                val window=PlanningPreferences.explicitWindow(request)
                if(window!=null||merged.windowStart.isNotBlank()){from=window?.first?:merged.windowStart;until=window?.second?:merged.windowEnd}
                if(merged.availableWeekdays.isNotEmpty()||merged.daysPerWeek>0){days=StudyIntent.availableDays(merged)}
                if(merged.topicCount>0)countText=merged.topicCount.toString()
                if(merged.dailyMinutes>0)daily=merged.dailyMinutes.toString()
                max=merged.sessionLimit.toString();pause=merged.breakMinutes.toString();compact=merged.compact
                hints=hints.copy(minutesPerTopic=merged.minutesPerTopic.takeIf{it>0}?:hints.minutesPerTopic,topicCount=merged.topicCount.takeIf{it>0}?:hints.topicCount,finishBeforeDeadline=hints.finishBeforeDeadline||merged.finishBeforeDeadline,breakReason=merged.breakReason.ifBlank{hints.breakReason})
                revision="";feedback="Preferencias revisadas. Comprueba el horario antes de guardar."
            }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){feedback=e.localizedMessage?:"Gemini no disponible"}finally{aiBusy=false}
        }
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
                        if(hints.breakReason.isNotBlank())Text(hints.breakReason,style=MaterialTheme.typography.bodySmall)
                        TextButton(enabled=!aiBusy,onClick={if(!es.planidroid.app.sync.AiConfig.configured(ctx,uid))connect=true else aiConsent=true}){Text(if(aiBusy)"Consultando…" else "Optimizar con Gemini")}
                        if(feedback.isNotBlank())Text(feedback,style=MaterialTheme.typography.bodySmall)
                        Text("Horario propuesto: "+from+" – "+until,style=MaterialTheme.typography.labelMedium)
                        TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Ocultar ajustes" else "Ajustar horario y duración")}
                        if(advanced){
                            DateField(start,{start=it},"Desde",false);DateField(deadline,{deadline=it},"Fecha límite",false)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Field(from,{from=it},"Desde HH:mm")};Column(Modifier.weight(1f)){Field(until,{until=it},"Hasta HH:mm")}}
                            Field(max,{max=it},"Máximo por bloque (min)");Field(daily,{daily=it},"Máximo por noche/día (opcional)")
                            Field(pause,{pause=it;hints=hints.copy(breakReason="")},"Pausa base (min, 0 sin pausas)")
                            Row(verticalAlignment=Alignment.CenterVertically){Switch(compact,{compact=it});Text("Concentrar bloques largos")}
                            Text("El horario puede cruzar medianoche. La madrugada pertenece a la noche anterior.",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item{
                    when{
                        hints.minutesPerTopic>0&&total==null->Text("Indica cuántos temas hay para generar una planificación real.",style=MaterialTheme.typography.bodyMedium)
                        days.isEmpty()->Text("Selecciona tus días de estudio.",color=MaterialTheme.colorScheme.error)
                        remaining==0->Text("Ya has reservado o completado todo el tiempo necesario.")
                        result==null->Text("Revisa la fecha, la duración y el horario.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        else->GlassCard(tint=if(missing==0)Mint else Peach){
                            Text(if(missing==0)"Tu planificación" else "Faltan "+missing+" min",style=MaterialTheme.typography.titleLarge)
                            Text(result.blocks.map{sessionDate(it.start)}.distinct().size.toString()+" días · "+result.blocks.sumOf{(it.end-it.start)/60000}+" min reservados")
                            if(hints.finishBeforeDeadline)Text("El día del examen queda libre.",style=MaterialTheme.typography.bodySmall)
                            if(missing>0){Text("No cabe todo en tus horas disponibles.",style=MaterialTheme.typography.bodySmall);Button(onClick={openAgenda(draftTask)},enabled=total!=null){Text("Buscar reorganización de agenda")}}
                            if(result.breaks.isNotEmpty()){Text(result.breaks.sumOf{(it.end-it.start)/60000}.toString()+" min de pausas · no cuentan como estudio",style=MaterialTheme.typography.bodySmall);Row(verticalAlignment=Alignment.CenterVertically){Checkbox(keepBreaks,{keepBreaks=it});Text("Reservar descansos en el calendario")}}
                        }
                    }
                }
                result?.blocks?.groupBy{sessionDate(it.start)}?.forEach{(day,blocks)->
                    item{
                        GlassCard(tint=Blue){
                            Text(day.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM",java.util.Locale("es","ES"))).replaceFirstChar{it.uppercase()},style=MaterialTheme.typography.titleMedium)
                            Text(blocks.sumOf{(it.end-it.start)/60000}.toString()+" min de estudio",style=MaterialTheme.typography.labelLarge)
                            val pauses=result?.breaks.orEmpty().filter{sessionDate(it.start)==day}
                            val timeline=blocks.map{Triple(it.start,it.end,draftTask.phases.find{p->p.id==it.phaseId}?.title?:"Estudio")}+pauses.map{Triple(it.start,it.end,"Descanso sugerido")}
                            timeline.sortedBy{it.first}.forEach{(a,b,label)->Text(time(a)+" – "+time(b)+" · "+label,style=MaterialTheme.typography.bodyMedium)}
                        }
                    }
                }
                item{
                    Button(enabled=!approved&&result?.blocks?.isNotEmpty()==true&&total!=null,onClick={
                        val r=result?:return@Button;val effort=total?:return@Button
                        approved=true
                        val updated=draftTask.copy(minutes=effort)
                        accept(updated,r.sessions(keepBreaks),previous)
                    },modifier=Modifier.fillMaxWidth()){Text(if(missing==0)if(previous.isEmpty())"Guardar tarea y agenda" else "Guardar nueva planificación" else "Aceptar plan parcial")}
                    TextButton(onClick=close){Text("Cancelar")}
                }
            }
        }
    }
    if(connect)PersonalGeminiDialog(uid){connect=false}
    if(aiConsent)Confirm("Revisar con Gemini","Se enviarán los datos necesarios de esta tarea y los intervalos ocupados para ajustar en una sola consulta fases, horarios y descansos.",{aiConsent=false},{reviseWithGemini()})
}
