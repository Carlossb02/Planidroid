package es.planidroid.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import es.planidroid.app.data.*
import es.planidroid.app.domain.*
import es.planidroid.app.sync.Cloud
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

private val Context.prefs by preferencesDataStore("appearance")
class PlanidroidModel(private val app: Application): AndroidViewModel(app) {
    private val db=PlanidroidDb.get(app); private val dao=db.records(); private val auth=Cloud.auth(app)
    private val _scope=MutableStateFlow(auth?.currentUser?.uid ?: "local"); val scope=_scope.asStateFlow()
    private val listener=com.google.firebase.auth.FirebaseAuth.AuthStateListener { _scope.value=it.currentUser?.uid ?: "local" }
    data class Access(val uid:String="local",val verified:Boolean?=null,val allowed:Boolean=false)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val access=scope.flatMapLatest{uid->
        if(uid=="local")flowOf(Access())else Cloud.observeApproval(app,uid).onStart{emit(null)}.map{server->
            Access(uid,server,AccessPolicy.allowed(uid,Cloud.previouslyApproved(app,uid),server))
        }
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),Access())
    val approval=access.map{it.verified}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),null)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class,kotlinx.coroutines.FlowPreview::class)
    private fun watchDirty(){viewModelScope.launch{scope.flatMapLatest{uid->if(uid=="local")flowOf(uid to emptyList<Record>())else dao.observeDirty(uid).map{uid to it}}.debounce(1500).collect{(uid,dirty)->if(dirty.isNotEmpty()&&Cloud.previouslyApproved(app,uid)){Cloud.dirtyChanged(app,uid)}}}}
    init { auth?.addAuthStateListener(listener);watchDirty() }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pendingCount=scope.flatMapLatest{uid->if(uid=="local")flowOf(0)else dao.observeDirty(uid).map{it.size}}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),0)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val rows=scope.flatMapLatest { dao.observe(it) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val external=scope.flatMapLatest { uid->es.planidroid.app.sync.CalendarSync.observe(app,uid).map{uid to it} }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),"local" to emptyList())
    val theme=app.prefs.data.map { it[stringPreferencesKey("theme")] ?: "Sistema" }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),"Sistema")
    val glass=app.prefs.data.map { it[booleanPreferencesKey("glass")] ?: false }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),false)
    val message=MutableStateFlow(""); val busy=MutableStateFlow(false); val syncState=MutableStateFlow("Sin sincronizar")
    fun configured()=auth!=null
    fun email()=auth?.currentUser?.email.orEmpty()
    fun run(block: suspend ()->Unit) { viewModelScope.launch { try { block() } catch(e: CancellationException) { throw e } catch(e:Exception) { message.value=e.localizedMessage ?: "Error inesperado" } } }
    fun setting(theme: String,glass: Boolean)=run { getApplication<Application>().prefs.edit { it[stringPreferencesKey("theme")]=theme; it[booleanPreferencesKey("glass")]=glass } }
    fun saveTasks(tasks:List<Task>){val uid=scope.value;run{db.withTransaction{tasks.forEach{dao.save(uid,it.id,Kind.TASK.name,encode(it))}};tasks.forEach{Reminders.update(getApplication(),uid,it)}}}
    fun save(t: Task) { val s=scope.value; run { var next:Task?=null;db.withTransaction{val old=dao.get(s,t.id)?.takeUnless{it.deleted}?.let{decode<Task>(it.payload)};if(old!=null&&old.overlapWith!=t.overlapWith){val all=dao.all(s).filterNot{it.deleted};val before=all.filter{it.kind==Kind.TASK.name}.map{decode<Task>(it.payload)};val sessions=all.filter{it.kind==Kind.SESSION.name}.map{decode<Session>(it.payload)};require(!OverlapPolicy.revokedConflict(sessions,before,before.map{if(it.id==t.id)t else it},System.currentTimeMillis())){"Reorganiza las sesiones solapadas antes de retirar el permiso"}};dao.save(s,t.id,Kind.TASK.name,encode(t));if(old?.status!=Status.DONE){RepeatingTasks.next(t)?.let{n->if(dao.get(s,n.id)==null){dao.save(s,n.id,Kind.TASK.name,encode(n));next=n}}}};Reminders.update(getApplication(),s,t);next?.let{Reminders.update(getApplication(),s,it);message.value="Siguiente ocurrencia creada: ${it.due}"} } }
    fun logFocus(taskId:String,minutes:Int){val uid=scope.value;run{require(minutes in 1..1440);db.withTransaction{val row=dao.get(uid,taskId)?.takeUnless{it.deleted}?:error("La tarea ya no existe");val t=decode<Task>(row.payload);dao.save(uid,t.id,Kind.TASK.name,encode(t.copy(actualMinutes=(t.actualMinutes+minutes).coerceAtMost(100000))))};message.value="Registrados $minutes minutos de concentración"}}
    fun saveRecipes(recipes:List<SavedRecipe>,finished:(String?)->Unit={}){
        stockAction({uid->db.withTransaction{
            require(recipes.size in 1..60)
            recipes.forEach{r->RecipeBook.validate(r);val previous=dao.get(uid,r.id);require(previous==null||(!previous.deleted&&previous.kind==Kind.RECIPE.name)){"La receta ya no está disponible"};dao.save(uid,r.id,Kind.RECIPE.name,encode(r.copy(updatedAt=System.currentTimeMillis())))}
        }},finished)
    }
    fun deleteRecipe(id:String,finished:(String?)->Unit={}){
        stockAction({uid->db.withTransaction{val row=dao.get(uid,id);if(row!=null&&!row.deleted&&row.kind==Kind.RECIPE.name)dao.remove(uid,id)}},finished)
    }
    fun save(h: Home) { val uid=scope.value;run { dao.save(uid,h.id,Kind.HOME.name,encode(h)) } }
    fun save(l: ShoppingList) { val uid=scope.value;run { dao.save(uid,l.id,Kind.SHOPPING_LIST.name,encode(l)) } }
    fun saveShopping(items:List<Shopping>) { val uid=scope.value;run { db.withTransaction { items.forEach{dao.save(uid,it.id,Kind.SHOPPING.name,encode(it))} } } }
    fun save(f: Furniture) { val s=scope.value; run { dao.save(s,f.id,Kind.FURNITURE.name,encode(f)) } }
    fun save(i: Item) { val s=scope.value; run { dao.save(s,i.id,Kind.ITEM.name,encode(i.copy(restockAmount=if(i.amount>0)0.0 else i.restockAmount))) } }
    private fun stockAction(block:suspend (String)->Unit,finished:(String?)->Unit){
        val uid=scope.value
        viewModelScope.launch{try{require(scope.value==uid){"La cuenta ha cambiado"};block(uid);finished(null)}catch(e:CancellationException){throw e}catch(e:Exception){val msg=e.localizedMessage?:"No se pudo actualizar el inventario";message.value=msg;finished(msg)}}
    }
    fun consumeItem(expected:Item,amount:Double,finished:(String?)->Unit={}){
        stockAction({uid->db.withTransaction{
            val row=dao.get(uid,expected.id)?.takeIf{!it.deleted&&it.kind==Kind.ITEM.name}?:error("El producto ya no existe")
            val current=decode<Item>(row.payload)
            require(current.amount==expected.amount&&current.unit==expected.unit&&current.name==expected.name){"Las existencias han cambiado. Revisa la cantidad."}
            val next=InventoryConsumption.consume(current,amount)
            dao.save(uid,next.id,Kind.ITEM.name,encode(next))
        }},finished)
    }
    fun dismissRestock(expected:Item,finished:(String?)->Unit={}){
        stockAction({uid->db.withTransaction{
            val row=dao.get(uid,expected.id)?.takeIf{!it.deleted&&it.kind==Kind.ITEM.name}?:return@withTransaction
            val current=decode<Item>(row.payload)
            if(InventoryConsumption.pending(current)&&current.restockAmount==expected.restockAmount)dao.save(uid,current.id,Kind.ITEM.name,encode(current.copy(restockAmount=0.0)))
        }},finished)
    }
    fun restockItem(expected:Item,products:List<Shopping>,listId:String,localInventory:Boolean=true,finished:(String?)->Unit={}){
        stockAction({uid->db.withTransaction{
            require(products.isNotEmpty()&&products.size<=100)
            if(listId=="inventory-restock-list"&&dao.get(uid,listId)==null)dao.save(uid,listId,Kind.SHOPPING_LIST.name,encode(ShoppingList(id=listId,name="Reposición")))
            if(listId.isNotBlank())require(dao.get(uid,listId)?.let{!it.deleted&&it.kind==Kind.SHOPPING_LIST.name}==true){"La lista ya no existe"}
            val current=if(localInventory){
                val row=dao.get(uid,expected.id)?.takeIf{!it.deleted&&it.kind==Kind.ITEM.name}?:error("El producto ya no existe")
                decode<Item>(row.payload).also{require(InventoryConsumption.pending(it)&&it.restockAmount==expected.restockAmount&&it.name==expected.name&&it.unit==expected.unit){"Las existencias o la sugerencia han cambiado"}}
            }else null
            val existing=dao.all(uid).filter{!it.deleted&&it.kind==Kind.SHOPPING.name}.map{decode<Shopping>(it.payload)}.toMutableList()
            for(p in products){
                require(p.name.isNotBlank()&&p.quantity.isFinite()&&p.quantity>0&&p.quantity<=100000)
                val unit=SpanishUnits.canonical(p.unit)?:error("Elige una unidad válida")
                val link=current?.takeIf{p.itemId==it.id&&InventoryConsumption.fold(it.name)==InventoryConsumption.fold(p.name)&&InventoryConsumption.unitKey(it.unit)==InventoryConsumption.unitKey(unit)}
                val candidate=p.copy(id=newId(),name=p.name.trim(),unit=unit,listId=listId,itemId=link?.id.orEmpty(),routine=false,pending=true)
                if(InventoryConsumption.alreadyPending(candidate,existing,current))continue
                val old=existing.find{it.listId==listId&&link!=null&&it.itemId==link.id&&InventoryConsumption.unitKey(it.unit)==InventoryConsumption.unitKey(unit)}
                val next=old?.copy(pending=true,quantity=candidate.quantity,quantitySpecified=true)?:candidate
                dao.save(uid,next.id,Kind.SHOPPING.name,encode(next));existing.removeAll{it.id==next.id};existing+=next
            }
            if(current!=null)dao.save(uid,current.id,Kind.ITEM.name,encode(current.copy(restockAmount=0.0)))
        };message.value="Reposición anotada en la lista"},finished)
    }
    fun save(g: Group) { val s=scope.value; run { dao.save(s,g.id,Kind.GROUP.name,encode(g)) } }
    fun commitPlan(proposed:List<Session>,replacing:List<Session>,updatedTask:Task?=null,updates:List<Task> = emptyList(),allowBatch:Boolean=false,finished:(String?)->Unit={}){val uid=scope.value;viewModelScope.launch{try{require(scope.value==uid);db.withTransaction{
        val priorTasks=dao.all(uid).filter{it.kind==Kind.TASK.name&&!it.deleted}.map{decode<Task>(it.payload)}
        if(updatedTask!=null){require(updatedTask.title.isNotBlank()&&updatedTask.minutes in 1..100000);dao.save(uid,updatedTask.id,Kind.TASK.name,encode(updatedTask))}
        for(update in updates){val old=dao.get(uid,update.id)?.takeUnless{it.deleted}?.let{decode<Task>(it.payload)}?:error("La tarea ya no existe");require(update.minutes in 0..100000);dao.save(uid,old.id,Kind.TASK.name,encode(old.copy(minutes=update.minutes,planHints=update.planHints,overlapWith=update.overlapWith)))}
        val rows=dao.all(uid).filterNot{it.deleted};val current=rows.filter{it.kind==Kind.SESSION.name}.map{decode<Session>(it.payload)};val tasks=rows.filter{it.kind==Kind.TASK.name}.map{decode<Task>(it.payload)}
        require(replacing.all{old->current.contains(old)&&!old.done&&old.start>System.currentTimeMillis()&&old.calendarTitle.isBlank()&&(updatedTask==null||old.taskId==updatedTask.id||allowBatch)}){"El calendario ha cambiado; recalcula antes de aceptar"}
        val busy=current.filterNot{it in replacing}.toMutableList();val externalBusy=es.planidroid.app.sync.CalendarSync.events(getApplication(),uid).filter{it.busy}
        require(!OverlapPolicy.revokedConflict(busy,priorTasks,tasks,System.currentTimeMillis())){"Hay sesiones que se solapan con un permiso retirado. Reorganízalas antes de guardar."}
        require(replacing.filterNot{it.isBreak}.groupBy{it.taskId}.all{(id,old)->!allowBatch||proposed.filter{it.taskId==id&&!it.isBreak}.sumOf{it.end-it.start}>=old.sumOf{it.end-it.start}}){"Una tarea existente perdería tiempo reservado"}
        for(s in proposed.sortedBy{it.start}){val t=tasks.find{it.id==s.taskId}?:error("La tarea ya no existe");require(TaskConstraints.unmet(t,tasks).isEmpty()&&t.status !in listOf(Status.DONE,Status.ARCHIVED,Status.BLOCKED));require(s.start>=System.currentTimeMillis()&&s.end>s.start&&PlanningPreferences.fits(s,t)&&(t.due.isBlank()||java.time.Instant.ofEpochMilli(s.end-1).atZone(java.time.ZoneId.systemDefault()).toLocalDate().let{d->if(t.planHints.finishBeforeDeadline)d<java.time.LocalDate.parse(t.due)else d<=java.time.LocalDate.parse(t.due)})&&busy.none{OverlapPolicy.conflict(s,it,tasks)}&&externalBusy.none{it.start<s.end&&it.end>s.start}){"Conflicto de horario; vuelve a calcular"};busy+=s}
        replacing.forEach{dao.remove(uid,it.id)};proposed.forEach{dao.save(uid,it.id,Kind.SESSION.name,encode(it))}
    };updatedTask?.let{Reminders.update(getApplication(),uid,it)};replacing.forEach{SessionReminders.cancel(getApplication(),uid,it.id)};proposed.forEach{SessionReminders.schedule(getApplication(),uid,it)};message.value="Plan guardado";finished(null)}catch(e:CancellationException){throw e}catch(e:Exception){val failure=e.localizedMessage?:"No se pudo guardar el plan";message.value=failure;finished(failure)}}}
    fun save(sessions: List<Session>) { val s=scope.value; run { val changedTasks=mutableListOf<Task>();db.withTransaction { sessions.forEach { v->val old=dao.get(s,v.id)?.takeUnless{it.deleted}?.let{decode<Session>(it.payload)};val currentTasks=dao.all(s).filter{it.kind==Kind.TASK.name&&!it.deleted}.map{decode<Task>(it.payload)};if(old==null||old.start!=v.start||old.end!=v.end){require(currentTasks.any{it.id==v.taskId}&&v.end>v.start&&dao.all(s).none{it.kind==Kind.SESSION.name&&!it.deleted&&it.id!=v.id&&OverlapPolicy.conflict(v,decode<Session>(it.payload),currentTasks)}&&es.planidroid.app.sync.CalendarSync.events(getApplication(),s).none{it.busy&&it.start<v.end&&it.end>v.start}){"Conflicto de horario"}};dao.save(s,v.id,Kind.SESSION.name,encode(v));if(v.calendarTitle.isNotBlank()&&!v.isBreak){val task=currentTasks.find{it.id==v.taskId}?:error("El evento ya no existe");val updated=task.copy(due=sessionDate(v.start).toString(),reminderTime=time(v.start),minutes=((v.end-v.start)/60000).toInt());dao.save(s,updated.id,Kind.TASK.name,encode(updated));changedTasks+=updated} } };changedTasks.forEach{Reminders.update(getApplication(),s,it)};sessions.forEach{SessionReminders.schedule(getApplication(),s,it)} } }
    fun save(s: Shopping) { val uid=scope.value; run { dao.save(uid,s.id,Kind.SHOPPING.name,encode(s)) } }
    fun buy(id:String) { val uid=scope.value; run { db.withTransaction {
        val row=dao.get(uid,id)?.takeUnless{it.deleted} ?: return@withTransaction
        val s=decode<Shopping>(row.payload)
        val i=dao.get(uid,s.itemId)?.takeUnless{it.deleted}?.let{decode<Item>(it.payload)}
        val n=ShoppingLogic.needed(s,i); if(n<=0)return@withTransaction
        require(i==null||InventoryConsumption.unitKey(s.unit)==InventoryConsumption.unitKey(i.unit)){"Las unidades de compra e inventario no coinciden. Edita el producto."}
        val p=Purchase(shoppingId=s.id,name=s.name,quantity=n,unit=s.unit,routine=s.routine,itemId=i?.id.orEmpty(),stockBefore=i?.amount,stockAfter=i?.amount?.plus(n),icon=s.icon,listId=s.listId,quantitySpecified=s.quantitySpecified||(i!=null&&s.routine&&i.amount<s.minimum),referenceUrl=s.referenceUrl,referenceTitle=s.referenceTitle)
        if(i!=null)dao.save(uid,i.id,Kind.ITEM.name,encode(i.copy(amount=i.amount+n,quantity=amountText(i.amount+n),restockAmount=0.0)))
        dao.save(uid,p.id,Kind.PURCHASE.name,encode(p));dao.save(uid,s.id,Kind.SHOPPING.name,encode(s.copy(pending=false)))
    } } }
    fun repeatPurchase(p:Purchase,undo:Boolean) { val uid=scope.value; run { db.withTransaction {
        val current=dao.get(uid,p.id)?.takeUnless{it.deleted}?.let{decode<Purchase>(it.payload)} ?: return@withTransaction
        if(undo && current.undone)return@withTransaction
        val old=dao.get(uid,current.shoppingId)?.takeUnless{it.deleted}?.let{decode<Shopping>(it.payload)}
        val s=old?.copy(pending=true,quantity=current.quantity,quantitySpecified=current.quantitySpecified,referenceUrl=current.referenceUrl,referenceTitle=current.referenceTitle) ?: Shopping(name=current.name,quantity=current.quantity,unit=current.unit,routine=current.routine,icon=current.icon,listId=current.listId,quantitySpecified=current.quantitySpecified,referenceUrl=current.referenceUrl,referenceTitle=current.referenceTitle)
        dao.save(uid,s.id,Kind.SHOPPING.name,encode(s))
        if(undo){ val item=dao.get(uid,current.itemId)?.takeUnless{it.deleted}?.let{decode<Item>(it.payload)}
            if(ShoppingLogic.canUndo(current,item) && item!=null){ val n=current.stockBefore!!;dao.save(uid,item.id,Kind.ITEM.name,encode(item.copy(amount=n,quantity=amountText(n)))) }
            else if(current.stockAfter!=null)message.value="Stock modificado desde la compra: se conserva. Revisa la cantidad en inventario."
            dao.save(uid,current.id,Kind.PURCHASE.name,encode(current.copy(undone=true)))
        }
    } } }
    fun deleteShopping(ids:Set<String>,listId:String){val uid=scope.value;run{db.withTransaction{ids.forEach{id->val row=dao.get(uid,id);if(row!=null&&!row.deleted&&row.kind==Kind.SHOPPING.name&&decode<Shopping>(row.payload).listId==listId)dao.remove(uid,id)}}}}
    fun delete(id: String,kind: Kind) { val s=scope.value; run { db.withTransaction {
        val all=dao.all(s).filterNot { it.deleted }
        when(kind) {
            Kind.SESSION -> {val session=all.find{it.id==id&&it.kind==Kind.SESSION.name}?.let{decode<Session>(it.payload)};if(session!=null&&session.calendarTitle.isNotBlank()&&!session.isBreak&&all.none{it.id!=id&&it.kind==Kind.SESSION.name&&decode<Session>(it.payload).taskId==session.taskId}){val task=all.find{it.id==session.taskId&&it.kind==Kind.TASK.name}?.let{decode<Task>(it.payload)};if(task!=null){dao.save(s,task.id,Kind.TASK.name,encode(task.copy(reminder=false,due="")));Reminders.cancel(getApplication(),s,task.id)}}}
            Kind.TASK -> {all.filter { it.kind==Kind.SESSION.name && decode<Session>(it.payload).taskId==id }.forEach { dao.remove(s,it.id) };all.filter{it.kind==Kind.TASK.name&&it.id!=id}.map{decode<Task>(it.payload)}.filter{id in it.dependencies||id in it.overlapWith}.forEach{dao.save(s,it.id,Kind.TASK.name,encode(it.copy(dependencies=it.dependencies-id,overlapWith=it.overlapWith-id)))}}
            Kind.FURNITURE -> all.filter { it.kind==Kind.ITEM.name && decode<Item>(it.payload).furnitureId==id }.forEach { dao.remove(s,it.id) }
            Kind.HOME -> {require(all.none{it.kind==Kind.FURNITURE.name&&decode<Furniture>(it.payload).homeId==id}){"Mueve los muebles antes de eliminar el hogar"}}
            Kind.SHOPPING_LIST -> {require(all.none{(it.kind==Kind.SHOPPING.name&&decode<Shopping>(it.payload).listId==id)||(it.kind==Kind.PURCHASE.name&&decode<Purchase>(it.payload).listId==id)}){"La lista contiene productos o historial"}}
            Kind.GROUP -> all.filter { it.kind==Kind.TASK.name }.map { decode<Task>(it.payload) }.filter { it.groupId==id }.forEach { dao.save(s,it.id,Kind.TASK.name,encode(it.copy(groupId=""))) }
            else -> Unit
        }; dao.remove(s,id)
    };if(kind==Kind.TASK)Reminders.cancel(getApplication(),s,id);if(kind==Kind.SESSION)SessionReminders.cancel(getApplication(),s,id);SessionReminders.refresh(getApplication(),s);message.value=if(kind==Kind.TASK)"Tarea eliminada" else "Elemento eliminado" } }
    suspend fun backup(): String=encode(BackupIO.create(dao.all(scope.value),theme.value,glass.value))
    fun restoreLocalReplace(b:Backup){val uid=scope.value;run{require(uid=="local"){"Reemplazar solo se permite en modo local"};val ctx=getApplication<Application>();db.withTransaction{val old=dao.all(uid);val snapshot=encode(BackupIO.create(old,theme.value,glass.value));val dir=java.io.File(ctx.filesDir,"recovery/$uid").apply{mkdirs()};val file=java.io.File(dir,"before-${System.currentTimeMillis()}.json");kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){file.writeText(snapshot)};dao.clearScope(uid);b.entries.forEach{dao.save(uid,it.id,it.kind.name,it.payload)}};b.entries.filter{it.kind==Kind.TASK}.forEach{Reminders.update(ctx,uid,decode<Task>(it.payload))};setting(b.theme,b.glass);message.value="Restauración completada. La copia anterior se conserva en recuperación local."}}
    suspend fun lastRecovery():String{val ctx=getApplication<Application>();val uid=scope.value;return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){val file=java.io.File(ctx.filesDir,"recovery/$uid").listFiles()?.filter{it.name.endsWith(".json")}?.maxByOrNull{it.lastModified()}?:error("No hay copia de recuperación");file.readText()}}
    fun clearLocal(){val uid=scope.value;run{require(uid=="local");db.withTransaction{val old=dao.all(uid);val dir=java.io.File(getApplication<Application>().filesDir,"recovery/$uid").apply{mkdirs()};val snapshot=encode(BackupIO.create(old,theme.value,glass.value));kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){java.io.File(dir,"before-${System.currentTimeMillis()}.json").writeText(snapshot)};dao.clearScope(uid)};message.value="Datos locales eliminados. Copia de recuperación conservada."}}
    fun restore(b: Backup, asNew: Boolean, keepFiles:Boolean=false) { val s=scope.value; run {
        val entries=if(asNew) BackupIO.remap(b,keepFiles) else b.entries
        var count=0
        db.withTransaction { for(e in entries) { if(dao.get(s,e.id)==null) { dao.save(s,e.id,e.kind.name,e.payload); count++ } } }
        entries.filter{it.kind==Kind.TASK}.forEach{e->dao.get(s,e.id)?.takeUnless{it.deleted}?.let{Reminders.update(getApplication(),s,decode<Task>(it.payload))}};setting(b.theme,b.glass);message.value="Importados $count de ${entries.size} elementos. Los existentes no se sobrescriben."
    } }
    fun login(context: Context) { run { busy.value=true;try{Cloud.login(context);val uid=auth?.currentUser?.uid?:return@run;_scope.value=uid;register(uid)}finally{busy.value=false} } }
    private suspend fun register(uid:String){
        try{val allowed=Cloud.registerApproval(getApplication(),uid);if(scope.value==uid){syncState.value=if(allowed)"Cuenta autorizada" else "Pendiente de autorización";if(allowed){Cloud.schedule(getApplication(),uid);Cloud.dirtyChanged(getApplication(),uid)}}}
        catch(e:CancellationException){throw e}catch(e:Exception){if(scope.value==uid){syncState.value="No se pudo comprobar la autorización";message.value=approvalError(e)}}
    }
    fun retryApproval(){val uid=scope.value;if(uid!="local"&&!busy.value)run{busy.value=true;try{register(uid)}finally{busy.value=false}}}
    fun resumeSync(uid:String){if(scope.value==uid&&Cloud.previouslyApproved(getApplication(),uid)){Cloud.schedule(getApplication(),uid);Cloud.refreshOnOpen(getApplication(),uid)}}
    fun createManualEvent(title:String,start:Long,end:Long,reminder:Boolean){val uid=scope.value;run{
        require(uid!="local"&&Cloud.previouslyApproved(getApplication(),uid))
        require(title.isNotBlank()&&end>start&&end-start<=86_400_000)
        val task=Task(title=title.trim(),due=sessionDate(start).toString(),minutes=((end-start)/60_000).toInt(),reminder=reminder,reminderTime=time(start))
        val session=Session(taskId=task.id,start=start,end=end,calendarTitle=task.title)
        db.withTransaction{
            require(dao.all(uid).filter{it.kind==Kind.SESSION.name&&!it.deleted}.map{decode<Session>(it.payload)}.none{it.start<end&&it.end>start}&&es.planidroid.app.sync.CalendarSync.events(getApplication(),uid).none{it.busy&&it.start<end&&it.end>start}){"Hay otro evento en ese horario"}
            dao.save(uid,task.id,Kind.TASK.name,encode(task));dao.save(uid,session.id,Kind.SESSION.name,encode(session))
        }
        Reminders.update(getApplication(),uid,task);message.value="Evento guardado"
    }}
    fun importGuestData(){val uid=scope.value;if(uid=="local")return;run{
        require(Cloud.previouslyApproved(getApplication(),uid))
        val entries=dao.all("local").filterNot{it.deleted}
        db.withTransaction{for(row in entries)if(dao.get(uid,row.id)==null)dao.save(uid,row.id,row.kind,row.payload)}
        entries.filter{it.kind==Kind.TASK.name}.forEach{row->dao.get(uid,row.id)?.takeUnless{it.deleted}?.let{Reminders.update(getApplication(),uid,decode<Task>(it.payload))}};SessionReminders.refresh(getApplication(),uid)
        message.value="Datos del modo invitado copiados a esta cuenta; los originales se conservan."
    }}
    fun logout() { val uid=scope.value;Cloud.recordApproval(getApplication(),uid,false);runCatching{es.planidroid.app.sync.PersonalGeminiKey.remove(getApplication(),uid)};auth?.signOut();_scope.value="local";syncState.value="Sin sincronizar" }
    val automaticSync=scope.map{uid->uid!="local"&&Cloud.automatic(app,uid)}.stateIn(viewModelScope,SharingStarted.Eagerly,false)
    private val _automaticOverride=MutableStateFlow<Pair<String,Boolean>?>(null)
    val autoSyncEnabled=combine(scope,automaticSync,_automaticOverride){uid,auto,override->override?.takeIf{it.first==uid}?.second?:auto}.stateIn(viewModelScope,SharingStarted.Eagerly,false)
    fun setAutomaticSync(enabled:Boolean){val uid=scope.value;if(uid=="local")return;run{
        if(enabled)Cloud.requireApproval(getApplication(),uid)
        Cloud.setAutomatic(getApplication(),uid,enabled)
        _automaticOverride.value=uid to enabled
        message.value=if(enabled)"Sincronización automática cada 6 horas aproximadamente" else "Sincronización manual"
    }}
    private fun approvalError(e:Exception):String=if(e is com.google.firebase.firestore.FirebaseFirestoreException && e.code==com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)"Firestore deniega el acceso. Comprueba que las reglas de Planidroid estén publicadas en el proyecto correcto y que el UID coincida." else e.localizedMessage?:"Sin conexión"
    fun sync() { val s=scope.value; if(s=="local"||busy.value) return; busy.value=true;run {
        syncState.value="Sincronizando…"
        try { Cloud.sync(getApplication(),s);syncState.value="Sincronizado" }
        catch(e: CancellationException) { throw e }
        catch(e:Cloud.ApprovalRequired){Cloud.recordApproval(getApplication(),s,false);_automaticOverride.value=s to false;syncState.value="Pendiente de autorización";message.value=e.message.orEmpty()}
        catch(e:Exception) { syncState.value="Error o conflicto"; message.value=approvalError(e) }
        finally{busy.value=false}
    } }
    override fun onCleared() { auth?.removeAuthStateListener(listener); super.onCleared() }
}
