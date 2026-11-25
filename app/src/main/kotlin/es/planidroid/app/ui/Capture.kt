package es.planidroid.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*
import es.planidroid.app.sync.AiConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One entry point: text, preview, save. No attachment or camera controls. */
@Composable fun Capture(uid:String, save:(List<Task>)->Unit, plan:(Task)->Unit, manual:(Task)->Unit = {},availability:String="") {
    val context=LocalContext.current
    val online=LocalOnlineAccess.current
    val coroutine=rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var draft by remember { mutableStateOf<List<Task>?>(null) }
    var questions by remember{mutableStateOf(emptyList<Clarification>())}
    var answers by remember{mutableStateOf(emptyMap<Int,String>())}
    var assumptions by remember{mutableStateOf(emptyList<String>())}
    var busy by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var connect by remember{mutableStateOf(false)}
    fun interpret(followUp:Boolean=false) {
        val request=input.trim()
        if(request.isBlank()||busy)return
        busy=true; error=""
        coroutine.launch {
            try {
                val clarification=if(followUp)questions.mapIndexed{i,q->"Tarea "+(q.taskIndex+1)+": "+q.question+"\nRespuesta: "+answers[i].orEmpty()}.joinToString("\n").take(3000)else ""
                val proposal=AiConfig.provider(context,uid).parseNaturalLanguage(request.take(6000)+(if(followUp)"\nACLARACIONES DEL USUARIO (prioritarias): "+clarification+"\nDevuelve propuesta final, no hagas más preguntas." else "")+"\nAgenda ocupada: "+availability.take(2500))
                val tasks=proposal.tasks
                check(tasks.isNotEmpty()){"Gemini no devolvió tareas. Añade más información."}
                draft=tasks.map { t -> t.copy(id=newId(),planHints=PlanningPreferences.merge(t.planHints,SmartCapture.hints(if(tasks.size==1&&!followUp)request else t.description,t.due,java.time.LocalDate.now()),if(tasks.size==1&&!followUp)request else t.description)) }
                questions=if(followUp)emptyList()else CaptureClarifications.questions(proposal.copy(tasks=draft.orEmpty()));answers=emptyMap();assumptions=proposal.assumptions.take(2)
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { error=e.localizedMessage?:"No se pudo usar Gemini." }
            finally { busy=false }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp).padding(top=16.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        val review=draft!=null
        run {
            Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(!review) {
                    Surface(shape=RoundedCornerShape(24.dp),color=soft(Lilac,.15f)) { Icon(Icons.Default.AutoAwesome,null,Modifier.padding(15.dp).size(30.dp),tint=Lilac) }
                    Section("¿Qué tienes en mente?","Escribe lo que necesitas hacer. Planidroid lo organizará por ti.")
                    OutlinedTextField(input,{input=it.take(16000)},Modifier.fillMaxWidth().heightIn(min=160.dp),shape=RoundedCornerShape(24.dp),minLines=5,enabled=!busy)
                    TextButton(onClick={manual(if(input.isBlank())Task(title="")else CaptureParser.parse(input.trim().replace(Regex("\\s*\\n\\s*")," ")).firstOrNull()?.let{it.copy(description=input.trim())}?:Task(title=input.trim().take(180)))},enabled=!busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Edit,null);Spacer(Modifier.width(8.dp));Text("Editar manualmente")}
                    Button(onClick={if(!AiConfig.configured(context,uid))connect=true else consent=true},enabled=input.isNotBlank()&&!busy,modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(18.dp)) { Icon(Icons.Default.AutoAwesome,null); Spacer(Modifier.width(10.dp)); Text(if(busy)"Organizando…" else "Organizar") }
                    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
                    Text("Puedes escribir una tarea o varias, una por línea. No se guarda nada sin tu confirmación.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Section("Tu propuesta","Revisa y ajusta lo necesario.")
                    if(questions.isNotEmpty())GlassCard(tint=Lilac){
                        Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text("Solo necesito aclarar esto",style=MaterialTheme.typography.titleMedium)}
                        questions.forEachIndexed{i,q->Text(q.question);OutlinedTextField(answers[i].orEmpty(),{answers=answers+(i to it.take(1500))},Modifier.fillMaxWidth(),placeholder={Text("Responde con tus palabras")},minLines=2,enabled=!busy,shape=RoundedCornerShape(16.dp))}
                        Button(enabled=!busy&&questions.indices.any{answers[it].orEmpty().isNotBlank()},onClick={
                            val simple=questions.indices.all{i->val q=questions[i];val a=answers[i].orEmpty().trim();val exact=when(q.field){"topicCount","minutes"->a.matches(Regex("[0-9]+")); "due"->a.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"));else->false};exact&&draft.orEmpty().getOrNull(q.taskIndex)?.let{CaptureClarifications.apply(it,q.field,a)}!=null}
                            if(simple){val next=draft.orEmpty().toMutableList();questions.forEachIndexed{i,q->CaptureClarifications.apply(next[q.taskIndex],q.field,answers[i].orEmpty())?.let{next[q.taskIndex]=it}};draft=next;questions=emptyList();answers=emptyMap()}else interpret(true)
                        },modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text(if(busy)"Interpretando…" else "Continuar")}
                        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
                        TextButton(enabled=!busy,onClick={questions=emptyList();answers=emptyMap()}){Text("Prefiero completar a mano")}
                    }
                    if(assumptions.isNotEmpty())Text(assumptions.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    draft.orEmpty().forEachIndexed { index,task -> key(task.id) {
                        var details by remember { mutableStateOf(false) }
                        fun update(t:Task){draft=draft.orEmpty().map{if(it.id==task.id)t else it}}
                        GlassCard(tint=categoryColor(task.category)) {
                            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Checklist,null,tint=categoryColor(task.category));Spacer(Modifier.width(8.dp));Text(task.title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);if(draft.orEmpty().size==1&&questions.isEmpty())IconButton(onClick={manual(task)}){Icon(Icons.Default.Edit,"Abrir formulario completo")}}
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                if(task.due.isNotBlank()) Text(task.due,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                                if(task.minutes>0) Text("${task.minutes} min",style=MaterialTheme.typography.labelLarge)
                                if(task.phases.isNotEmpty()) Text("${task.phases.size} fases",style=MaterialTheme.typography.labelLarge)
                            }
                            if(task.planHints.windowStart.isNotBlank())Text("Horario: "+task.planHints.windowStart+" – "+task.planHints.windowEnd,style=MaterialTheme.typography.bodySmall)
                            if(details&&task.planHints.daysPerWeek>0) Text("Disponibilidad: "+task.planHints.daysPerWeek+" días por semana",style=MaterialTheme.typography.bodySmall)
                            if(details&&task.planHints.minutesPerTopic>0) Text("Por tema: "+task.planHints.minutesPerTopic+" min",style=MaterialTheme.typography.bodySmall)
                            if(task.minutes==0&&task.planHints.explanation.isNotBlank()) Text(task.planHints.explanation,style=MaterialTheme.typography.bodySmall)
                            if(task.planHints.breakMinutes>0)Text("Bloques de "+task.planHints.sessionLimit+" min · pausas de "+task.planHints.breakMinutes+" min",style=MaterialTheme.typography.bodySmall)
                            if(details&&task.planHints.breakReason.isNotBlank())Text(task.planHints.breakReason,style=MaterialTheme.typography.bodySmall)
                            if(details&&task.planHints.dailyMinutes>0) Text("Máximo diario: "+task.planHints.dailyMinutes+" min",style=MaterialTheme.typography.bodySmall)
                            if(details)task.phases.take(8).forEach{Text("• ${it.title}",style=MaterialTheme.typography.bodyMedium)}
                            TextButton(onClick={details=!details}){Text(if(details)"Ocultar detalles" else "Ajustar detalles")}
                            if(details){Field(task.title,{update(task.copy(title=it))},"Título");Field(task.description,{update(task.copy(description=it))},"Notas",false);DateField(task.due,{update(task.copy(due=it,category=if(it.isBlank())Category.INBOX else Category.DEADLINE))},"Fecha límite");Field(if(task.minutes==0)"" else task.minutes.toString(),{v->if(v.isBlank())update(task.copy(minutes=0))else v.toIntOrNull()?.takeIf{it in 1..100000}?.let{update(task.copy(minutes=it))}},"Duración (min)")}
                            if(questions.isEmpty()&&draft.orEmpty().size>1) TextButton(onClick={draft=draft.orEmpty().filterIndexed{i,_->i!=index}}){Text("Descartar")}
                        }
                    } }
                    val tasks=draft.orEmpty()
                    val valid=questions.isEmpty()&&!submitted&&tasks.isNotEmpty()&&tasks.all{it.title.isNotBlank()&&(it.category!=Category.DEADLINE||it.due.isNotBlank())&&(it.due.isBlank()||runCatching{java.time.LocalDate.parse(it.due)}.isSuccess)}
                    val shouldPlan=tasks.size==1&&tasks.first().let{it.due.isNotBlank()||it.minutes>0||it.planHints.minutesPerTopic>0}
                    if(shouldPlan){
                        Button(enabled=valid,onClick={submitted=true;val t=tasks.first();plan(if(t.minutes==0&&t.phases.isEmpty())t.copy(minutes=t.planHints.suggestedMinutes)else t)},modifier=Modifier.fillMaxWidth().height(56.dp)){Text("Crear mi planificación")}
                        TextButton(enabled=valid,onClick={submitted=true;save(tasks)},modifier=Modifier.align(Alignment.CenterHorizontally)){Text("Guardar solo la tarea")}
                    }else{
                        Button(enabled=valid,onClick={submitted=true;save(tasks)},modifier=Modifier.fillMaxWidth().height(56.dp)){Text(if(tasks.size==1)"Guardar tarea" else "Guardar "+tasks.size+" tareas")}
                    }
                    TextButton(onClick={draft=null;questions=emptyList();answers=emptyMap();submitted=false;error=""},modifier=Modifier.align(Alignment.CenterHorizontally)){Text("Volver al texto")}
                }
            }
        }
    }
    if(connect)PersonalGeminiDialog(uid){connect=false}
    if(consent) AlertDialog(onDismissRequest={consent=false},title={Text("Organizar con Gemini")},text={Text("Se enviarán tu petición y los intervalos ocupados relevantes, sin títulos de citas, a Gemini con tu clave personal. La propuesta incluye fases, horarios y descansos.")},confirmButton={TextButton(onClick={consent=false;interpret()}){Text("Continuar")}},dismissButton={TextButton(onClick={consent=false}){Text("Cancelar")}})
}
