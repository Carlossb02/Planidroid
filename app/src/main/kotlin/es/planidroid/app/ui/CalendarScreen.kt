package es.planidroid.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.planidroid.app.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class AgendaEvent(val id:String,val start:Long,val end:Long,val title:String,val session:Session?=null,val task:Task?=null,val external:ExternalEvent?=null)
private val spanish=Locale("es","ES")
private fun dayLabel(day:LocalDate)=day.format(DateTimeFormatter.ofPattern("EEE d MMM",spanish)).replaceFirstChar{it.uppercase()}
private fun monthLabel(day:LocalDate)=day.format(DateTimeFormatter.ofPattern("MMMM yyyy",spanish)).replaceFirstChar{it.uppercase()}

@Composable fun CalendarScreen(tasks:List<Task>,sessions:List<Session>,edit:(Task)->Unit,save:(Session)->Unit,delete:(Session)->Unit,plan:()->Unit,external:List<ExternalEvent> = emptyList(),addEvent:(LocalDate)->Unit = {}){
    var dateText by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
    val date=LocalDate.parse(dateText)
    var mode by rememberSaveable{mutableStateOf("Semana")}
    var selectedId by remember{mutableStateOf<String?>(null)}
    var selectedExternal by remember{mutableStateOf<ExternalEvent?>(null)}
    val selected=sessions.find{it.id==selectedId}
    val taskMap=remember(tasks){tasks.associateBy{it.id}}
    val events=remember(sessions,tasks,external){
        val local=sessions.filter{it.end>it.start&&taskMap.containsKey(it.taskId)}.map{s->val t=taskMap[s.taskId];AgendaEvent("s:"+s.id,s.start,s.end,if(s.isBreak)"Descanso" else s.calendarTitle.ifBlank{t?.title.orEmpty()},s,t)}
        local+external.filter{it.end>it.start}.map{AgendaEvent("e:"+it.id,it.start,it.end,it.title,external=it)}
    }
    val week=date.minusDays((date.dayOfWeek.value-1).toLong())
    val zone=ZoneId.systemDefault()
    val days=if(mode=="Semana")(0L..6L).map{week.plusDays(it)}else listOf(date)
    fun choose(d:LocalDate){dateText=d.toString();mode="Día";selectedId=null;selectedExternal=null}
    fun navigate(delta:Long){dateText=when(mode){"Semana"->date.plusWeeks(delta);"Mes"->date.plusMonths(delta);else->date.plusDays(delta)}.toString();selectedId=null}
    fun open(event:AgendaEvent){if(event.session!=null)selectedId=event.session.id else selectedExternal=event.external}
    Column(Modifier.fillMaxSize().padding(horizontal=12.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(monthLabel(date),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
            IconButton(onClick={navigate(-1)}){Icon(Icons.Default.ChevronLeft,"Anterior")}
            IconButton(onClick={navigate(1)}){Icon(Icons.Default.ChevronRight,"Siguiente")}
            TextButton(onClick={dateText=LocalDate.now().toString()}){Text("Hoy")}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically){
            listOf("Día","Semana","Mes","Agenda").forEach{v->FilterChip(selected=mode==v,onClick={mode=v},label={Text(v)},modifier=Modifier.weight(1f))}
        }
        Row(Modifier.fillMaxWidth().padding(bottom=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={addEvent(date)},modifier=Modifier.weight(1f)){Icon(Icons.Default.Add,null);Text(" Evento")}
            FilledTonalButton(onClick=plan,modifier=Modifier.weight(1f)){Icon(Icons.Default.AutoAwesome,null);Text(" Planificar")}
        }
        when(mode){
            "Mes"->Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){
                val first=date.withDayOfMonth(1);val offset=first.dayOfWeek.value-1
                Row{listOf("L","M","X","J","V","S","D").forEach{Text(it,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.labelMedium)}}
                repeat((offset+date.lengthOfMonth()+6)/7){w->
                    Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){repeat(7){c->
                        val n=w*7+c-offset+1
                        Box(Modifier.weight(1f).height(65.dp)){
                            if(n in 1..date.lengthOfMonth()){
                                val d=first.withDayOfMonth(n);val bounds=TimelineLayout.bounds(d,zone)
                                val visible=events.filter{it.start<bounds.end&&it.end>bounds.start}
                                Surface(Modifier.fillMaxSize().clickable{choose(d)},shape=RoundedCornerShape(10.dp),color=if(d==date)soft(Blue,.20f)else MaterialTheme.colorScheme.surface){
                                    Column(Modifier.padding(5.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
                                        Text(n.toString(),style=MaterialTheme.typography.labelLarge)
                                        visible.take(3).forEach{e->Box(Modifier.fillMaxWidth().height(5.dp).background(if(e.session?.isBreak==true)MaterialTheme.colorScheme.outline else if(e.external!=null)Lilac else taskColor(e.task),RoundedCornerShape(3.dp)))}
                                        if(visible.size>3)Text("+"+(visible.size-3),style=MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }}
                }
                Spacer(Modifier.height(12.dp));Text("Toca un día para abrir su horario.",style=MaterialTheme.typography.bodySmall)
            }
            "Agenda"->LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)){
                val first=TimelineLayout.bounds(date,zone).start
                val visible=events.filter{it.end>first}.sortedBy{it.start}
                if(visible.isEmpty())item{Text("No hay eventos próximos.",modifier=Modifier.padding(16.dp))}
                visible.groupBy{maxOf(date,sessionDate(it.start))}.forEach{(day,group)->
                    item{Text(dayLabel(day),style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=8.dp))}
                    items(group,key={it.id}){e->
                        Surface(Modifier.fillMaxWidth().clickable{open(e)},shape=RoundedCornerShape(14.dp),color=eventBackground(e)){
                            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(time(e.start),Modifier.width(55.dp));Column(Modifier.weight(1f)){Text(e.title,style=MaterialTheme.typography.titleMedium);Text(time(e.start)+" – "+time(e.end),style=MaterialTheme.typography.bodySmall)}}
                        }
                    }
                }
            }
            else->TimeGrid(days,date,events,Modifier.weight(1f),::choose,::open)
        }
    }
    selected?.let{s->SessionDetails(s,taskMap[s.taskId],sessions,tasks,external,{selectedId=null},{v->save(v);selectedId=null},{delete(s);selectedId=null},{t->selectedId=null;edit(t)})}
    selectedExternal?.let{e->AlertDialog(onDismissRequest={selectedExternal=null},title={Text(e.title)},text={Text(dayLabel(sessionDate(e.start))+" · "+time(e.start)+" – "+time(e.end)+"\nGoogle Calendar · solo lectura")},confirmButton={TextButton(onClick={selectedExternal=null}){Text("Cerrar")}})}
}
@Composable private fun eventBackground(e:AgendaEvent)=if(e.session?.isBreak==true)MaterialTheme.colorScheme.surfaceVariant else soft(if(e.external!=null)Lilac else taskColor(e.task),if(e.session?.done==true).10f else .25f)
@Composable private fun eventAccent(e:AgendaEvent)=if(e.session?.isBreak==true)MaterialTheme.colorScheme.outline else if(e.external!=null)Lilac else taskColor(e.task)

@Composable private fun TimeGrid(days:List<LocalDate>,selected:LocalDate,events:List<AgendaEvent>,modifier:Modifier,choose:(LocalDate)->Unit,open:(AgendaEvent)->Unit){
    val zone=ZoneId.systemDefault();val hourHeight=if(days.size==1)96.dp else 78.dp
    val vScroll=rememberScrollState();val hScroll=rememberScrollState();val density=LocalDensity.current
    val selectedBounds=TimelineLayout.bounds(selected,zone)
    val first=events.filter{it.start<selectedBounds.end&&it.end>selectedBounds.start}.minOfOrNull{maxOf(it.start,selectedBounds.start)}
    val initialHour=if(first==null)8 else (((first-selectedBounds.start)/3600000).toInt()-1).coerceIn(0,23)
    LaunchedEffect(days,selected){vScroll.scrollTo(with(density){(hourHeight*initialHour).roundToPx()});if(days.size>1)hScroll.scrollTo(with(density){(118.dp*days.indexOf(selected).coerceAtLeast(0)).roundToPx()})}
    val maxHours=days.maxOf{d->val bound=TimelineLayout.bounds(d,zone);((bound.end-bound.start)/3600000).toInt()}
    BoxWithConstraints(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)){
        val dayWidth=if(days.size==1)(maxWidth-48.dp).coerceAtLeast(80.dp)else 118.dp
        Column{
            Row(Modifier.fillMaxWidth()){
                Box(Modifier.width(48.dp).height(48.dp),contentAlignment=Alignment.Center){Text("Hora",style=MaterialTheme.typography.labelSmall)}
                Row(Modifier.weight(1f).horizontalScroll(hScroll)){
                    days.forEach{d->
                        Surface(Modifier.width(dayWidth).height(48.dp).clickable{choose(d)},color=if(d==selected)soft(Blue,.17f)else MaterialTheme.colorScheme.surface){
                            Box(contentAlignment=Alignment.Center){Text(dayLabel(d),style=MaterialTheme.typography.labelLarge)}
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll)){
                Box(Modifier.width(48.dp).height(hourHeight*maxHours)){
                    for(hour in 0 until maxHours){val label=selectedBounds.start+hour*3600000L;Text(time(label),Modifier.offset(y=hourHeight*hour).padding(start=5.dp,top=3.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
                Row(Modifier.weight(1f).horizontalScroll(hScroll)){
                    days.forEach{d->DayColumn(d,events,dayWidth,hourHeight,maxHours,open)}
                }
            }
        }
    }
}
@Composable private fun DayColumn(day:LocalDate,events:List<AgendaEvent>,width:Dp,hourHeight:Dp,maxHours:Int,open:(AgendaEvent)->Unit){
    val bounds=TimelineLayout.bounds(day,ZoneId.systemDefault())
    val placed=remember(events,bounds){TimelineLayout.place(events.map{TimelineSpan(it.id,it.start,it.end)},bounds)}
    val byId=remember(events){events.associateBy{it.id}}
    val hours=((bounds.end-bounds.start)/3600000).toInt()
    Box(Modifier.width(width).height(hourHeight*maxHours).border(.5.dp,MaterialTheme.colorScheme.outlineVariant)){
        for(hour in 0 until hours)Box(Modifier.offset(y=hourHeight*hour).fillMaxWidth().height(.6.dp).background(MaterialTheme.colorScheme.outlineVariant))
        placed.forEach{p->key(p.id){
            val e=byId.getValue(p.id)
            val y=hourHeight*((p.start-bounds.start)/3600000f)
            val height=hourHeight*((p.end-p.start)/3600000f)
            val lane=width/p.lanes
            val accent=eventAccent(e);val bg=eventBackground(e)
            Surface(Modifier.offset(x=lane*p.lane,y=y).width(lane).height(height).padding(horizontal=2.dp).clip(RoundedCornerShape(7.dp)).clickable{open(e)}.semantics{contentDescription=e.title+", "+time(p.start)+" a "+time(p.end)},shape=RoundedCornerShape(7.dp),color=bg){
                Row(Modifier.fillMaxSize()){
                    Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
                    Column(Modifier.weight(1f).padding(horizontal=4.dp,vertical=if(height>=28.dp)4.dp else 0.dp)){
                        Text(e.title,maxLines=if(height>=70.dp)2 else 1,overflow=TextOverflow.Ellipsis,fontSize=if(height<22.dp)9.sp else 12.sp,lineHeight=if(height<22.dp)10.sp else 14.sp)
                        if(height>=40.dp)Text(time(p.start)+"–"+time(p.end),fontSize=10.sp,maxLines=1)
                        if(height>=80.dp)e.task?.phases?.find{it.id==e.session?.phaseId}?.let{Text(it.title,fontSize=10.sp,maxLines=2,overflow=TextOverflow.Ellipsis)}
                    }
                }
            }
        }}
        if(day==LocalDate.now()){val elapsed=System.currentTimeMillis()-bounds.start;if(elapsed in 0 until (bounds.end-bounds.start))Box(Modifier.offset(y=hourHeight*(elapsed/3600000f)).fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.error))}
    }
}
@Composable private fun SessionDetails(s:Session,task:Task?,sessions:List<Session>,tasks:List<Task>,external:List<ExternalEvent>,close:()->Unit,save:(Session)->Unit,delete:()->Unit,edit:(Task)->Unit){
    var date by remember(s.id){mutableStateOf(sessionDate(s.start).toString())}
    var startText by remember(s.id){mutableStateOf(time(s.start))}
    var duration by remember(s.id){mutableStateOf(((s.end-s.start)/60000).toString())}
    var error by remember(s.id){mutableStateOf("")};var confirmDelete by remember(s.id){mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(if(s.isBreak)"Descanso" else task?.title?:"Evento")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        DateField(date,{date=it},"Fecha",false);Field(startText,{startText=it},"Inicio HH:mm");Field(duration,{duration=it},"Duración (min)")
        if(s.isBreak)Text(task?.planHints?.breakReason?.takeIf{it.isNotBlank()}?:"Pausa reservada",style=MaterialTheme.typography.bodySmall)
        else{Row(verticalAlignment=Alignment.CenterVertically){Checkbox(s.done,{save(s.copy(done=it))});Text("Completada")};if(task!=null)TextButton(onClick={edit(task)}){Text("Abrir tarea")}}
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        TextButton(onClick={confirmDelete=true}){Text(if(s.isBreak)"Eliminar descanso" else "Eliminar sesión",color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={
        runCatching{
            val a=LocalDate.parse(date).atTime(LocalTime.parse(startText)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val n=duration.toInt();require(n in 1..1440){"Duración inválida"}
            val v=s.copy(start=a,end=a+n*60000L)
            require(sessions.none{OverlapPolicy.conflict(v,it,tasks)}&&external.none{it.busy&&it.start<v.end&&it.end>v.start}){"Ese horario está ocupado"}
            v
        }.onSuccess(save).onFailure{error=it.message?:"Datos inválidos"}
    }){Text("Guardar")}},dismissButton={TextButton(onClick=close){Text("Cerrar")}})
    if(confirmDelete)Confirm("Eliminar bloque","Se quitará del calendario.",{confirmDelete=false},delete)
}
