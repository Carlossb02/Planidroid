package es.planidroid.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.planidroid.app.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

private fun screenTitle(tab:Tab)=if(tab==Tab.HOME)"Mi día" else tab.label
private enum class Tab(val label:String){HOME("Inicio"),CALENDAR("Calendario"),TASKS("Tareas"),CAPTURE("Añadir"),SPACES("Espacios"),INVENTORY("Hogar"),SHOPPING("Compra"),SETTINGS("Ajustes"), TOOLS("Herramientas"), SHARED("Compartido"), ASSISTANT("Asistente"), FOCUS("Concentración")}
fun sessionDate(ms:Long)=Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
fun time(ms:Long)=Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
@Composable fun PlanidroidScreen(model: PlanidroidModel) {
    val uid by model.scope.collectAsStateWithLifecycle()
    val access by model.access.collectAsStateWithLifecycle()
    val theme by model.theme.collectAsStateWithLifecycle();val glass by model.glass.collectAsStateWithLifecycle()
    val allowed=access.uid==uid&&access.allowed&&uid!="local"
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,uid,allowed){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME&&allowed)model.resumeSync(uid)}
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer)}
    }
    if(allowed){key(uid){PrivateScreen(model)}}else PlanidroidTheme(theme,glass){AccountGate(model,uid,access.verified)}
}
@Composable private fun PrivateScreen(model: PlanidroidModel) {
    val theme by model.theme.collectAsStateWithLifecycle(); val glass by model.glass.collectAsStateWithLifecycle()
    val scope by model.scope.collectAsStateWithLifecycle(); val all by model.rows.collectAsStateWithLifecycle(); val message by model.message.collectAsStateWithLifecycle()
    val rows=all.filter { it.scope==scope };val ext by model.external.collectAsStateWithLifecycle();val external=if(ext.first==scope)ext.second else emptyList();val blocked=remember(external){external.filter{it.busy}.map{Interval(it.start,it.end)}}
    val tasks=remember(rows){ rows.filter{it.kind==Kind.TASK.name}.mapNotNull { runCatching{decode<Task>(it.payload)}.getOrNull() } }
    val sessions=remember(rows){ rows.filter{it.kind==Kind.SESSION.name}.mapNotNull { runCatching{decode<Session>(it.payload)}.getOrNull() } }
    val furniture=remember(rows){ rows.filter{it.kind==Kind.FURNITURE.name}.mapNotNull { runCatching{decode<Furniture>(it.payload)}.getOrNull() } }
    val homes=remember(rows){rows.filter{it.kind==Kind.HOME.name}.mapNotNull{runCatching{decode<Home>(it.payload)}.getOrNull()}}
    val shoppingLists=remember(rows){rows.filter{it.kind==Kind.SHOPPING_LIST.name}.mapNotNull{runCatching{decode<ShoppingList>(it.payload)}.getOrNull()}}
    val inventory=remember(rows){ rows.filter{it.kind==Kind.ITEM.name}.mapNotNull { runCatching{decode<Item>(it.payload)}.getOrNull() } }
    val groups=remember(rows){ rows.filter{it.kind==Kind.GROUP.name}.mapNotNull { runCatching{decode<Group>(it.payload)}.getOrNull() } }
    val shopping=remember(rows){rows.filter{it.kind==Kind.SHOPPING.name}.mapNotNull{runCatching{decode<Shopping>(it.payload)}.getOrNull()}}
    val purchases=remember(rows){rows.filter{it.kind==Kind.PURCHASE.name}.mapNotNull{runCatching{decode<Purchase>(it.payload)}.getOrNull()}}
    val compactAvailability=remember(sessions,blocked,LocalDate.now()){AgendaContext.availability(sessions,blocked,LocalDate.now(),LocalDate.now().plusDays(30))}
    var tab by rememberSaveable(scope){mutableStateOf(Tab.HOME)};val screenState=rememberSaveableStateHolder(); var edit by remember(scope){mutableStateOf<Task?>(null)}; var plan by remember(scope){mutableStateOf<Task?>(null)}
    var manualEvent by remember(scope){mutableStateOf<java.time.LocalDate?>(null)};var onlineFeature by remember{mutableStateOf<String?>(null)};var agendaPlan by remember(scope){mutableStateOf(false)};var agendaDraft by remember(scope){mutableStateOf<Task?>(null)};val context=LocalContext.current; val snack=remember{SnackbarHostState()}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    LaunchedEffect(message){if(message.isNotBlank()){snack.showSnackbar(message);model.message.value=""}}
    fun back(){when{agendaPlan->{agendaPlan=false;agendaDraft=null};plan!=null->plan=null;edit!=null->edit=null;tab in listOf(Tab.TOOLS,Tab.SHARED,Tab.INVENTORY,Tab.SHOPPING)->tab=Tab.SPACES;else->tab=Tab.HOME}}
    BackHandler(edit!=null || plan!=null || agendaPlan || tab!=Tab.HOME){back()}
    PlanidroidTheme(theme,glass) {
        es.planidroid.app.update.UpdatePrompt()
        CompositionLocalProvider(LocalOnlineAccess provides { feature -> onlineFeature=feature }) {
        Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={SnackbarHost(snack)},topBar={Surface(color=MaterialTheme.colorScheme.background){Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){if(tab !in listOf(Tab.HOME,Tab.CALENDAR,Tab.CAPTURE,Tab.TASKS,Tab.SPACES))IconButton(onClick={back()}){Icon(Icons.Default.ArrowBack,"Volver")} ;Text(screenTitle(tab),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));ProfileAvatar(scope,onClick={tab=Tab.SETTINGS;edit=null;plan=null;agendaPlan=false})}}},bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp){listOf(Tab.HOME,Tab.CALENDAR,Tab.CAPTURE,Tab.TASKS,Tab.SPACES).forEach{t->NavigationBarItem(selected=tab==t||(t==Tab.SPACES&&tab in listOf(Tab.INVENTORY,Tab.SHOPPING,Tab.SHARED,Tab.TOOLS)),onClick={tab=t;edit=null;plan=null;agendaPlan=false;agendaDraft=null},icon={Icon(when(t){Tab.HOME->Icons.Default.Home;Tab.CALENDAR->Icons.Default.CalendarMonth;Tab.CAPTURE->Icons.Default.AddCircle;Tab.TASKS->Icons.Default.Checklist;else->Icons.Default.GridView},t.label,Modifier.size(if(t==Tab.CAPTURE)30.dp else 24.dp))},label={Text(t.label,maxLines=1)})}}}){pad->
            Box(Modifier.fillMaxSize().padding(pad)){screenState.SaveableStateProvider(tab.name){when(tab){
                Tab.HOME->Home(tasks,sessions,{edit=it},{tab=Tab.CAPTURE},{tab=Tab.INVENTORY},{tab=Tab.SHOPPING},{tab=Tab.CALENDAR},{tab=Tab.TASKS},{tab=Tab.SPACES},inventory.size,shopping.count{ShoppingLogic.needed(it,inventory.find{p->p.id==it.itemId})>0},{model.save(listOf(it))},{tab=Tab.FOCUS},external,{tab=Tab.ASSISTANT})
                Tab.FOCUS->FocusScreen(tasks){id,min->model.logFocus(id,min)}
                Tab.TASKS->TaskList(tasks,{edit=it},{tab=Tab.CAPTURE},{model.delete(it.id,Kind.TASK)})
                Tab.CALENDAR->CalendarScreen(tasks,sessions,{edit=it},{model.save(listOf(it))},{model.delete(it.id,Kind.SESSION)},{agendaDraft=null;agendaPlan=true},external,{manualEvent=it})
                Tab.CAPTURE->Capture(scope,{created->model.saveTasks(created);tab=Tab.TASKS},{created->plan=created;tab=Tab.TASKS},{prefill->edit=prefill},compactAvailability)
                Tab.ASSISTANT->AssistantScreen(scope,tasks,{model.saveTasks(it)},{plan=it;tab=Tab.CALENDAR},compactAvailability)
                Tab.SPACES->SpacesHub(groups,tasks,{model.save(it)},{model.delete(it.id,Kind.GROUP)},{edit=it},{tab=Tab.INVENTORY},{tab=Tab.SHOPPING},{tab=Tab.SHARED},{tab=Tab.ASSISTANT},{tab=Tab.TOOLS})
                Tab.INVENTORY->HomesScreen(scope,homes,furniture,inventory,model)
                Tab.SHOPPING->ShoppingListsScreen(scope,shoppingLists,shopping,purchases,inventory,model)
                Tab.SHARED->SharedScreen(scope)
                Tab.SETTINGS->SettingsScreen(model,scope)
                Tab.TOOLS->ToolsScreen(model,scope,theme,glass)
            }}}
        }
        manualEvent?.let{day->ManualEventDialog(day,{manualEvent=null},{title,start,end,reminder->model.createManualEvent(title,start,end,reminder);if(reminder&&Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS);manualEvent=null})}
        edit?.let { task -> TaskEditor(task,groups,{edit=null},{t->model.save(t); if(t.reminder && Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS);edit=null;if(tab==Tab.CAPTURE)tab=Tab.TASKS},{model.delete(task.id,Kind.TASK);edit=null},{t->edit=null;plan=t},{t->model.save(t.copy(id=newId(),title=t.title+" (copia)",dependencies=emptyList(),seriesId="",overlapWith=emptyList()));edit=null},tasks,scope,compactAvailability) }
        if(agendaPlan){val draft=agendaDraft;val candidates=if(draft==null)tasks else tasks.filterNot{it.id==draft.id}+draft;AgendaEditor(candidates,sessions,{agendaPlan=false;agendaDraft=null},{new,old,updates,finished->val selectedDraft=draft?.takeIf{d->new.any{it.taskId==d.id}}?.let{d->updates.find{it.id==d.id}?:d};model.commitPlan(new,old,updatedTask=selectedDraft,updates=updates.filterNot{it.id==draft?.id},allowBatch=true,finished={failure->finished(failure);if(failure==null){agendaPlan=false;agendaDraft=null;tab=Tab.CALENDAR}})},blocked,scope,draft)}
        plan?.let { t -> PlanDialog(t,sessions,{plan=null},{updated,blocks,replacing,finished->model.commitPlan(blocks,replacing,updated,finished={failure->finished(failure);if(failure==null){plan=null;tab=Tab.CALENDAR}})},blocked,tasks,scope,{draft->agendaDraft=draft;plan=null;agendaPlan=true}) }
        }
        onlineFeature?.let{feature->OnlineSignInDialog(feature,model,scope){onlineFeature=null}}
    }
}
@Composable fun TaskCard(t:Task,onClick:()->Unit){val tint=categoryColor(t.category);val p by animateFloatAsState(t.progress(),label="task progress");GlassCard(Modifier.clickable(onClick=onClick),tint){Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(14.dp),color=soft(tint,.18f)){Icon(if(t.status==Status.DONE)Icons.Default.CheckCircle else if(t.phases.isNotEmpty())Icons.Default.Folder else Icons.Default.Checklist,null,Modifier.padding(11.dp).size(22.dp),tint=tint)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(t.title,style=MaterialTheme.typography.titleMedium);Text("${t.category.label} · ${t.status.label}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Default.ChevronRight,null)};if(t.description.isNotBlank())Text(t.description,maxLines=2,overflow=TextOverflow.Ellipsis);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){if(t.due.isNotBlank())Text("${t.due}",color=tint,style=MaterialTheme.typography.labelLarge);if(t.minutes>0)Text("${t.minutes} min",style=MaterialTheme.typography.labelLarge);if(t.priority.ordinal>=Priority.HIGH.ordinal)Text(t.priority.label,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelLarge)};if(t.checks.isNotEmpty()||t.phases.isNotEmpty()){LinearProgressIndicator(progress={p},modifier=Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(8.dp)),color=tint);Text("${(p*100).toInt()}% completado",style=MaterialTheme.typography.labelSmall)}}}
@Composable fun Spaces(groups:List<Group>,tasks:List<Task>,save:(Group)->Unit,delete:(Group)->Unit,edit:(Task)->Unit){var group by remember{mutableStateOf<Group?>(null)};var remove by remember{mutableStateOf<Group?>(null)};LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Section("Espacios","Proyectos personales y acceso a espacios compartidos.");Button(onClick={group=Group(name="")}){Text("Crear proyecto")}};items(groups,key={it.id}){g->GlassCard{Text(g.name,style=MaterialTheme.typography.titleLarge);Text(g.description);tasks.filter{it.groupId==g.id}.forEach{t->TextButton(onClick={edit(t)}){Text(t.title)}};Row{TextButton(onClick={edit(Task(title="",groupId=g.id))}){Text("Añadir tarea")};TextButton(onClick={group=g}){Text("Editar")};TextButton(onClick={remove=g}){Text("Eliminar")}}}}};group?.let{g->var name by remember(g){mutableStateOf(g.name)};var desc by remember(g){mutableStateOf(g.description)};AlertDialog(onDismissRequest={group=null},title={Text("Proyecto")},text={Column{Field(name,{name=it},"Nombre");Field(desc,{desc=it},"Descripción")}},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={save(g.copy(name=name.trim(),description=desc));group=null}){Text("Guardar")}},dismissButton={TextButton(onClick={group=null}){Text("Cancelar")}})};remove?.let{Confirm("Eliminar proyecto","Sus tareas se conservarán sin proyecto.",{remove=null},{delete(it)})}}
