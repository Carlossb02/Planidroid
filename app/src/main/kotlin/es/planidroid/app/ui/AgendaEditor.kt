package es.planidroid.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import es.planidroid.app.domain.*
import java.time.*

@Composable fun AgendaEditor(tasks:List<Task>,sessions:List<Session>,close:()->Unit,accept:(List<Session>,List<Session>,List<Task>)->Unit,external:List<Interval> = emptyList()){
    val today=LocalDate.now()
    val now=remember{System.currentTimeMillis()}
    val eligible=tasks.filter{it.status !in listOf(Status.DONE,Status.ARCHIVED,Status.BLOCKED)&&TaskConstraints.unmet(it,tasks).isEmpty()}
    fun effort(t:Task)=t.minutes.takeIf{it>0}?:t.phases.sumOf{it.minutes}
    var picked by remember{mutableStateOf(eligible.filter{effort(it)==0||TaskConstraints.remaining(it,sessions)>0}.map{it.id}.toSet())}
    var estimates by remember{mutableStateOf<Map<String,String>>(emptyMap())}
    var topics by remember{mutableStateOf<Map<String,String>>(emptyMap())}
    var expanded by remember{mutableStateOf<String?>(null)}
    var permissions by remember{mutableStateOf<Map<String,List<String>>>(emptyMap())}
    var end by remember{mutableStateOf(maxOf(today.plusDays(7),eligible.mapNotNull{runCatching{LocalDate.parse(it.due)}.getOrNull()}.maxOrNull()?:today).coerceAtMost(today.plusDays(366)).toString())}
    var from by remember{mutableStateOf("09:00")};var until by remember{mutableStateOf("19:00")}
    var max by remember{mutableStateOf("60")};var margin by remember{mutableStateOf("0")}
    var exceptions by remember{mutableStateOf("")};var advanced by remember{mutableStateOf(false)}
    var replan by remember{mutableStateOf(false)};var days by remember{mutableStateOf(DayOfWeek.entries.toSet())}
    var proposal by remember{mutableStateOf<AgendaProposal?>(null)};var replacing by remember{mutableStateOf(emptyList<Session>())}
    var approvedUpdates by remember{mutableStateOf(emptyList<Task>())}
    var error by remember{mutableStateOf("")};var submitted by remember{mutableStateOf(false)}
    fun reset(){proposal=null;error="";replacing=emptyList();approvedUpdates=emptyList();submitted=false}
    fun candidate(t:Task):Task{
        val h=t.planHints
        val topicText=topics[t.id]
        val count=topicText?.let{StudyIntent.topicsAnswer(it)}?:h.topicCount.takeIf{topicText==null&&it>0}
        val n=if(h.minutesPerTopic>0&&count!=null)runCatching{StudyIntent.studyMinutes(h,count)}.getOrDefault(0)
            else if(h.minutesPerTopic>0)0
            else estimates[t.id]?.toIntOrNull()?.takeIf{it in 1..100000}?:if(t.id in estimates)0 else effort(t).takeIf{it>0}?:h.suggestedMinutes
        return t.copy(minutes=n,planHints=h.copy(topicCount=count?:0),overlapWith=permissions[t.id]?:t.overlapWith)
    }
    val replacements=if(replan)sessions.filter{it.taskId in picked&&!it.done&&it.start>now}else emptyList()
    val kept=sessions.filterNot{it in replacements}
    LaunchedEffect(tasks,sessions,external){reset()}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize()){
            LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                item{
                    Section("Planificar mi agenda","Selecciona tus tareas. Las sesiones no se solapan salvo permiso expreso.")
                    Text("Las tareas sin duración también se pueden añadir.",style=MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Switch(replan,{replan=it;reset()});Text("Reorganizar sesiones futuras",Modifier.padding(start=8.dp))
                    }
                    TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Ocultar disponibilidad" else "Ajustar disponibilidad")}
                    if(advanced){
                        DateField(end,{end=it;reset()},"Último día",false)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Field(from,{from=it;reset()},"Desde HH:mm")};Column(Modifier.weight(1f)){Field(until,{until=it;reset()},"Hasta HH:mm")}}
                        Field(max,{max=it;reset()},"Máximo por sesión (min)")
                        Field(margin,{margin=it;reset()},"Margen antes de fecha límite (días)")
                        Field(exceptions,{exceptions=it;reset()},"Días excluidos AAAA-MM-DD, separados por coma")
                        DayOfWeek.entries.chunked(4).forEach{group->Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){group.forEach{d->FilterChip(d in days,{days=if(d in days)days-d else days+d;reset()},label={Text(listOf("L","M","X","J","V","S","D")[d.value-1])})}}}
                    }else Text("Hasta "+end+" · "+from+" – "+until,style=MaterialTheme.typography.bodySmall)
                    if(eligible.isEmpty())Text("No hay tareas pendientes disponibles.")
                }
                items(eligible,key={it.id}){t->
                    val draft=candidate(t)
                    val remaining=TaskConstraints.remaining(draft,kept)
                    val hasFuture=sessions.any{it.taskId==t.id&&!it.done&&it.start>now}
                    GlassCard(tint=categoryColor(t.category)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Checkbox(t.id in picked,{v->picked=if(v)picked+t.id else picked-t.id;reset()})
                            Column(Modifier.weight(1f)){
                                Text(t.title,style=MaterialTheme.typography.titleMedium)
                                Text(when{draft.minutes==0->"Falta duración";remaining>0->remaining.toString()+" min por reservar";hasFuture->"Ya planificada · puedes reorganizarla";else->"Tiempo ya reservado o realizado"},style=MaterialTheme.typography.bodySmall)
                            }
                        }
                        if(t.id in picked){
                            if(t.planHints.minutesPerTopic>0){
                                Text((t.planHints.minutesPerTopic/60.0).toString()+" h por tema",style=MaterialTheme.typography.bodySmall)
                                Field(topics[t.id]?:t.planHints.topicCount.takeIf{it>0}?.toString().orEmpty(),{topics=topics+(t.id to it);reset()},"Número de temas")
                            }else if(effort(t)==0||t.id in estimates||expanded==t.id){
                                Field(estimates[t.id]?:draft.minutes.takeIf{it>0}?.toString().orEmpty(),{estimates=estimates+(t.id to it);reset()},"Duración total (min)")
                                if(effort(t)==0&&t.planHints.suggestedMinutes>0)Text("Estimación orientativa: revisa antes de aceptar.",style=MaterialTheme.typography.bodySmall)
                            }
                            if(hasFuture&&!replan&&remaining==0)TextButton(onClick={replan=true;reset()}){Text("Reorganizar")}
                            TextButton(onClick={expanded=if(expanded==t.id)null else t.id}){Text(if(expanded==t.id)"Ocultar opciones" else "Opciones de tarea")}
                            if(expanded==t.id){
                                Text("Permitir solapamiento solo con:",style=MaterialTheme.typography.labelLarge)
                                eligible.filter{it.id!=t.id}.forEach{other->
                                    val otherIds=permissions[other.id]?:other.overlapWith
                                    val ownIds=permissions[t.id]?:t.overlapWith
                                    val checked=other.id in ownIds||t.id in otherIds
                                    Row(verticalAlignment=Alignment.CenterVertically){
                                        Checkbox(checked,{allow->
                                            permissions=permissions+(t.id to (if(allow)(ownIds+other.id).distinct()else ownIds-other.id))
                                            if(!allow)permissions=permissions+(other.id to (otherIds-t.id))
                                            reset()
                                        })
                                        Text(other.title)
                                    }
                                }
                                Text("Las citas de Google Calendar continúan bloqueando el horario.",style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item{
                    Button(enabled=picked.isNotEmpty()&&!submitted,modifier=Modifier.fillMaxWidth(),onClick={
                        runCatching{
                            val selected=eligible.filter{it.id in picked}.map(::candidate)
                            require(selected.all{it.minutes in 1..100000}){"Indica una duración o el número de temas de cada tarea seleccionada."}
                            val last=LocalDate.parse(end);val buffer=margin.toLong();require(buffer in 0..30)
                            val zone=ZoneId.systemDefault()
                            val excluded=exceptions.split(',').map{it.trim()}.filter{it.isNotBlank()}.map{LocalDate.parse(it)}.map{Interval(it.atStartOfDay(zone).toInstant().toEpochMilli(),it.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())}
                            val updates=tasks.map{t->selected.find{it.id==t.id}?:t.copy(overlapWith=permissions[t.id]?:t.overlapWith)}
                            val inputs=selected.map{t->
                                val phaseBudgets=t.phases.filterNot{it.done}.map{p->p.id to (p.minutes-maxOf(p.actualMinutes,kept.filter{s->s.taskId==t.id&&s.phaseId==p.id&&s.done}.sumOf{s->((s.end-s.start)/60000).toInt()})-kept.filter{s->s.taskId==t.id&&s.phaseId==p.id&&!s.done}.sumOf{s->((s.end-s.start)/60000).toInt()}).coerceAtLeast(0)}
                                AgendaInput(t.id,t.title,TaskConstraints.remaining(t,kept),t.due.takeIf{it.isNotBlank()}?.let{LocalDate.parse(it).minusDays(buffer)}?:last,t.priority.ordinal,phaseBudgets,t.startDate.takeIf{it.isNotBlank()}?.let{LocalDate.parse(it)})
                            }
                            val p=AgendaPlanner.plan(inputs,today,last,days,LocalTime.parse(from),LocalTime.parse(until),max.toInt(),external+excluded,zone,existing=kept,tasks=updates)
                            Triple(p,replacements,updates.filter{u->tasks.find{it.id==u.id}!=u})
                        }.onSuccess{(p,old,updates)->proposal=p;replacing=old;approvedUpdates=updates;error=if(p.blocks.isEmpty())"No hay sesiones nuevas que reservar. Revisa el tiempo y los días disponibles." else ""}
                            .onFailure{error=it.message?:"Revisa la duración, fechas y disponibilidad.";proposal=null}
                    }){Text("Calcular propuesta")}
                    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
                }
                proposal?.let{p->
                    item{GlassCard(tint=if(p.deficits.isEmpty())Mint else Peach){
                        Text(if(p.deficits.isEmpty())"Plan viable" else "No cabe todo",style=MaterialTheme.typography.headlineSmall)
                        Text(p.blocks.size.toString()+" sesiones · "+p.blocks.sumOf{(it.end-it.start)/60000}+" min")
                        if(replacing.isNotEmpty())Text("Se sustituirán "+replacing.size+" sesiones futuras. Las demás se conservan.")
                        p.deficits.forEach{Text(it.title+": faltan "+it.minutes+" min")}
                    }}
                    items(p.blocks){b->GlassCard{
                        Text(sessionDate(b.start).toString()+" · "+time(b.start)+" – "+time(b.end),style=MaterialTheme.typography.labelLarge)
                        Text(tasks.find{it.id==b.taskId}?.title.orEmpty(),style=MaterialTheme.typography.titleMedium)
                        tasks.find{it.id==b.taskId}?.phases?.find{it.id==b.phaseId}?.let{Text(it.title)}
                        val approved=tasks.map{t->approvedUpdates.find{it.id==t.id}?:t}
                        if(p.blocks.any{other->other.taskId!=b.taskId&&other.start<b.end&&b.start<other.end&&OverlapPolicy.allowed(b.taskId,other.taskId,approved)}||kept.any{other->other.start<b.end&&b.start<other.end&&OverlapPolicy.allowed(b.taskId,other.taskId,approved)})Text("Solapamiento autorizado",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall)
                    }}
                    item{Button(enabled=p.blocks.isNotEmpty()&&!submitted,modifier=Modifier.fillMaxWidth(),onClick={submitted=true;accept(p.blocks.map{Session(taskId=it.taskId,phaseId=it.phaseId,start=it.start,end=it.end)},replacing,approvedUpdates)}){Text(if(p.deficits.isEmpty())"Aceptar planificación" else "Aceptar plan parcial")}}
                }
                item{TextButton(onClick=close){Text("Cancelar")}}
            }
        }
    }
}
