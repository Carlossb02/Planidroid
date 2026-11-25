package es.planidroid.app.ui
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.planidroid.app.data.*
@Composable fun NotificationSettings(model:PlanidroidModel,uid:String){val ctx=LocalContext.current;var sessions by remember(uid){mutableStateOf(NotificationPrefs.sessions(ctx,uid))};var lead by remember(uid){mutableIntStateOf(NotificationPrefs.lead(ctx,uid))};var daily by remember(uid){mutableStateOf(NotificationPrefs.daily(ctx,uid))};var from by remember(uid){mutableStateOf(NotificationPrefs.quietStart(ctx,uid))};var to by remember(uid){mutableStateOf(NotificationPrefs.quietEnd(ctx,uid))};val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){};GlassCard(tint=Blue){Text("Notificaciones",style=MaterialTheme.typography.titleLarge);Row{Switch(sessions,{sessions=it});Text(" Avisos de sesiones")};Choice("Antelación",listOf(0,5,10,15,30,60),lead,{"$it min"}){lead=it};Row{Switch(daily,{daily=it});Text(" Resumen diario aproximado a las 08:00")};Field(from,{from=it},"Silencio desde HH:mm (opcional)");Field(to,{to=it},"Silencio hasta HH:mm (opcional)");Button(onClick={model.run{NotificationPrefs.set(ctx,uid,sessions,lead,daily,from.trim(),to.trim());SessionReminders.refresh(ctx,uid);model.message.value="Preferencias guardadas"};if(Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("Guardar avisos")};Text("El sistema puede retrasar los recordatorios por ahorro de batería. Durante el silencio se omiten avisos.",style=MaterialTheme.typography.bodySmall)}}
