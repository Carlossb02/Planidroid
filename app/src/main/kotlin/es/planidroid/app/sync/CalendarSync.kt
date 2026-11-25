package es.planidroid.app.sync

import android.accounts.Account
import android.content.Context
import android.content.SharedPreferences
import androidx.work.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import es.planidroid.app.data.PlanidroidDb
import es.planidroid.app.domain.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.*
import java.util.concurrent.TimeUnit

object CalendarSync {
    private val mutex=Mutex()
    const val oauthScope="https://www.googleapis.com/auth/calendar.events"
    fun request(email:String)=AuthorizationRequest.builder().setAccount(Account(email,"com.google")).setRequestedScopes(listOf(Scope(oauthScope))).build()
    fun explain(e:Throwable):String=when(e){
        is CalendarHttpException->when(e.status){
            401->"Calendar: autorización caducada. Vuelve a conectar."
            403->"Calendar: acceso denegado ("+(e.reason.ifBlank{"403"})+"). Comprueba permisos de eventos, API habilitada y cuenta de prueba."
            429->"Calendar: límite de consultas. Inténtalo más tarde."
            else->"Calendar HTTP "+e.status+" "+e.reason
        }
        is com.google.android.gms.common.api.ApiException->"Google OAuth: código "+e.statusCode+". Comprueba cliente Android, SHA-1 y usuario de prueba."
        else->e.localizedMessage?:"No se pudo conectar a Calendar"
    }
    suspend fun verifyConnection(ctx:Context,uid:String){
        if(mode(ctx,uid)=="OFF")return
        try{
            val user=Cloud.auth(ctx)?.currentUser?:error("Sin cuenta")
            check(user.uid==uid)
            val a=Identity.getAuthorizationClient(ctx).authorize(request(user.email?:error("Sin correo"))).await()
            val token=a.accessToken
            check(!a.hasResolution()&&oauthScope in a.grantedScopes&&!token.isNullOrBlank())
            GoogleCalendarGateway{requireNotNull(token)}.verify()
            ServiceHealth.mark(ctx,uid,"calendar",true)
        }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){ServiceHealth.mark(ctx,uid,"calendar",false)}
    }
    private fun prefs(ctx:Context,uid:String)=ctx.getSharedPreferences("calendar-$uid",Context.MODE_PRIVATE)
    fun mode(ctx:Context,uid:String)=prefs(ctx,uid).getString("mode","OFF")?:"OFF"
    fun setMode(ctx:Context,uid:String,value:String){prefs(ctx,uid).edit().putString("mode",value).apply();val wm=WorkManager.getInstance(ctx);if(value=="AUTOMATIC_ALL"){val req=PeriodicWorkRequestBuilder<CalendarWorker>(6,TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setInputData(workDataOf("uid" to uid)).build();wm.enqueueUniquePeriodicWork("calendar-$uid",ExistingPeriodicWorkPolicy.UPDATE,req)}else wm.cancelUniqueWork("calendar-$uid");if(value=="OFF"){prefs(ctx,uid).edit().remove("events").apply();ServiceHealth.mark(ctx,uid,"calendar",false)}}
    fun events(ctx:Context,uid:String)=runCatching{decode<List<ExternalEvent>>(prefs(ctx,uid).getString("events","[]")!!)}.getOrDefault(emptyList())
    fun observe(ctx:Context,uid:String)=callbackFlow{val p=prefs(ctx,uid);val l=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key=="events")trySend(events(ctx,uid))};p.registerOnSharedPreferenceChangeListener(l);trySend(events(ctx,uid));awaitClose{p.unregisterOnSharedPreferenceChangeListener(l)}}
    fun message(ctx:Context,uid:String)=prefs(ctx,uid).getString("message","").orEmpty()
    private fun stamp(event:JSONObject):CalendarStamp?{fun date(name:String):Long{val v=event.getJSONObject(name);return if(v.has("dateTime"))Instant.parse(v.getString("dateTime")).toEpochMilli()else LocalDate.parse(v.getString("date")).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()};return runCatching{CalendarStamp(date("start"),date("end"),event.optString("summary","Evento"))}.getOrNull()}
    private fun ownership(e:JSONObject)=e.optJSONObject("extendedProperties")?.optJSONObject("private")?.optString("planidroidSessionId").orEmpty()
    suspend fun sync(ctx:Context,uid:String,token:String,selected:Set<String>?=null):String=mutex.withLock{
        val user=Cloud.auth(ctx)?.currentUser;check(user?.uid==uid && uid!="local"){"Cuenta no disponible"};check(mode(ctx,uid)!="OFF"){"Activa Calendar primero"}
        val gateway=GoogleCalendarGateway{token};val p=prefs(ctx,uid);val dao=PlanidroidDb.get(ctx).records();val rows=dao.all(uid);val tasks=rows.filter{!it.deleted&&it.kind==Kind.TASK.name}.associate{it.id to decode<Task>(it.payload)}
        val sessionRows=rows.filter{it.kind==Kind.SESSION.name};val links=runCatching{decode<List<CalendarLink>>(p.getString("links","[]")!!)}.getOrDefault(emptyList()).associateBy{it.id}.toMutableMap();val issues=mutableListOf<String>();var pushed=0;var pulled=0
        fun storeLink(id:String,e:JSONObject){val v=stamp(e)?:error("Evento sin fechas");links[id]=CalendarLink(id,v,e.getString("etag"));p.edit().putString("links",encode(links.values.toList())).apply()}
        suspend fun remote(s:Session):JSONObject?=try{gateway.get("primary",gateway.eventId(s))}catch(e:CalendarHttpException){if(e.status in listOf(404,410))null else throw e}
        val now=System.currentTimeMillis();val from=LocalDate.now().minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant();val to=LocalDate.now().plusDays(366).atStartOfDay(ZoneId.systemDefault()).toInstant();val remoteEvents=gateway.list("primary",from,to);val conflicting=mutableSetOf<String>()
        fun overlapsRemote(s:Session)=remoteEvents.any{e->ownership(e)!=s.id&&e.optString("transparency")!="transparent"&&stamp(e)?.let{it.start<s.end&&it.end>s.start}==true}
        val candidates=sessionRows.filter{it.id in links || (!it.deleted && decode<Session>(it.payload).end>now && (selected==null || selected.contains(it.id)))}
        for(row in candidates){check(Cloud.auth(ctx)?.currentUser?.uid==uid&&mode(ctx,uid)!="OFF");val s=decode<Session>(row.payload);val base=links[s.id];val title=s.calendarTitle.ifBlank{tasks[s.taskId]?.title.orEmpty()};if(!row.deleted&&(title.isBlank()||s.done&&base==null))continue
            val local=CalendarStamp(s.start,s.end,title);val r=remote(s);val missing=r==null||r.optString("status")=="cancelled"
            if(row.deleted){if(!missing&&base!=null&&ownership(r!!)==s.id&&stamp(r)==base.stamp&&r.optString("etag")==base.etag){gateway.deleteOwned("primary",s,base.etag);pushed++}else if(!missing){issues+="Eliminación en conflicto: ${s.id.take(8)}";continue};links.remove(s.id);p.edit().putString("links",encode(links.values.toList())).apply();continue}
            if(missing){if(base!=null){issues+="$title: eliminado en Google; se conserva localmente";continue};if(r!=null){issues+="$title: ID cancelado, crea una nueva sesión para volver a publicar";continue};if(overlapsRemote(s)){issues+="$title: solapa un evento externo";continue};storeLink(s.id,gateway.insert("primary",s,title));pushed++;continue}
            if(ownership(r!!)!=s.id){issues+="$title: identificador ajeno";continue};val rs=stamp(r)?:continue
            when(CalendarMerge.decide(base?.stamp,local,rs)){
                Reconcile.SAME->storeLink(s.id,r)
                Reconcile.PUSH->{if(overlapsRemote(s)){issues+="$title: solapa un evento externo";conflicting+=s.id;continue};storeLink(s.id,gateway.updateOwned("primary",s,title,r.getString("etag")));pushed++}
                Reconcile.PULL->{if(s.done||rs.end<=rs.start||dao.all(uid).any{other->other.kind==Kind.SESSION.name&&!other.deleted&&other.id!=s.id&&OverlapPolicy.conflict(s.copy(start=rs.start,end=rs.end),decode<Session>(other.payload),tasks.values)}){issues+="$title: cambio remoto incompatible";conflicting+=s.id;continue};val current=dao.get(uid,s.id);if(current?.revision!=row.revision){issues+="$title: edición local concurrente";conflicting+=s.id;continue};dao.save(uid,s.id,Kind.SESSION.name,encode(s.copy(start=rs.start,end=rs.end,calendarTitle=if(rs.title==tasks[s.taskId]?.title)"" else rs.title)));es.planidroid.app.data.SessionReminders.schedule(ctx,uid,s.copy(start=rs.start,end=rs.end));storeLink(s.id,r);pulled++}
                Reconcile.CONFLICT->{issues+="$title: editado en ambos calendarios, no se sobrescribe";conflicting+=s.id}
            }
        }
        val currentIds=dao.all(uid).filter{!it.deleted&&it.kind==Kind.SESSION.name}.map{it.id}.toSet()
        val events=gateway.list("primary",from,to).mapNotNull{e->val st=stamp(e)?:return@mapNotNull null;if((ownership(e) in currentIds && ownership(e) !in conflicting) || st.end<=st.start)return@mapNotNull null;ExternalEvent(e.getString("id"),st.title,st.start,st.end,e.optString("transparency")!="transparent")}
        check(Cloud.auth(ctx)?.currentUser?.uid==uid);val msg="Google: $pushed enviados, $pulled actualizados, ${events.size} externos"+if(issues.isEmpty())"." else ". ${issues.size} conflictos:\n"+issues.joinToString("\n");p.edit().putString("events",encode(events)).putString("message",msg).apply();ServiceHealth.mark(ctx,uid,"calendar",true);msg
    }
}
class CalendarWorker(ctx:Context,p:WorkerParameters):CoroutineWorker(ctx,p){override suspend fun doWork():Result{val uid=inputData.getString("uid")?:return Result.failure();val user=Cloud.auth(applicationContext)?.currentUser?:return Result.success();if(user.uid!=uid||CalendarSync.mode(applicationContext,uid)!="AUTOMATIC_ALL")return Result.success();val email=user.email?:return Result.success();return try{val auth=Identity.getAuthorizationClient(applicationContext).authorize(CalendarSync.request(email)).await();val token=auth.accessToken;if(auth.hasResolution()||token.isNullOrBlank()||CalendarSync.oauthScope !in auth.grantedScopes){ServiceHealth.mark(applicationContext,uid,"calendar",false);return Result.success()};CalendarSync.sync(applicationContext,uid,token);Result.success()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){ServiceHealth.mark(applicationContext,uid,"calendar",false);Result.success()}}}
