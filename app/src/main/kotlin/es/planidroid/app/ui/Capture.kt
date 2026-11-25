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
@Composable fun Capture(uid:String, save:(List<Task>)->Unit, plan:(Task)->Unit) {
    val context=LocalContext.current
    val coroutine=rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var draft by remember { mutableStateOf<List<Task>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    fun interpret(remote:Boolean) {
        val request=input.trim()
        busy=true; error=""
        coroutine.launch {
            try {
                val tasks=if(remote) AiConfig.provider(context,uid,true).parseNaturalLanguage(request).tasks else CaptureParser.parse(request)
                draft=tasks.ifEmpty { CaptureParser.parse(request) }.map { it.copy(id=newId()) }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { error="No se pudo usar Gemini. Puedes continuar sin conexión." }
            finally { busy=false }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        AnimatedContent(targetState=draft!=null,label="capture step") { review ->
            Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(!review) {
                    Surface(shape=RoundedCornerShape(24.dp),color=soft(Lilac,.15f)) { Icon(Icons.Default.AutoAwesome,null,Modifier.padding(15.dp).size(30.dp),tint=Lilac) }
                    Section("¿Qué tienes en mente?","Escribe lo que necesitas hacer. Planidroid lo organizará por ti.")
                    OutlinedTextField(input,{input=it.take(16000)},Modifier.fillMaxWidth().heightIn(min=160.dp),placeholder={Text("Preparar el examen del 20 de diciembre, dedicar 8 horas y repasar los temas…")},shape=RoundedCornerShape(24.dp),minLines=5,enabled=!busy)
                    Button(onClick={if(AiConfig.mode(context,uid)=="GEMINI")consent=true else interpret(false)},enabled=input.isNotBlank()&&!busy,modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(18.dp)) { Icon(Icons.Default.AutoAwesome,null); Spacer(Modifier.width(10.dp)); Text(if(busy)"Organizando…" else "Organizar") }
                    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(error.isNotBlank()){Text(error,color=MaterialTheme.colorScheme.error);TextButton(onClick={interpret(false)},enabled=!busy){Text("Continuar en local")}}
                    Text("Puedes escribir una tarea o varias, una por línea. No se guarda nada sin tu confirmación.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Section("Tu propuesta","Revisa y ajusta lo necesario.")
                    draft.orEmpty().forEachIndexed { index,task -> key(task.id) {
                        var details by remember { mutableStateOf(false) }
                        fun update(t:Task){draft=draft.orEmpty().map{if(it.id==task.id)t else it}}
                        GlassCard(tint=categoryColor(task.category)) {
                            Field(task.title,{update(task.copy(title=it))},"Tarea")
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                if(task.due.isNotBlank()) Text(task.due,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                                if(task.minutes>0) Text("${task.minutes} min",style=MaterialTheme.typography.labelLarge)
                                if(task.phases.isNotEmpty()) Text("${task.phases.size} fases",style=MaterialTheme.typography.labelLarge)
                            }
                            if(task.minutes==0&&task.planHints.explanation.isNotBlank()) Text(task.planHints.explanation,style=MaterialTheme.typography.bodySmall)
                            if(task.planHints.dailyMinutes>0) Text("Máximo diario: "+task.planHints.dailyMinutes+" min",style=MaterialTheme.typography.bodySmall)
                            task.phases.take(8).forEach{Text("• ${it.title}",style=MaterialTheme.typography.bodyMedium)}
                            TextButton(onClick={details=!details}){Text(if(details)"Ocultar detalles" else "Ajustar detalles")}
                            if(details){Field(task.description,{update(task.copy(description=it))},"Notas",false);DateField(task.due,{update(task.copy(due=it,category=if(it.isBlank())Category.INBOX else Category.DEADLINE))},"Fecha límite");Field(if(task.minutes==0)"" else task.minutes.toString(),{v->if(v.isBlank())update(task.copy(minutes=0))else v.toIntOrNull()?.takeIf{it in 1..100000}?.let{update(task.copy(minutes=it))}},"Duración (min)")}
                            if(draft.orEmpty().size>1) TextButton(onClick={draft=draft.orEmpty().filterIndexed{i,_->i!=index}}){Text("Descartar")}
                        }
                    } }
                    val tasks=draft.orEmpty()
                    val valid=!submitted&&tasks.isNotEmpty()&&tasks.all{it.title.isNotBlank()&&(it.category!=Category.DEADLINE||it.due.isNotBlank())&&(it.due.isBlank()||runCatching{java.time.LocalDate.parse(it.due)}.isSuccess)}
                    Button(enabled=valid,onClick={submitted=true;save(tasks)},modifier=Modifier.fillMaxWidth().height(56.dp)){Text(if(tasks.size==1)"Guardar tarea" else "Guardar ${tasks.size} tareas")}
                    if(tasks.size==1) OutlinedButton(enabled=valid,onClick={submitted=true;val t=tasks.first();plan(if(t.minutes==0&&t.phases.isEmpty())t.copy(minutes=t.planHints.suggestedMinutes)else t)},modifier=Modifier.fillMaxWidth()){Text("Guardar y planificar")}
                    TextButton(onClick={draft=null;submitted=false;error=""},modifier=Modifier.align(Alignment.CenterHorizontally)){Text("Volver al texto")}
                }
            }
        }
    }
    if(consent) AlertDialog(onDismissRequest={consent=false},title={Text("Organizar con Gemini")},text={Text("Se enviará solo el texto que acabas de escribir a tu servidor configurado.")},confirmButton={TextButton(onClick={consent=false;interpret(true)}){Text("Continuar")}},dismissButton={TextButton(onClick={consent=false;interpret(false)}){Text("Usar modo local")}})
}
