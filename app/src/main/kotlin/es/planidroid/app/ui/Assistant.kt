package es.planidroid.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*
import es.planidroid.app.sync.AiConfig
import es.planidroid.app.sync.SharedCloud
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class GeminiTurn(val id:String,val question:String,val answer:String,val action:AssistantAction?)

@Composable internal fun AssistantScreen(
    uid:String,tasks:List<Task>,lists:List<ShoppingList>,model:PlanidroidModel,
    openPlan:(Task)->Unit,availability:String
){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val scroll=rememberScrollState()
    val eligible=tasks.filter{it.status in setOf(Status.PENDING,Status.ACTIVE)&&TaskConstraints.unmet(it,tasks).isEmpty()}
    val cloud=remember(uid){if(uid=="local")null else runCatching{SharedCloud(context)}.getOrNull()}
    var shared by remember(uid){mutableStateOf(cloud?.cached()?.first.orEmpty())}
    LaunchedEffect(cloud){cloud?.observeCached()?.collect{shared=it.first}}
    val personalLists=lists.filter{list->
        shared.none{it.kind=="shopping_list"&&it.role=="owner"&&it.sourceId==list.id}
    }
    var input by rememberSaveable(uid){mutableStateOf("")}
    var turns by remember(uid){mutableStateOf(emptyList<GeminiTurn>())}
    var pending by remember(uid){mutableStateOf<GeminiTurn?>(null)}
    var includeAgenda by remember(uid){mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var connect by remember{mutableStateOf(false)}
    fun finish(turn:GeminiTurn){
        turns=turns.map{if(it.id==turn.id)it.copy(action=null)else it}
        pending=null
    }
    fun ask(){
        val question=input.trim()
        if(busy||question.isBlank())return
        if(!AiConfig.configured(context,uid)){connect=true;return}
        val conversation=turns.takeLast(4).joinToString("\n"){
            "Usuario: "+it.question.take(600)+"\nGemini: "+it.answer.take(800)
        }
        val included=if(includeAgenda)eligible.take(20)else emptyList()
        val agenda=if(includeAgenda){
            included.joinToString("\n"){task->
                task.id+" | "+task.title.take(160)+" | fecha "+task.due.ifBlank{"sin fecha"}+" | "+task.minutes+" min"
            }+"\nBloques ocupados: "+availability.take(1700)
        }else ""
        busy=true
        error=""
        scope.launch {
            try{
                val reply=AiConfig.provider(context,uid).assistantReply(question,conversation,agenda,included.map{it.id}.toSet())
                turns=(turns+GeminiTurn(newId(),question,reply.answer,reply.action)).takeLast(12)
                input=""
            }catch(cancellation:CancellationException){
                throw cancellation
            }catch(exception:Exception){
                error=exception.localizedMessage?:"No se pudo consultar Gemini."
            }finally{
                busy=false
            }
        }
    }
    LaunchedEffect(turns.lastOrNull()?.id){if(turns.isNotEmpty())scroll.scrollTo(scroll.maxValue)}
    Column(Modifier.fillMaxSize().padding(horizontal=ScreenGutter),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(vertical=8.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)){
            if(turns.isEmpty()) {
                Text("Pregúntale a Gemini",style=MaterialTheme.typography.headlineSmall)
                Text("Puedes pedirle que prepare tareas, una lista de compra o un plan para una tarea de tu agenda.",
                    style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            turns.forEach{turn->
                Surface(modifier=Modifier.align(Alignment.End).widthIn(max=420.dp),shape=RoundedCornerShape(16.dp),
                    color=MaterialTheme.colorScheme.primaryContainer){
                    Text(turn.question,Modifier.padding(horizontal=14.dp,vertical=10.dp),style=MaterialTheme.typography.bodyMedium)
                }
                if(turn.answer.isNotBlank())Text(turn.answer,Modifier.padding(horizontal=4.dp),style=MaterialTheme.typography.bodyLarge)
                val label=when(turn.action){
                    is AssistantAction.Tasks -> "Revisar tareas"
                    is AssistantAction.ShoppingItems -> "Revisar compra"
                    is AssistantAction.Plan -> "Revisar planificación"
                    null -> null
                }
                if(label!=null)FilledTonalButton(enabled=!busy,onClick={pending=turn}){
                    Icon(Icons.Default.AutoAwesome,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            }
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(input,{input=it.take(3000)},Modifier.fillMaxWidth(),label={Text("Pregunta a Gemini")},
            minLines=1,maxLines=4,enabled=!busy,shape=RoundedCornerShape(16.dp))
        if(includeAgenda)Text("Se enviarán títulos, fechas, duración y bloques ocupados para esta consulta.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        ActionFlow {
            FilterChip(includeAgenda,{includeAgenda=!includeAgenda},enabled=!busy,label={Text("Incluir agenda")})
            if(turns.isNotEmpty())TextButton(enabled=!busy,onClick={turns=emptyList();error=""}){Text("Nueva conversación")}
            Button(enabled=!busy&&input.isNotBlank(),onClick=::ask){Text(if(error.isBlank())"Enviar" else "Reintentar")}
        }
        Spacer(Modifier.height(4.dp))
    }
    if(connect)PersonalGeminiDialog(uid){connect=false}
    pending?.let{turn->
        when(val action=turn.action){
            is AssistantAction.Tasks -> ReviewAssistantTasks(action.entries,{pending=null}){entries,done->
                model.saveAssistantTasks(entries){failure->done(failure);if(failure==null)finish(turn)}
            }
            is AssistantAction.ShoppingItems -> ReviewAssistantShopping(action.entries,personalLists,{pending=null}){entries,list,newList,done->
                model.saveAssistantShopping(entries,list,newList){failure->done(failure);if(failure==null)finish(turn)}
            }
            is AssistantAction.Plan -> {
                val target=eligible.find{it.id==action.taskId}
                if(target!=null)Confirm("Planificar "+target.title,"Revisa la disponibilidad en la propuesta del calendario.",
                    {pending=null},{finish(turn);openPlan(target)})
                else AlertDialog(onDismissRequest={pending=null},title={Text("Tarea no disponible")},
                    text={Text("La tarea ha cambiado. Puedes pedir otra propuesta.")},
                    confirmButton={TextButton(onClick={finish(turn)}){Text("Cerrar")}})
            }
            null -> Unit
        }
    }
}

@Composable private fun ReviewAssistantTasks(
    entries:List<Task>,close:()->Unit,save:(List<Task>,(String?)->Unit)->Unit
){
    var selected by remember(entries){mutableStateOf(entries.map{it.id}.toSet())}
    var drafts by remember(entries){mutableStateOf(entries)}
    var expanded by remember(entries){mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    val outgoing=drafts.filter{it.id in selected}
    val valid=outgoing.isNotEmpty()&&outgoing.all{it.title.isNotBlank()&&
        (it.due.isEmpty()||runCatching{java.time.LocalDate.parse(it.due)}.isSuccess)}
    AlertDialog(onDismissRequest={if(!busy)close()},title={Text("Crear tareas")},text={
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            drafts.forEach{task->
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(task.id in selected,{selected=if(it)selected+task.id else selected-task.id},enabled=!busy)
                    Column(Modifier.weight(1f)){
                        Text(task.title.ifBlank{"Tarea sin título"},style=MaterialTheme.typography.titleSmall)
                        val detail=listOfNotNull(task.due.takeIf{it.isNotBlank()},task.minutes.takeIf{it>0}?.let{it.toString()+" min"}).joinToString(" · ")
                        if(detail.isNotBlank())Text(detail,style=MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick={expanded=if(expanded==task.id)null else task.id},enabled=!busy){Text("Editar")}
                }
                if(expanded==task.id){
                    Field(task.title,{value->drafts=drafts.map{if(it.id==task.id)it.copy(title=value.take(180))else it}},"Título")
                    DateField(task.due,{value->drafts=drafts.map{if(it.id==task.id)it.copy(due=value,category=if(value.isBlank())Category.INBOX else Category.DEADLINE)else it}},"Fecha límite")
                }
            }
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={Button(enabled=valid&&!busy,onClick={
        busy=true;error=""
        save(outgoing){failure->busy=false;error=failure.orEmpty()}
    }){Text("Guardar")}},dismissButton={TextButton(enabled=!busy,onClick=close){Text("Cancelar")}})
}

@Composable private fun ReviewAssistantShopping(
    entries:List<Shopping>,lists:List<ShoppingList>,close:()->Unit,
    save:(List<Shopping>,ShoppingList,Boolean,(String?)->Unit)->Unit
){
    var selected by remember(entries){mutableStateOf(entries.map{it.id}.toSet())}
    var target by remember(entries){mutableStateOf(lists.firstOrNull()?.id.orEmpty())}
    var name by remember(entries){mutableStateOf("Compra")}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    val existing=lists.find{it.id==target}
    val valid=selected.isNotEmpty()&&(if(target.isBlank())name.isNotBlank() else existing!=null)
    AlertDialog(onDismissRequest={if(!busy)close()},title={Text("Añadir a la compra")},text={
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(lists.isNotEmpty())Choice("Lista personal",lists.map{it.id}+listOf(""),target,
                {id->lists.find{it.id==id}?.name?:"Nueva lista"}){if(!busy)target=it}
            if(target.isBlank())Field(name,{name=it.take(180)},"Nombre de la nueva lista")
            entries.forEach{item->
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(item.id in selected,{selected=if(it)selected+item.id else selected-item.id},enabled=!busy)
                    ProductGlyph(item.name,Modifier.size(30.dp),item.icon)
                    Column(Modifier.weight(1f).padding(start=8.dp)){
                        Text(item.name,style=MaterialTheme.typography.titleSmall)
                        if(item.quantitySpecified)Text((amountText(item.quantity)+" "+item.unit).trim(),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={Button(enabled=valid&&!busy,onClick={
        busy=true;error=""
        save(entries.filter{it.id in selected},existing?:ShoppingList(name=name.trim()),existing==null){
            busy=false;error=it.orEmpty()
        }
    }){Text("Añadir")}},dismissButton={TextButton(enabled=!busy,onClick=close){Text("Cancelar")}})
}
