package es.planidroid.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.identity.Identity
import es.planidroid.app.domain.*
import es.planidroid.app.sync.CalendarSync
import es.planidroid.app.sync.ServiceHealth
import com.google.android.gms.auth.api.identity.AuthorizationResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable fun CalendarSettings(model:PlanidroidModel,uid:String){
    val online=LocalOnlineAccess.current;val ctx=LocalContext.current;val scope=rememberCoroutineScope();val rows by model.rows.collectAsStateWithLifecycle();val sessions=remember(rows,uid){rows.filter{it.scope==uid&&it.kind==Kind.SESSION.name}.mapNotNull{runCatching{decode<Session>(it.payload)}.getOrNull()}.filterNot{it.done}};val tasks=remember(rows,uid){rows.filter{it.scope==uid&&it.kind==Kind.TASK.name}.mapNotNull{runCatching{decode<Task>(it.payload)}.getOrNull()}}
    var mode by remember(uid){mutableStateOf(CalendarSync.mode(ctx,uid))};var status by remember(uid){mutableStateOf(CalendarSync.message(ctx,uid))};var selected by remember(uid){mutableStateOf(emptySet<String>())};var busy by remember(uid){mutableStateOf(false)};var confirm by remember{mutableStateOf(false)}
    fun fail(message:String){status=message;busy=false;ServiceHealth.mark(ctx,uid,"calendar",false)}
    fun sync(result:AuthorizationResult){
        if(CalendarSync.oauthScope !in result.grantedScopes){fail("Google no concedió el permiso de eventos. Vuelve a conectar y marca el permiso de Calendar.");return}
        val token=result.accessToken
        if(token.isNullOrBlank()){fail("No se obtuvo un token de Calendar");return}
        scope.launch{try{
            if(mode=="OFF"){mode="MANUAL";CalendarSync.setMode(ctx,uid,mode)}
            status=CalendarSync.sync(ctx,uid,token,if(mode=="MANUAL")selected else null)
            ServiceHealth.mark(ctx,uid,"calendar",true)
        }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){fail(CalendarSync.explain(e))}finally{busy=false}}
    }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){result->if(result.resultCode==Activity.RESULT_OK){runCatching{Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(result.data)}.onSuccess{sync(it)}.onFailure{fail(CalendarSync.explain(it))}}else{fail("Autorización cancelada")}}
    GlassCard(tint=Blue){Text("Google Calendar",style=MaterialTheme.typography.titleLarge);if(uid=="local"){OutlinedButton(onClick={online("sincronizar Google Calendar")}){Text("Conectar Google Calendar")};return@GlassCard};Choice("Modo",listOf("OFF","MANUAL","AUTOMATIC_ALL"),mode,{when(it){"OFF"->"Desactivado";"MANUAL"->"Manual / selectivo";else->"Automático · todas las sesiones"}}){v->if(v=="AUTOMATIC_ALL")confirm=true else {mode=v;CalendarSync.setMode(ctx,uid,v)}};if(mode=="MANUAL"){Text("Selecciona sesiones nuevas para exportar. Las vinculadas se revisan siempre, incluso si desmarcas.");Column(Modifier.heightIn(max=230.dp).verticalScroll(rememberScrollState())){sessions.sortedBy{it.start}.forEach{s->Row{Checkbox(s.id in selected,{v->selected=if(v)selected+s.id else selected-s.id},enabled=!busy);Column{Text(if(s.isBreak)"Descanso" else tasks.find{it.id==s.taskId}?.title?:"Sesión");Text("${sessionDate(s.start)} ${time(s.start)}",style=MaterialTheme.typography.bodySmall)}}}}};Button(enabled=!busy,onClick={busy=true;scope.launch{try{val a=Identity.getAuthorizationClient(ctx).authorize(CalendarSync.request(model.email())).await();if(a.hasResolution()){val pending=a.pendingIntent?:error("No hay solicitud de autorización");launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())}else sync(a)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){fail(CalendarSync.explain(e))}}}){Text("Autorizar y sincronizar")};if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());if(status.isNotBlank())Text(status);}
    if(confirm)Confirm("Activar sincronización automática","Se enviarán títulos y horarios de todas las sesiones futuras y pendientes. Google podrá recibir estos datos.",{confirm=false},{mode="AUTOMATIC_ALL";CalendarSync.setMode(ctx,uid,mode)})
}
