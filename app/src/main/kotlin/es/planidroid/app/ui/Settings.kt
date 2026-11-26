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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    val approval by model.approval.collectAsStateWithLifecycle()
    val gemini by remember(uid){ServiceHealth.observe(ctx,uid,"gemini")}.collectAsStateWithLifecycle(initialValue=false)
    var logout by remember(uid){mutableStateOf(false)}
    var geminiOpen by remember(uid){mutableStateOf(false)}
    var keyConfigured by remember(uid){mutableStateOf(AiConfig.configured(ctx,uid))}
    var detail by remember(uid){mutableStateOf("")}
    var syncOpen by remember(uid){mutableStateOf(false)}
    val health by remember(uid){ServiceHealth.observe(ctx,uid,"firestore")}.collectAsStateWithLifecycle(initialValue=false)
    val sync by model.syncState.collectAsStateWithLifecycle()
    val pending by model.pendingCount.collectAsStateWithLifecycle()
    val preferences=LocalViewPreferences.current
    val theme by model.theme.collectAsStateWithLifecycle()
    val glass by model.glass.collectAsStateWithLifecycle()
    var notificationsOpen by remember(uid){mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        GlassCard{
            Row(verticalAlignment=Alignment.CenterVertically){ProfileAvatar(uid,onClick={});Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(if(uid=="local")"Sin cuenta" else model.email(),style=MaterialTheme.typography.titleMedium);if(uid!="local")Text(when(approval){true->"Autorizado";false->"No autorizado";null->"Autorizado · sin conexión"},color=if(approval==true)Color(0xFF26834A)else MaterialTheme.colorScheme.error)}}
            if(uid=="local")Button(enabled=!busy,onClick={online("conectar tu cuenta")}){Text("Iniciar sesión con Google")}
            else TextButton(onClick={logout=true}){Text("Cerrar sesión")}
        }
        Text("Personalización",style=MaterialTheme.typography.titleLarge)
        Choice("Tema",listOf("Sistema","Claro","Oscuro"),theme,{it}){model.setting(it,glass)}
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("Tarjetas con más color",Modifier.weight(1f))
            Switch(glass,{model.setting(theme,it)})
        }
        if(android.os.Build.VERSION.SDK_INT>=31)Row(verticalAlignment=Alignment.CenterVertically){
            Text("Colores del dispositivo",Modifier.weight(1f))
            Switch(preferences.systemColors,{model.viewSetting(booleanPreferencesKey("system_colors"),it)})
        }
        Choice("Vista inicial del calendario",listOf("Día","Semana","Mes","Agenda"),preferences.calendar,{it}){
            model.viewSetting(stringPreferencesKey("calendar_view"),it)
        }
        Choice("Vista inicial de los muebles",listOf("Visual","Lista"),preferences.inventory,{it}){
            model.viewSetting(stringPreferencesKey("inventory_view"),it)
        }
        Choice("Mostrar caducidades",listOf(3,7,14,30),preferences.expiryDays,{"Próximos "+it+" días"}){
            model.viewSetting(intPreferencesKey("inventory_expiry_days"),it)
        }
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("Planificar de lunes a viernes",Modifier.weight(1f))
            Switch(preferences.workdaysOnly,{model.viewSetting(booleanPreferencesKey("agenda_workdays_only"),it)})
        }
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("Reservar descansos por defecto",Modifier.weight(1f))
            Switch(preferences.reserveBreaks,{model.viewSetting(booleanPreferencesKey("agenda_reserve_breaks"),it)})
        }
        OutlinedButton(onClick={notificationsOpen=true},modifier=Modifier.fillMaxWidth()){Text("Configurar avisos")}
        GlassCard(tint=Blue){
            Text("Servicios",style=MaterialTheme.typography.titleLarge)
            ServiceRow("Google",uid!="local")
            HorizontalDivider()
            ServiceRow("Sincronización",uid!="local"&&approval==true&&health,if(pending>0)"$pending cambios pendientes" else if(approval==null)"Sin conexión / sin verificar" else if(health)"Última sincronización correcta" else "Sin sincronización reciente"){if(uid=="local")online("sincronizar")else syncOpen=true}
            HorizontalDivider()
            ServiceRow("Gemini",uid!="local"&&keyConfigured&&gemini,if(keyConfigured)if(gemini)"Última consulta correcta" else "Clave guardada · sin verificar" else "Opcional · conectar clave personal"){geminiOpen=true}
        }
    }
    if(notificationsOpen)AlertDialog(onDismissRequest={notificationsOpen=false},title={Text("Avisos")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())){NotificationSettings(model,uid)}},confirmButton={TextButton(onClick={notificationsOpen=false}){Text("Cerrar")}})
    if(geminiOpen)PersonalGeminiDialog(uid,{geminiOpen=false;keyConfigured=AiConfig.configured(ctx,uid)})
    if(syncOpen)AlertDialog(onDismissRequest={syncOpen=false},title={Text("Sincronización")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(sync);Button(enabled=!busy,onClick={model.sync()}){Text("Sincronizar ahora")};Row(verticalAlignment=Alignment.CenterVertically){Text("Sincronización automática · cambios pendientes al recuperar conexión",Modifier.weight(1f))}}},confirmButton={TextButton(onClick={syncOpen=false}){Text("Cerrar")}})
    if(logout)Confirm("Cerrar sesión","Tus datos locales se conservan. La clave personal de Gemini se elimina de este dispositivo.",{logout=false},{model.logout()})
}
