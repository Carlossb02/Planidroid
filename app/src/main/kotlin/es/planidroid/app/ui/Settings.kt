package es.planidroid.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.planidroid.app.sync.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable private fun ServiceRow(name:String,ok:Boolean,detail:String="",click:(()->Unit)?=null){
    Row(Modifier.fillMaxWidth().then(if(click!=null)Modifier.clickable(onClick=click)else Modifier).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){Text(name,style=MaterialTheme.typography.titleMedium);if(detail.isNotBlank())Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Icon(if(ok)Icons.Default.CheckCircle else Icons.Default.Cancel,if(ok)"Funcionando" else "No verificado o no disponible",tint=if(ok)Color(0xFF26834A)else MaterialTheme.colorScheme.error)
    }
}

@Composable fun SettingsScreen(model:PlanidroidModel,uid:String){
    val ctx=LocalContext.current;val online=LocalOnlineAccess.current;val coroutine=rememberCoroutineScope()
    val busy by model.busy.collectAsStateWithLifecycle()
    val approval by remember(uid){Cloud.observeApproval(ctx,uid)}.collectAsStateWithLifecycle(initialValue=null)
    val calendar by remember(uid){ServiceHealth.observe(ctx,uid,"calendar")}.collectAsStateWithLifecycle(initialValue=false)
    val gemini by remember(uid){ServiceHealth.observe(ctx,uid,"gemini")}.collectAsStateWithLifecycle(initialValue=false)
    var dbOk by remember(uid){mutableStateOf(false)}
    var calendarOpen by remember(uid){mutableStateOf(false)}
    var logout by remember(uid){mutableStateOf(false)}
    var testBusy by remember(uid){mutableStateOf(false)}
    var detail by remember(uid){mutableStateOf("")}
    var syncOpen by remember(uid){mutableStateOf(false)}
    val auto by model.autoSyncEnabled.collectAsStateWithLifecycle()
    val sync by model.syncState.collectAsStateWithLifecycle()
    LaunchedEffect(uid,approval){
        dbOk=false
        if(approval==true)try{Cloud.verifyDatabase(ctx,uid);dbOk=true}catch(e:CancellationException){throw e}catch(_:Exception){}
    }
    LaunchedEffect(sync){if(sync=="Sincronizado")dbOk=true}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        GlassCard{
            Row(verticalAlignment=Alignment.CenterVertically){ProfileAvatar(uid,onClick={});Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(if(uid=="local")"Sin cuenta" else model.email(),style=MaterialTheme.typography.titleMedium);if(uid!="local")Text(when(approval){true->"Autorizado";false->"No autorizado";null->"Sin verificar"},color=if(approval==true)Color(0xFF26834A)else MaterialTheme.colorScheme.error)}}
            if(uid=="local")Button(enabled=!busy,onClick={online("conectar tu cuenta")}){Text("Iniciar sesión con Google")}
            else TextButton(onClick={logout=true}){Text("Cerrar sesión")}
        }
        GlassCard{
            Text("Servicios",style=MaterialTheme.typography.titleLarge)
            ServiceRow("Google",uid!="local")
            HorizontalDivider()
            ServiceRow("Sincronización",uid!="local"&&approval==true&&dbOk,if(uid!="local")"Pulsa para sincronizar" else ""){if(uid=="local")online("sincronizar")else syncOpen=true}
            HorizontalDivider()
            ServiceRow("Google Calendar",uid!="local"&&calendar,if(uid!="local")"Pulsa para conectar o comprobar" else ""){if(uid=="local")online("conectar Google Calendar")else calendarOpen=true}
            HorizontalDivider()
            ServiceRow("Gemini",uid!="local"&&approval==true&&AiConfig.configured()&&gemini,if(testBusy)"Comprobando…" else if(!AiConfig.configured())"Pendiente de configuración" else "Pulsa para comprobar"){
                if(uid=="local")online("usar Gemini")
                else if(!testBusy){testBusy=true;detail="";coroutine.launch{try{AiConfig.provider(ctx,uid).answerWithUserContext("Responde únicamente OK.",emptyList());detail="Gemini ha respondido correctamente."}catch(e:CancellationException){throw e}catch(e:Exception){detail=e.localizedMessage?:"No se pudo conectar"}finally{testBusy=false}}}
            }
            if(detail.isNotBlank())Text(detail,style=MaterialTheme.typography.bodySmall)
        }
    }
    if(calendarOpen)AlertDialog(onDismissRequest={calendarOpen=false},title={Text("Google Calendar")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())){CalendarSettings(model,uid)}},confirmButton={TextButton(onClick={calendarOpen=false}){Text("Cerrar")}})
    if(syncOpen)AlertDialog(onDismissRequest={syncOpen=false},title={Text("Sincronización")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(sync);Button(enabled=!busy,onClick={model.sync()}){Text("Sincronizar ahora")};Row(verticalAlignment=Alignment.CenterVertically){Text("Automática cada 6 horas",Modifier.weight(1f));Switch(auto,{model.setAutomaticSync(it)})}}},confirmButton={TextButton(onClick={syncOpen=false}){Text("Cerrar")}})
    if(logout)Confirm("Cerrar sesión","Tus datos locales se conservan.",{logout=false},{model.logout()})
}
