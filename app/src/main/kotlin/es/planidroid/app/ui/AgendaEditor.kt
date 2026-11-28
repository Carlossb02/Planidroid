package es.planidroid.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*
import java.time.*
import kotlinx.coroutines.launch

@Composable fun AgendaEditor(tasks:List<Task>,sessions:List<Session>,close:()->Unit,accept:(List<Session>,List<Session>,List<Task>,(String?)->Unit)->Unit,external:List<Interval> = emptyList(),uid:String="local",draft:Task?=null){
    val preferences=LocalViewPreferences.current
    val today=LocalDate.now()
    val now=remember{System.currentTimeMillis()}
    val eligible=tasks.filter{it.status !in listOf(Status.DONE,Status.ARCHIVED,Status.BLOCKED)&&TaskConstraints.unmet(it,tasks).isEmpty()}
    fun effort(t:Task)=t.minutes.takeIf{it>0}?:t.phases.sumOf{it.minutes}
    var picked by remember{mutableStateOf(eligible.filter{if(draft!=null)it.id==draft.id else (effort(it)>0&&TaskConstraints.remaining(it,sessions,now)>0&&it.due.ifBlank{today.toString()}>=today.toString())}.map{it.id}.toSet())}
    var estimates by remember{mutableStateOf<Map<String,String>>(emptyMap())}
    var topics by remember{mutableStateOf<Map<String,String>>(emptyMap())}
    var expanded by remember{mutableStateOf<String?>(null)}
    var permissions by remember{mutableStateOf<Map<String,List<String>>>(emptyMap())}
    var end by remember{mutableStateOf(maxOf(today.plusDays(7),eligible.mapNotNull{runCatching{LocalDate.parse(it.due)}.getOrNull()}.maxOrNull()?:today).coerceAtMost(today.plusDays(366)).toString())}
    var from by remember{mutableStateOf("00:00")};var until by remember{mutableStateOf("00:00")}
    var max by remember{mutableStateOf("180")};var margin by remember{mutableStateOf("0")}
    var exceptions by remember{mutableStateOf("")};var advanced by remember{mutableStateOf(false)}
    var aiOpen by remember{mutableStateOf(false)}
    var includeToday by remember{mutableStateOf(true)}
    var replan by remember{mutableStateOf(draft!=null)};var days by remember{mutableStateOf((if(preferences.workdaysOnly)DayOfWeek.entries.take(5) else DayOfWeek.entries).toSet())}
    var proposal by remember{mutableStateOf<AgendaProposal?>(null)};var replacing by remember{mutableStateOf(emptyList<Session>())}
    var approvedUpdates by remember{mutableStateOf(emptyList<Task>())}
    var error by remember{mutableStateOf("")};var submitted by remember{mutableStateOf(false)}
    val ctx=androidx.compose.ui.platform.LocalContext.current;val coroutine=rememberCoroutineScope()
    var aiBusy by remember{mutableStateOf(false)};var aiConsent by remember{mutableStateOf(false)};var connect by remember{mutableStateOf(false)}
    var keepBreaks by remember{mutableStateOf(preferences.reserveBreaks)};var restPolicies by remember{mutableStateOf(emptyMap<String,Triple<Int,Int,String>>())}
    var advice by remember{mutableStateOf("")};var suggested by remember{mutableStateOf(emptySet<String>())}
    var request by remember{mutableStateOf("")}
    fun askGemini(){aiBusy=true;advice="";suggested=emptySet();restPolicies=emptyMap();coroutine.launch{try{
        val payload=AgendaContext.build(eligible,sessions,external,today,runCatching{LocalDate.parse(end)}.getOrDefault(today.plusDays(7)),picked)+"\nPetición: "+request.take(400)
        val response=es.planidroid.app.sync.AiConfig.provider(ctx,uid).agendaAdvice(payload)
        advice=response.optString("answer").take(3000)
        val policies=response.optJSONArray("restPolicies");restPolicies=if(policies==null)emptyMap() else (0 until minOf(policies.length(),40)).mapNotNull{i->val p=policies.optJSONObject(i)?:return@mapNotNull null;val id=p.optString("taskId");val focus=p.optInt("focusMinutes",-1);val rest=p.optInt("breakMinutes",-1);if(eligible.none{it.id==id}||focus !in 15..240||rest !in 0..60)null else id to Triple(focus,rest,p.optString("reason").take(500))}.toMap()
        proposal=null;submitted=false
        val ids=response.optJSONArray("taskIds");suggested=if(ids==null)emptySet()else (0 until ids.length()).map{ids.optString(it)}.filter{id->eligible.any{it.id==id}&&sessions.any{it.taskId==id&&!it.done&&it.start>now&&it.calendarTitle.isBlank()}}.toSet()
    }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){advice=e.localizedMessage?:"Gemini no disponible"}finally{aiBusy=false}}}
    fun reset(){proposal=null;error="";replacing=emptyList();approvedUpdates=emptyList();submitted=false}
    fun candidate(t:Task):Task{
        val policy=restPolicies[t.id]
        val h=if(policy==null)t.planHints else t.planHints.copy(sessionLimit=policy.first,breakMinutes=policy.second,breakReason=policy.third)
        val topicText=topics[t.id]
        val count=topicText?.let{StudyIntent.topicsAnswer(it)}?:h.topicCount.takeIf{topicText==null&&it>0}
        val n=if(h.minutesPerTopic>0&&count!=null)runCatching{StudyIntent.studyMinutes(h,count)}.getOrDefault(0)
            else if(h.minutesPerTopic>0)0
            else estimates[t.id]?.toIntOrNull()?.takeIf{it in 1..100000}?:if(t.id in estimates)0 else effort(t).takeIf{it>0}?:h.suggestedMinutes
        return t.copy(minutes=n,planHints=h.copy(topicCount=count?:0,sessionLimit=if(h.sessionLimit==60&&h.compact)120 else h.sessionLimit),overlapWith=permissions[t.id]?:t.overlapWith)
    }
    val replacements=if(replan)sessions.filter{it.taskId in picked&&!it.done&&it.start>now&&it.calendarTitle.isBlank()}else emptyList()
    val kept=sessions.filterNot{it in replacements}
    val listState=rememberLazyListState()
    val preview=proposal
    LaunchedEffect(preview){if(preview!=null)listState.scrollToItem(0)}
    LaunchedEffect(tasks,sessions,external){if(!submitted)reset()}
    ScreenDialog({if(!submitted)close()}){
        Surface(Modifier.fillMaxSize()){
            Column(Modifier.fillMaxSize().screenInsets().padding(horizontal=4.dp)){
            LazyColumn(Modifier.weight(1f),state=listState,contentPadding=PaddingValues(start=16.dp,end=16.dp,top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(preview==null){
                item{
                    Section("Planificar tareas")
                    TextButton(onClick={aiOpen=!aiOpen}){Text(if(aiOpen)"Ocultar Gemini" else "✦ Optimizar con Gemini")}
                    if(aiOpen){Field(request,{request=it.take(1200)},"¿Qué quieres ajustar?",false)
                    OutlinedButton(enabled=!aiBusy,onClick={if(!es.planidroid.app.sync.AiConfig.configured(ctx,uid))connect=true else aiConsent=true},modifier=Modifier.fillMaxWidth()){Text(if(aiBusy)"Analizando calendario…" else "Consultar")}}
                    if(advice.isNotBlank())GlassCard(tint=Lilac){Text(advice);restPolicies.forEach{(id,p)->Text((eligible.find{it.id==id}?.title?:"Tarea")+": "+p.first+" min de trabajo / "+p.second+" min de pausa · "+p.third,style=MaterialTheme.typography.bodySmall)};suggested.forEach{id->Text("Revisar: "+eligible.find{it.id==id}?.title.orEmpty())};if(suggested.isNotEmpty())TextButton(onClick={picked=picked+suggested;replan=true;reset()}){Text("Seleccionar tareas sugeridas")}}
                    TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Ocultar opciones" else "Disponibilidad y opciones")}
                    if(advanced){
                        Row(verticalAlignment=Alignment.CenterVertically){Switch(replan,{replan=it;reset()});Text("Reorganizar sesiones futuras",Modifier.padding(start=8.dp))}
                        Row(verticalAlignment=Alignment.CenterVertically){Switch(includeToday,{includeToday=it;reset()});Text("Empezar hoy",Modifier.padding(start=8.dp))}
                        DateField(end,{end=it;reset()},"Último día",false)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Field(from,{from=it;reset()},"Desde HH:mm")};Column(Modifier.weight(1f)){Field(until,{until=it;reset()},"Hasta HH:mm")}}
                        Field(max,{max=it;reset()},"Máximo por sesión (min)")
                        Field(margin,{margin=it;reset()},"Margen antes de fecha límite (días)")
                        Field(exceptions,{exceptions=it;reset()},"Días excluidos AAAA-MM-DD, separados por coma")
                        DayOfWeek.entries.chunked(4).forEach{group->Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){group.forEach{d->FilterChip(d in days,{days=if(d in days)days-d else days+d;reset()},label={Text(listOf("L","M","X","J","V","S","D")[d.value-1])})}}}
                    }else Text("Hasta "+end+" · "+if(from=="00:00"&&until=="00:00")"horario de cada tarea" else from+" – "+until,style=MaterialTheme.typography.bodySmall)
                    if(eligible.isEmpty())Text("No hay tareas pendientes disponibles.")
                }
                items(eligible,key={it.id}){t->
                    val draft=candidate(t)
                    val remaining=TaskConstraints.remaining(draft,kept,now)
                    val hasFuture=sessions.any{it.taskId==t.id&&!it.done&&it.start>now}
                    Column(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
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
                                Text("Los eventos del calendario mantienen su horario.",style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item{
                    Button(enabled=picked.isNotEmpty()&&!aiBusy&&!submitted,modifier=Modifier.fillMaxWidth(),onClick={
                        runCatching{
                            val selected=eligible.filter{it.id in picked}.map(::candidate)
                            require(selected.all{it.minutes in 1..100000}){"Indica una duración o el número de temas de cada tarea seleccionada."}
                            val last=runCatching{LocalDate.parse(end)}.getOrNull() ?: error("Elige un último día válido.")
                            val first=if(includeToday)today else today.plusDays(1)
                            require(last in first..first.plusDays(366)){"El último día debe estar dentro del próximo año."}
                            val buffer=margin.toLongOrNull()?.takeIf{it in 0..30} ?: error("Indica un margen de 0 a 30 días.")
                            val sessionLimit=max.toIntOrNull()?.takeIf{it in 1..1440} ?: error("Indica un máximo por sesión de 1 a 1440 minutos.")
                            require(days.isNotEmpty()){"Selecciona al menos un día disponible."}
                            val startTime=runCatching{LocalTime.parse(from)}.getOrNull() ?: error("Revisa la hora de inicio (HH:mm).")
                            val endTime=runCatching{LocalTime.parse(until)}.getOrNull() ?: error("Revisa la hora de fin (HH:mm).")
                            require(startTime!=endTime||startTime==LocalTime.MIDNIGHT){"Usa dos horas distintas o 00:00 en ambos campos para respetar el horario por tarea."}
                            val zone=ZoneId.systemDefault()
                            val excluded=exceptions.split(',').map{it.trim()}.filter{it.isNotBlank()}.map{LocalDate.parse(it)}.map{Interval(it.atStartOfDay(zone).toInstant().toEpochMilli(),it.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())}
                            val updates=tasks.map{t->selected.find{it.id==t.id}?:t.copy(overlapWith=permissions[t.id]?:t.overlapWith)}
                            val inputs=selected.map{t->
                                val phaseBudgets=t.phases.filterNot{it.done}.map{p->p.id to (p.minutes-maxOf(p.actualMinutes,kept.filter{s->s.taskId==t.id&&!s.isBreak&&s.phaseId==p.id&&s.done}.sumOf{s->((s.end-s.start)/60000).toInt()})-kept.filter{s->s.taskId==t.id&&!s.isBreak&&s.phaseId==p.id&&!s.done&&s.end>now}.sumOf{s->((s.end-s.start)/60000).toInt()}).coerceAtLeast(0)}
                                AgendaInput(t.id,t.title,TaskConstraints.remaining(t,kept,now),t.due.takeIf{it.isNotBlank()}?.let{LocalDate.parse(it).minusDays(buffer)}?:last,t.priority.ordinal,phaseBudgets,t.startDate.takeIf{it.isNotBlank()}?.let{LocalDate.parse(it)})
                            }
                            val p=AgendaPlanner.plan(inputs,first,last,days,startTime,endTime,sessionLimit,external+excluded,zone,minStart=System.currentTimeMillis()+30_000L,existing=kept,tasks=updates)
                            require(replacements.none{old->p.deficits.any{it.taskId==old.taskId}}){"Una tarea existente perdería tiempo reservado. Amplía el horario o desmarca esa tarea."}
                            Triple(p,replacements,updates.filter{u->tasks.find{it.id==u.id}!=u})
                        }.onSuccess{(p,old,updates)->proposal=p;replacing=old;approvedUpdates=updates;error=if(p.blocks.isEmpty()&&p.deficits.isEmpty())"No hay sesiones nuevas que reservar." else ""}
                            .onFailure{error=it.message?:"Revisa la duración, fechas y disponibilidad.";proposal=null}
                    }){Text("Calcular propuesta")}
                }
                }
                preview?.let{p->
                    item{GlassCard(tint=if(p.deficits.isEmpty())Mint else Peach){
                        Text(if(p.deficits.isEmpty())"Plan viable" else "No cabe todo",style=MaterialTheme.typography.headlineSmall)
                        Text(p.blocks.size.toString()+" sesiones · "+p.blocks.sumOf{(it.end-it.start)/60000}+" min")
                        if(replacing.isNotEmpty()){Text("Se sustituirán "+replacing.size+" sesiones futuras. Las demás se conservan.");replacing.groupBy{it.taskId}.forEach{(id,old)->Text((tasks.find{it.id==id}?.title?:"Tarea")+": "+old.size+" sesiones anteriores → "+p.blocks.count{it.taskId==id}+" nuevas",style=MaterialTheme.typography.bodySmall)}}
                        if(p.breaks.isNotEmpty()){Text(p.breaks.sumOf{(it.end-it.start)/60000}.toString()+" min de descansos sugeridos");Row(verticalAlignment=Alignment.CenterVertically){Checkbox(keepBreaks,{keepBreaks=it});Text("Reservar descansos en el calendario")}}
                        p.deficits.forEach{Text(it.title+": faltan "+it.minutes+" min")}
                    }}
                    val approved=tasks.map{t->approvedUpdates.find{it.id==t.id}?:t}
                    p.blocks.groupBy{sessionDate(it.start)}.forEach { (day,blocks) ->
                        item { Text(day.toString(),Modifier.padding(top=8.dp),style=MaterialTheme.typography.titleMedium) }
                        items(blocks){block->
                            val task=approved.find{it.id==block.taskId}
                            Row(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                Text(time(block.start)+" – "+time(block.end),style=MaterialTheme.typography.labelLarge)
                                Column(Modifier.weight(1f)){
                                    Text(task?.title.orEmpty(),style=MaterialTheme.typography.titleSmall)
                                    task?.phases?.find{it.id==block.phaseId}?.let{Text(it.title,style=MaterialTheme.typography.bodySmall)}
                                    if(p.blocks.any{other->other.taskId!=block.taskId&&other.start<block.end&&block.start<other.end&&OverlapPolicy.allowed(block.taskId,other.taskId,approved)}||
                                        kept.any{other->other.start<block.end&&block.start<other.end&&OverlapPolicy.allowed(block.taskId,other.taskId,approved)})
                                        Text("Solapamiento autorizado",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if(p.breaks.isNotEmpty())item{
                        var open by remember{mutableStateOf(false)}
                        TextButton(onClick={open=!open}){Text(if(open)"Ocultar horas de descanso" else "Ver horas de descanso")}
                        if(open)p.breaks.take(50).forEach{Text(sessionDate(it.start).toString()+" · "+time(it.start)+" – "+time(it.end))}
                    }

                }
                item {
                    Column(Modifier.fillMaxWidth()){
                        if(error.isNotBlank())Text(error,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                        ActionFlow{
                            TextButton(enabled=!submitted,onClick={if(preview==null)close()else reset()}){
                                Text(if(preview==null)"Cerrar" else "Ajustar")
                            }
                            if(submitted)CircularProgressIndicator(Modifier.size(24.dp))
                            if(preview!=null)Button(enabled=preview.blocks.isNotEmpty()&&!aiBusy&&!submitted,onClick={
                                if(preview.blocks.any{it.start<=System.currentTimeMillis()+5_000L}){
                                    reset()
                                    error="El horario ha avanzado. Calcula de nuevo."
                                }else{
                                    submitted=true
                                    accept(preview.sessions(keepBreaks),replacing,approvedUpdates){failure->
                                        submitted=false
                                        if(failure!=null)error=failure
                                    }
                                }
                            }){Text(if(preview.deficits.isEmpty())"Guardar plan" else "Guardar plan parcial")}
                        }
                    }
                }
            }
            }
        }
    }
    if(connect)PersonalGeminiDialog(uid){connect=false}
    if(aiConsent)Confirm("Analizar agenda con Gemini","Se enviarán títulos, prioridades, fechas y horarios de tareas; de las citas externas solo se enviarán intervalos ocupados. No se modificará nada sin revisar la propuesta.",{aiConsent=false},{askGemini()})
}
