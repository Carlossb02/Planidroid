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
import androidx.compose.ui.platform.LocalDensity
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
    val preferences by model.viewPreferences.collectAsStateWithLifecycle()
    val allowed=access.uid==uid&&access.allowed&&uid!="local"
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,uid,allowed){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME&&allowed)model.resumeSync(uid)}
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer)}
    }
    if(allowed){key(uid){PrivateScreen(model)}}else PlanidroidTheme(theme,glass,preferences.systemColors){AccountGate(model,uid,access.verified)}
}
@Composable private fun PrivateScreen(model: PlanidroidModel) {
    val theme by model.theme.collectAsStateWithLifecycle(); val glass by model.glass.collectAsStateWithLifecycle()
    val scope by model.scope.collectAsStateWithLifecycle(); val all by model.rows.collectAsStateWithLifecycle(); val message by model.message.collectAsStateWithLifecycle()
    val preferences by model.viewPreferences.collectAsStateWithLifecycle()
    val rows=all.filter { it.scope==scope }
    val blocked=emptyList<Interval>()
    val importPreview by model.calendarFile.collectAsStateWithLifecycle()
    val tasks=remember(rows){ rows.filter{it.kind==Kind.TASK.name}.mapNotNull { runCatching{decode<Task>(it.payload)}.getOrNull() } }
    val sessions=remember(rows){ rows.filter{it.kind==Kind.SESSION.name}.mapNotNull { runCatching{decode<Session>(it.payload)}.getOrNull() } }
    val busySessions=remember(tasks,sessions){
        val index=tasks.associateBy{it.id}
        sessions.filterNot{OverlapPolicy.calendarFree(it,index[it.taskId])}
    }
    val actionable=remember(tasks,sessions){
        val fixed=sessions.filter{it.calendarTitle.isNotBlank()}.map{it.taskId}.toSet()
        tasks.filterNot{it.id in fixed}
    }
    val furniture=remember(rows){ rows.filter{it.kind==Kind.FURNITURE.name}.mapNotNull { runCatching{decode<Furniture>(it.payload)}.getOrNull() } }
    val homes=remember(rows){rows.filter{it.kind==Kind.HOME.name}.mapNotNull{runCatching{decode<Home>(it.payload)}.getOrNull()}}
    val savedRecipes=remember(rows){rows.filter{it.kind==Kind.RECIPE.name}.mapNotNull{runCatching{decode<SavedRecipe>(it.payload)}.getOrNull()}}
    val shoppingLists=remember(rows){rows.filter{it.kind==Kind.SHOPPING_LIST.name}.mapNotNull{runCatching{decode<ShoppingList>(it.payload)}.getOrNull()}}
    val inventory=remember(rows){ rows.filter{it.kind==Kind.ITEM.name}.mapNotNull { runCatching{decode<Item>(it.payload)}.getOrNull() } }
    val groups=remember(rows){ rows.filter{it.kind==Kind.GROUP.name}.mapNotNull { runCatching{decode<Group>(it.payload)}.getOrNull() } }
    val shopping=remember(rows){rows.filter{it.kind==Kind.SHOPPING.name}.mapNotNull{runCatching{decode<Shopping>(it.payload)}.getOrNull()}}
    val purchases=remember(rows){rows.filter{it.kind==Kind.PURCHASE.name}.mapNotNull{runCatching{decode<Purchase>(it.payload)}.getOrNull()}}
    val compactAvailability=remember(busySessions,blocked,LocalDate.now()){AgendaContext.availability(busySessions,blocked,LocalDate.now(),LocalDate.now().plusDays(30))}
    var history by rememberSaveable(scope){mutableStateOf(arrayListOf(Tab.HOME.name))}
    val tab=Tab.valueOf(history.lastOrNull()?:Tab.HOME.name)
    fun navigate(to:Tab){history=ArrayList(pushDestination(history,to.name))}
    val screenState=rememberSaveableStateHolder(); var edit by remember(scope){mutableStateOf<Task?>(null)}; var plan by remember(scope){mutableStateOf<Task?>(null)}
    var calendarFocus by rememberSaveable(scope){mutableStateOf<String?>(null)}
    var calendarEdit by remember(scope){mutableStateOf<Pair<CalendarDraft,Boolean>?>(null)};
    val importFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(model::openCalendarFile)}
    var onlineFeature by remember{mutableStateOf<String?>(null)};var agendaPlan by remember(scope){mutableStateOf(false)};var agendaDraft by remember(scope){mutableStateOf<Task?>(null)};val context=LocalContext.current; val snack=remember{SnackbarHostState()}
    LaunchedEffect(importPreview?.id){
        if(importPreview!=null){
            calendarEdit=null;edit=null;plan=null;agendaPlan=false;agendaDraft=null
            navigate(Tab.CALENDAR)
        }
    }
    fun editCalendarEvent(entry:Session,task:Task){
        calendarEdit=CalendarDraft(entry.id,entry.calendarTitle,entry.start,entry.end,
            isAllDayEvent(entry),task.goal,task.description,task.reminder,!OverlapPolicy.calendarFree(entry,task)) to true
    }
    val keyboardOpen=WindowInsets.ime.getBottom(LocalDensity.current)>0
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    LaunchedEffect(message){if(message.isNotBlank()){snack.showSnackbar(message);model.message.value=""}}
    var sectionBack by remember(tab){mutableStateOf<(() -> Unit)?>(null)}
    val registerBack=remember(tab){ {action:(()->Unit)?->sectionBack=action} }
    fun back(){when{agendaPlan->{agendaPlan=false;agendaDraft=null};plan!=null->plan=null;edit!=null->edit=null;sectionBack!=null->sectionBack?.invoke();history.size>1->history=ArrayList(popDestination(history))}}
    BackHandler(edit!=null || plan!=null || agendaPlan || sectionBack!=null || history.size>1){back()}
    PlanidroidTheme(theme,glass,preferences.systemColors) {
        es.planidroid.app.update.UpdatePrompt()
        CompositionLocalProvider(LocalOnlineAccess provides { feature -> onlineFeature=feature },LocalRecipeLibrary provides RecipeLibrary(savedRecipes,model),LocalSectionBack provides registerBack,LocalViewPreferences provides preferences) {
        WindowFrame { Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),containerColor=MaterialTheme.colorScheme.background,snackbarHost={SnackbarHost(snack)},topBar={Surface(color=MaterialTheme.colorScheme.background){Row(Modifier.fillMaxWidth().padding(horizontal=ScreenGutter,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){if(history.size>1&&sectionBack==null)IconButton(onClick={back()}){Icon(Icons.Default.ArrowBack,"Volver")} ;Text(screenTitle(tab),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));ProfileAvatar(scope,onClick={navigate(Tab.SETTINGS);edit=null;plan=null;agendaPlan=false})}}},bottomBar={if(!keyboardOpen)NavigationBar(modifier=Modifier.padding(horizontal=8.dp),windowInsets=WindowInsets(0,0,0,0),containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp){listOf(Tab.HOME,Tab.CALENDAR,Tab.CAPTURE,Tab.TASKS,Tab.SPACES).forEach{t->NavigationBarItem(selected=tab==t||(t.name==history.dropLast(1).lastOrNull()&&tab in listOf(Tab.INVENTORY,Tab.SHOPPING,Tab.SHARED,Tab.TOOLS)),onClick={navigate(t);edit=null;plan=null;agendaPlan=false;agendaDraft=null},icon={Icon(when(t){Tab.HOME->Icons.Default.Home;Tab.CALENDAR->Icons.Default.CalendarMonth;Tab.CAPTURE->Icons.Default.AddCircle;Tab.TASKS->Icons.Default.Checklist;else->Icons.Default.GridView},null,Modifier.size(if(t==Tab.CAPTURE)26.dp else 23.dp))},label={Text(t.label,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)})}}}){pad->
            Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)){screenState.SaveableStateProvider(tab.name){when(tab){
                Tab.HOME->Home(tasks,sessions,{edit=it},{navigate(Tab.CAPTURE)},{navigate(Tab.INVENTORY)},{navigate(Tab.SHOPPING)},{navigate(Tab.CALENDAR)},{navigate(Tab.TASKS)},{navigate(Tab.SPACES)},inventory.size,shopping.count{ShoppingLogic.needed(it,inventory.find{p->p.id==it.itemId})>0},{model.save(listOf(it))},{navigate(Tab.FOCUS)},{navigate(Tab.ASSISTANT)})
                Tab.FOCUS->FocusScreen(actionable){id,min->model.logFocus(id,min)}
                Tab.TASKS->TaskList(actionable,{edit=it},{navigate(Tab.CAPTURE)},{model.delete(it.id,Kind.TASK)},{task,status->model.changeTaskStatus(task.id,status)})
                Tab.CALENDAR->CalendarScreen(tasks,sessions,{edit=it},{model.save(listOf(it))},{model.delete(it.id,Kind.SESSION)},
                    {agendaDraft=null;agendaPlan=true},addEvent={day->
                        val start=day.atTime(9,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        calendarEdit=CalendarDraft(newId(),"",start,start+3_600_000L) to false
                    },importFile={importFile.launch(arrayOf("text/calendar","text/x-vcalendar","application/ics","application/icalendar","application/octet-stream"))},
                    editEvent=::editCalendarEvent,focusDay=calendarFocus,consumeFocus={calendarFocus=null})
                Tab.CAPTURE->Capture(scope,{created->model.saveTasks(created);navigate(Tab.TASKS)},{created->plan=created;navigate(Tab.TASKS)},{prefill->edit=prefill},compactAvailability)
                Tab.ASSISTANT->AssistantScreen(scope,actionable,shoppingLists,model,{plan=it;navigate(Tab.CALENDAR)},compactAvailability)
                Tab.SPACES->SpacesHub(groups,tasks,{model.save(it)},{model.delete(it.id,Kind.GROUP)},{edit=it},{navigate(Tab.INVENTORY)},{navigate(Tab.SHOPPING)},{navigate(Tab.SHARED)},{navigate(Tab.ASSISTANT)},{navigate(Tab.TOOLS)})
                Tab.INVENTORY->HomesScreen(scope,homes,furniture,inventory,model,lists=shoppingLists)
                Tab.SHOPPING->ShoppingListsScreen(scope,shoppingLists,shopping,purchases,inventory,model)
                Tab.SHARED->SharedScreen(scope)
                Tab.SETTINGS->SettingsScreen(model,scope)
                Tab.TOOLS->ToolsScreen(model,scope)
            }}}
        }
        }
        fun requestEventReminder(remind:Boolean){
            if(remind&&Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        calendarEdit?.let{(draft,revising)->
            CalendarEventDialog(draft,if(revising)"Editar evento" else "Nuevo evento",{calendarEdit=null},save={value,done->
                val completed:(String?)->Unit={failure->done(failure);if(failure==null)requestEventReminder(value.reminder)}
                if(revising)model.updateCalendarEvent(draft.sourceId,value,completed)
                else model.saveCalendarEvents(listOf(value),false,completed)
            },delete=if(revising)({model.delete(draft.sourceId,Kind.SESSION)}) else null)
        }
        importPreview?.let{preview->
            val close={model.dismissCalendarFile(preview.id)}
            when{
                preview.file!=null->CalendarFileDialog(preview.file,close){events,done->
                    model.saveCalendarEvents(events,true){failure->
                        done(failure)
                        if(failure==null){
                            calendarFocus=events.minOfOrNull{it.start}?.let{sessionDate(it).toString()}
                            requestEventReminder(events.any{it.reminder})
                        }
                    }
                }
                else->AlertDialog(onDismissRequest=close,title={Text("Importar calendario")},
                    text={val error=preview.error;if(error!=null)Text(error)else LinearProgressIndicator(Modifier.fillMaxWidth())},
                    confirmButton={TextButton(onClick=close){Text("Cerrar")}})
            }
        }
        edit?.let { task -> TaskEditor(task,groups,{edit=null},{t->model.save(t); if(t.reminder && Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS);edit=null;if(tab==Tab.CAPTURE)navigate(Tab.TASKS)},{model.delete(task.id,Kind.TASK);edit=null},{t->edit=null;plan=t},{t->model.save(t.copy(id=newId(),title=t.title+" (copia)",dependencies=emptyList(),seriesId="",overlapWith=emptyList()));edit=null},actionable,scope,compactAvailability) }
        if(agendaPlan){val draft=agendaDraft;val candidates=if(draft==null)actionable else actionable.filterNot{it.id==draft.id}+draft;AgendaEditor(candidates,busySessions,{agendaPlan=false;agendaDraft=null},{new,old,updates,finished->val selectedDraft=draft?.takeIf{d->new.any{it.taskId==d.id}}?.let{d->updates.find{it.id==d.id}?:d};model.commitPlan(new,old,updatedTask=selectedDraft,updates=updates.filterNot{it.id==draft?.id},allowBatch=true,finished={failure->finished(failure);if(failure==null){agendaPlan=false;agendaDraft=null;navigate(Tab.CALENDAR)}})},blocked,scope,draft)}
        plan?.let { t -> PlanDialog(t,busySessions,{plan=null},{updated,blocks,replacing,finished->model.commitPlan(blocks,replacing,updated,finished={failure->finished(failure);if(failure==null){plan=null;navigate(Tab.CALENDAR)}})},blocked,actionable,scope,{draft->agendaDraft=draft;plan=null;agendaPlan=true}) }
        }
        onlineFeature?.let{feature->OnlineSignInDialog(feature,model,scope){onlineFeature=null}}
    }
}
@Composable fun TaskCard(t:Task,onClick:()->Unit){val tint=categoryColor(t.category);val p by animateFloatAsState(t.progress(),label="task progress");GlassCard(Modifier.clickable(onClick=onClick),tint){Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(14.dp),color=soft(tint,.18f)){Icon(if(t.status==Status.DONE)Icons.Default.CheckCircle else if(t.phases.isNotEmpty())Icons.Default.Folder else Icons.Default.Checklist,null,Modifier.padding(11.dp).size(22.dp),tint=tint)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(t.title,style=MaterialTheme.typography.titleMedium);Text("${t.category.label} · ${t.status.label}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Default.ChevronRight,null)};if(t.description.isNotBlank())Text(t.description,maxLines=2,overflow=TextOverflow.Ellipsis);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){if(t.due.isNotBlank())Text("${t.due}",color=tint,style=MaterialTheme.typography.labelLarge);if(t.minutes>0)Text("${t.minutes} min",style=MaterialTheme.typography.labelLarge);if(t.priority.ordinal>=Priority.HIGH.ordinal)Text(t.priority.label,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelLarge)};if(t.checks.isNotEmpty()||t.phases.isNotEmpty()){LinearProgressIndicator(progress={p},modifier=Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(8.dp)),color=tint);Text("${(p*100).toInt()}% completado",style=MaterialTheme.typography.labelSmall)}}}
@Composable fun Spaces(groups:List<Group>,tasks:List<Task>,save:(Group)->Unit,delete:(Group)->Unit,edit:(Task)->Unit){var group by remember{mutableStateOf<Group?>(null)};var remove by remember{mutableStateOf<Group?>(null)};LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Section("Espacios","Proyectos personales y acceso a espacios compartidos.");Button(onClick={group=Group(name="")}){Text("Crear proyecto")}};items(groups,key={it.id}){g->GlassCard{Text(g.name,style=MaterialTheme.typography.titleLarge);Text(g.description);tasks.filter{it.groupId==g.id}.forEach{t->TextButton(onClick={edit(t)}){Text(t.title)}};Row{TextButton(onClick={edit(Task(title="",groupId=g.id))}){Text("Añadir tarea")};TextButton(onClick={group=g}){Text("Editar")};TextButton(onClick={remove=g}){Text("Eliminar")}}}}};group?.let{g->var name by remember(g){mutableStateOf(g.name)};var desc by remember(g){mutableStateOf(g.description)};AlertDialog(onDismissRequest={group=null},title={Text("Proyecto")},text={Column{Field(name,{name=it},"Nombre");Field(desc,{desc=it},"Descripción")}},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={save(g.copy(name=name.trim(),description=desc));group=null}){Text("Guardar")}},dismissButton={TextButton(onClick={group=null}){Text("Cancelar")}})};remove?.let{Confirm("Eliminar proyecto","Sus tareas se conservarán sin proyecto.",{remove=null},{delete(it)})}}
