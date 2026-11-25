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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable fun CalendarSettings(model:PlanidroidModel,uid:String){
    val ctx=LocalContext.current;val scope=rememberCoroutineScope();val rows by model.rows.collectAsStateWithLifecycle();val sessions=remember(rows,uid){rows.filter{it.scope==uid&&it.kind==Kind.SESSION.name}.mapNotNull{runCatching{decode<Session>(it.payload)}.getOrNull()}.filterNot{it.done}};val tasks=remember(rows,uid){rows.filter{it.scope==uid&&it.kind==Kind.TASK.name}.mapNotNull{runCatching{decode<Task>(it.payload)}.getOrNull()}}
    var mode by remember(uid){mutableStateOf(CalendarSync.mode(ctx,uid))};var status by remember(uid){mutableStateOf(CalendarSync.message(ctx,uid))};var selected by remember(uid){mutableStateOf(emptySet<String>())};var busy by remember(uid){mutableStateOf(false)};var confirm by remember{mutableStateOf(false)}
    fun sync(token:String?){if(token.isNullOrBlank()){status="No se obtuvo autorización";busy=false;return};scope.launch{try{status=CalendarSync.sync(ctx,uid,token,if(mode=="MANUAL")selected else null)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){status=e.localizedMessage?:"Error de Calendar"}finally{busy=false}}}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){result->if(result.resultCode==Activity.RESULT_OK){runCatching{Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(result.data).accessToken}.onSuccess{sync(it)}.onFailure{busy=false;status="No se pudo autorizar Calendar"}}else{busy=false;status="Autorización cancelada"}}
    GlassCard(tint=Blue){Text("Google Calendar",style=MaterialTheme.typography.titleLarge);Text("Sincronización del calendario principal de tu cuenta, con autorización independiente. Solo se publican títulos y horarios; no se envían notas ni contactos.");if(uid=="local"){Text("Inicia sesión en Google para activar esta función.");return@GlassCard};Choice("Modo",listOf("OFF","MANUAL","AUTOMATIC_ALL"),mode,{when(it){"OFF"->"Desactivado";"MANUAL"->"Manual / selectivo";else->"Automático · todas las sesiones"}}){v->if(v=="AUTOMATIC_ALL")confirm=true else {mode=v;CalendarSync.setMode(ctx,uid,v)}};if(mode=="MANUAL"){Text("Selecciona sesiones nuevas para exportar. Las vinculadas se revisan siempre, incluso si desmarcas.");Column(Modifier.heightIn(max=230.dp).verticalScroll(rememberScrollState())){sessions.sortedBy{it.start}.forEach{s->Row{Checkbox(s.id in selected,{v->selected=if(v)selected+s.id else selected-s.id},enabled=!busy);Column{Text(tasks.find{it.id==s.taskId}?.title?:"Sesión");Text("${sessionDate(s.start)} ${time(s.start)}",style=MaterialTheme.typography.bodySmall)}}}}};Button(enabled=mode!="OFF"&&!busy,onClick={busy=true;scope.launch{try{val a=Identity.getAuthorizationClient(ctx).authorize(CalendarSync.request(model.email())).await();if(a.hasResolution()){val pending=a.pendingIntent?:error("No hay solicitud de autorización");launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())}else sync(a.accessToken)}catch(e:Exception){busy=false;status=e.localizedMessage?:"Error de autorización"}}}){Text("Autorizar y sincronizar")};if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());if(status.isNotBlank())Text(status);Text("Importación: desde 30 días atrás hasta 366 días adelante. Los eventos externos bloquean disponibilidad y no se modifican. La sincronización automática es periódica y requiere permiso vigente.",style=MaterialTheme.typography.bodySmall)}
    if(confirm)Confirm("Activar sincronización automática","Se enviarán títulos y horarios de todas las sesiones futuras y pendientes. Google podrá recibir estos datos.",{confirm=false},{mode="AUTOMATIC_ALL";CalendarSync.setMode(ctx,uid,mode)})
}
