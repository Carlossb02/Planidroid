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
class PlanidroidModel(app: Application): AndroidViewModel(app) {
    private val db=PlanidroidDb.get(app); private val dao=db.records(); private val auth=Cloud.auth(app)
    private val _scope=MutableStateFlow(auth?.currentUser?.uid ?: "local"); val scope=_scope.asStateFlow()
    private val listener=com.google.firebase.auth.FirebaseAuth.AuthStateListener { _scope.value=it.currentUser?.uid ?: "local" }
    init { auth?.addAuthStateListener(listener) }
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
    fun save(t: Task) { val s=scope.value; run { var next:Task?=null;db.withTransaction{val old=dao.get(s,t.id)?.takeUnless{it.deleted}?.let{decode<Task>(it.payload)};dao.save(s,t.id,Kind.TASK.name,encode(t));if(old?.status!=Status.DONE){RepeatingTasks.next(t)?.let{n->if(dao.get(s,n.id)==null){dao.save(s,n.id,Kind.TASK.name,encode(n));next=n}}}};Reminders.update(getApplication(),s,t);next?.let{Reminders.update(getApplication(),s,it);message.value="Siguiente ocurrencia creada: ${it.due}"} } }
    fun logFocus(taskId:String,minutes:Int){val uid=scope.value;run{require(minutes in 1..1440);db.withTransaction{val row=dao.get(uid,taskId)?.takeUnless{it.deleted}?:error("La tarea ya no existe");val t=decode<Task>(row.payload);dao.save(uid,t.id,Kind.TASK.name,encode(t.copy(actualMinutes=(t.actualMinutes+minutes).coerceAtMost(100000))))};message.value="Registrados $minutes minutos de concentración"}}
    fun save(h: Home) { val uid=scope.value;run { dao.save(uid,h.id,Kind.HOME.name,encode(h)) } }
    fun save(l: ShoppingList) { val uid=scope.value;run { dao.save(uid,l.id,Kind.SHOPPING_LIST.name,encode(l)) } }
    fun saveShopping(items:List<Shopping>) { val uid=scope.value;run { db.withTransaction { items.forEach{dao.save(uid,it.id,Kind.SHOPPING.name,encode(it))} } } }
    fun save(f: Furniture) { val s=scope.value; run { dao.save(s,f.id,Kind.FURNITURE.name,encode(f)) } }
    fun save(i: Item) { val s=scope.value; run { dao.save(s,i.id,Kind.ITEM.name,encode(i)) } }
    fun save(g: Group) { val s=scope.value; run { dao.save(s,g.id,Kind.GROUP.name,encode(g)) } }
    fun commitPlan(proposed:List<Session>,replacing:List<Session>,updatedTask:Task?=null){val uid=scope.value;run{db.withTransaction{
        if(updatedTask!=null){require(updatedTask.title.isNotBlank()&&updatedTask.minutes in 1..100000);dao.save(uid,updatedTask.id,Kind.TASK.name,encode(updatedTask))}
        val rows=dao.all(uid).filterNot{it.deleted};val current=rows.filter{it.kind==Kind.SESSION.name}.map{decode<Session>(it.payload)};val tasks=rows.filter{it.kind==Kind.TASK.name}.map{decode<Task>(it.payload)}
        require(replacing.all{old->current.contains(old)&&!old.done&&old.start>System.currentTimeMillis()&&(updatedTask==null||old.taskId==updatedTask.id)}){"El calendario ha cambiado; recalcula antes de aceptar"}
        val busy=current.filterNot{it in replacing}.map{Interval(it.start,it.end)}.toMutableList();busy+=es.planidroid.app.sync.CalendarSync.events(getApplication(),uid).filter{it.busy}.map{Interval(it.start,it.end)}
        for(s in proposed.sortedBy{it.start}){val t=tasks.find{it.id==s.taskId}?:error("La tarea ya no existe");require(TaskConstraints.unmet(t,tasks).isEmpty()&&t.status !in listOf(Status.DONE,Status.ARCHIVED,Status.BLOCKED));require(s.start>=System.currentTimeMillis()&&s.end>s.start&&(t.due.isBlank()||java.time.Instant.ofEpochMilli(s.end-1).atZone(java.time.ZoneId.systemDefault()).toLocalDate().let{d->if(t.planHints.finishBeforeDeadline)d<java.time.LocalDate.parse(t.due)else d<=java.time.LocalDate.parse(t.due)})&&busy.none{it.start<s.end&&it.end>s.start}){"Conflicto de horario; vuelve a calcular"};busy+=Interval(s.start,s.end)}
        replacing.forEach{dao.remove(uid,it.id)};proposed.forEach{dao.save(uid,it.id,Kind.SESSION.name,encode(it))}
    };updatedTask?.let{Reminders.update(getApplication(),uid,it)};replacing.forEach{SessionReminders.cancel(getApplication(),uid,it.id)};proposed.forEach{SessionReminders.schedule(getApplication(),uid,it)};message.value="Plan guardado"}}
    fun save(sessions: List<Session>) { val s=scope.value; run { db.withTransaction { sessions.forEach { v->val old=dao.get(s,v.id)?.takeUnless{it.deleted}?.let{decode<Session>(it.payload)};if(old==null||old.start!=v.start||old.end!=v.end){require(v.end>v.start&&dao.all(s).none{it.kind==Kind.SESSION.name&&!it.deleted&&it.id!=v.id&&decode<Session>(it.payload).let{r->r.start<v.end&&r.end>v.start}}&&es.planidroid.app.sync.CalendarSync.events(getApplication(),s).none{it.busy&&it.start<v.end&&it.end>v.start}){"Conflicto de horario"}};dao.save(s,v.id,Kind.SESSION.name,encode(v)) } };sessions.forEach{SessionReminders.schedule(getApplication(),s,it)} } }
    fun save(s: Shopping) { val uid=scope.value; run { dao.save(uid,s.id,Kind.SHOPPING.name,encode(s)) } }
    fun buy(id:String) { val uid=scope.value; run { db.withTransaction {
        val row=dao.get(uid,id)?.takeUnless{it.deleted} ?: return@withTransaction
        val s=decode<Shopping>(row.payload)
        val i=dao.get(uid,s.itemId)?.takeUnless{it.deleted}?.let{decode<Item>(it.payload)}
        val n=ShoppingLogic.needed(s,i); if(n<=0)return@withTransaction
        val p=Purchase(shoppingId=s.id,name=s.name,quantity=n,unit=s.unit,routine=s.routine,itemId=i?.id.orEmpty(),stockBefore=i?.amount,stockAfter=i?.amount?.plus(n),icon=s.icon,listId=s.listId,quantitySpecified=s.quantitySpecified||(i!=null&&s.routine&&i.amount<s.minimum))
        if(i!=null)dao.save(uid,i.id,Kind.ITEM.name,encode(i.copy(amount=i.amount+n,quantity=amountText(i.amount+n))))
        dao.save(uid,p.id,Kind.PURCHASE.name,encode(p));dao.save(uid,s.id,Kind.SHOPPING.name,encode(s.copy(pending=false)))
    } } }
    fun repeatPurchase(p:Purchase,undo:Boolean) { val uid=scope.value; run { db.withTransaction {
        val current=dao.get(uid,p.id)?.takeUnless{it.deleted}?.let{decode<Purchase>(it.payload)} ?: return@withTransaction
        if(undo && current.undone)return@withTransaction
        val old=dao.get(uid,current.shoppingId)?.takeUnless{it.deleted}?.let{decode<Shopping>(it.payload)}
        val s=old?.copy(pending=true,quantity=current.quantity,quantitySpecified=current.quantitySpecified) ?: Shopping(name=current.name,quantity=current.quantity,unit=current.unit,routine=current.routine,icon=current.icon,listId=current.listId,quantitySpecified=current.quantitySpecified)
        dao.save(uid,s.id,Kind.SHOPPING.name,encode(s))
        if(undo){ val item=dao.get(uid,current.itemId)?.takeUnless{it.deleted}?.let{decode<Item>(it.payload)}
            if(ShoppingLogic.canUndo(current,item) && item!=null){ val n=current.stockBefore!!;dao.save(uid,item.id,Kind.ITEM.name,encode(item.copy(amount=n,quantity=amountText(n)))) }
            else if(current.stockAfter!=null)message.value="Stock modificado desde la compra: se conserva. Revisa la cantidad en inventario."
            dao.save(uid,current.id,Kind.PURCHASE.name,encode(current.copy(undone=true)))
        }
    } } }
    fun delete(id: String,kind: Kind) { val s=scope.value; run { db.withTransaction {
        val all=dao.all(s).filterNot { it.deleted }
        when(kind) {
            Kind.TASK -> {all.filter { it.kind==Kind.SESSION.name && decode<Session>(it.payload).taskId==id }.forEach { dao.remove(s,it.id) };all.filter{it.kind==Kind.TASK.name&&it.id!=id}.map{decode<Task>(it.payload)}.filter{id in it.dependencies}.forEach{dao.save(s,it.id,Kind.TASK.name,encode(it.copy(dependencies=it.dependencies-id)))}}
            Kind.FURNITURE -> all.filter { it.kind==Kind.ITEM.name && decode<Item>(it.payload).furnitureId==id }.forEach { dao.remove(s,it.id) }
            Kind.HOME -> {require(all.none{it.kind==Kind.FURNITURE.name&&decode<Furniture>(it.payload).homeId==id}){"Mueve los muebles antes de eliminar el hogar"}}
            Kind.SHOPPING_LIST -> {require(all.none{(it.kind==Kind.SHOPPING.name&&decode<Shopping>(it.payload).listId==id)||(it.kind==Kind.PURCHASE.name&&decode<Purchase>(it.payload).listId==id)}){"La lista contiene productos o historial"}}
            Kind.GROUP -> all.filter { it.kind==Kind.TASK.name }.map { decode<Task>(it.payload) }.filter { it.groupId==id }.forEach { dao.save(s,it.id,Kind.TASK.name,encode(it.copy(groupId=""))) }
            else -> Unit
        }; dao.remove(s,id)
    };SessionReminders.refresh(getApplication(),s) } }
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
    fun login(context: Context) { run { busy.value=true; try { Cloud.login(context); val uid=auth?.currentUser?.uid ?: return@run; _scope.value=uid; Cloud.schedule(getApplication(),uid); sync() } finally { busy.value=false } } }
    fun logout() { auth?.signOut(); syncState.value="Sin sincronizar" }
    fun sync() { val s=scope.value; if(s=="local") return; run { if(busy.value) { /* login may already be in progress */ }; syncState.value="Sincronizando…"; try { Cloud.sync(getApplication(),s); SessionReminders.refresh(getApplication(),s);syncState.value="Sincronizado" } catch(e: CancellationException) { throw e } catch(e:Exception) { syncState.value="Error o conflicto"; message.value=e.localizedMessage ?: "Sin conexión" } } }
    override fun onCleared() { auth?.removeAuthStateListener(listener); super.onCleared() }
}
