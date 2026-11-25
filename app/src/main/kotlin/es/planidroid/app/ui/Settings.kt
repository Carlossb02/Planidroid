package es.planidroid.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.planidroid.app.data.BackupIO
import es.planidroid.app.data.PortableBackup
import es.planidroid.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun SettingsScreen(model:PlanidroidModel,scope:String,theme:String,glass:Boolean){
    val online=LocalOnlineAccess.current;val ctx=LocalContext.current;val busy by model.busy.collectAsStateWithLifecycle();val state by model.syncState.collectAsStateWithLifecycle();val autoSync by model.autoSyncEnabled.collectAsStateWithLifecycle();var backup by remember(scope){mutableStateOf<Backup?>(null)};var imported by remember(scope){mutableStateOf<PortableBackup.Imported?>(null)};var confirmLogout by remember{mutableStateOf(false)};var replace by remember(scope){mutableStateOf<Backup?>(null)};var clear by remember{mutableStateOf(false)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)model.run{val json=model.backup();withContext(Dispatchers.IO){val stream=ctx.contentResolver.openOutputStream(uri) ?: error("No se pudo abrir el archivo");stream.use{it.write(json.toByteArray(Charsets.UTF_8))}};model.message.value="Copia exportada. Contiene información privada."}}
    val zipExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->if(uri!=null)model.run{val r=PortableBackup.write(ctx,decode<Backup>(model.backup()),uri);model.message.value="ZIP exportado: ${r.included} archivos; ${r.missing} no disponibles."}}
    val zipImport=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)model.run{val r=PortableBackup.read(ctx,uri);imported=r;backup=r.backup}}
    val recovery=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)model.run{val json=model.lastRecovery();withContext(Dispatchers.IO){ctx.contentResolver.openOutputStream(uri)?.use{it.write(json.toByteArray(Charsets.UTF_8))}?:error("No se pudo guardar")};model.message.value="Copia de recuperación exportada"}}
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)model.run{val text=withContext(Dispatchers.IO){val stream=ctx.contentResolver.openInputStream(uri) ?: error("Archivo no disponible");stream.bufferedReader().use{reader->val out=StringBuilder();val buf=CharArray(8192);while(true){val n=reader.read(buf);if(n<0)break;out.append(buf,0,n);require(out.length<=8_000_000){"Copia demasiado grande"}};out.toString()}};imported=null;backup=PortableBackup.stripReferences(BackupIO.validate(text))}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Section("Ajustes","Controla tus datos y la apariencia.")
        GlassCard{Text("Cuenta y sincronización",style=MaterialTheme.typography.titleLarge);if(scope=="local"){Text("Modo local. Los datos de una cuenta Google y los locales se mantienen separados. Exporta e importa si quieres trasladarlos.");Button(enabled=!busy,onClick={online("conectar tu cuenta")}){Text("Iniciar sesión con Google")}}else{Text(model.email());Text("Estado: $state");Text("UID: $scope",style=MaterialTheme.typography.bodySmall);TextButton(onClick={val clipboard=ctx.getSystemService(android.content.ClipboardManager::class.java);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("UID",scope))}){Text("Copiar UID para autorización")};Text("La nube requiere aprobación manual. Por defecto solo sincroniza cuando lo solicitas.",style=MaterialTheme.typography.bodySmall);Button(enabled=!busy,onClick={model.sync()}){Text("Comprobar acceso y sincronizar")};TextButton(enabled=!busy,onClick={model.setAutomaticSync(!autoSync)}){Text(if(autoSync)"Desactivar sincronización automática" else "Activar sincronización cada 6 horas")};TextButton(onClick={confirmLogout=true}){Text("Cerrar sesión")}};if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());Text("Google Sign-In no autoriza Calendar ni Drive. La nube no incluye documentos ni contactos locales.",style=MaterialTheme.typography.bodySmall)}
        CalendarSettings(model,scope)
        ConflictSettings(model,scope)
        GlassCard{Text("Apariencia",style=MaterialTheme.typography.titleLarge);Choice("Tema",listOf("Sistema","Claro","Oscuro"),theme,{it}){model.setting(it,glass)};Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Tarjetas con más color");Text("Material 3 con un tinte más intenso. Sin desenfoque dinámico.",style=MaterialTheme.typography.bodySmall)};Switch(glass,{model.setting(theme,it)})}}
        GlassCard{Text("Copias de seguridad JSON",style=MaterialTheme.typography.titleLarge);Text("Incluye tareas, proyectos, inventario, compra, historial y sesiones. JSON conserva metadatos; ZIP incluye fotografías y documentos accesibles. No incluye credenciales. Guarda las copias en un lugar seguro.");Button(onClick={export.launch("Planidroid-backup.json")}){Text("Exportar copia")};OutlinedButton(onClick={restore.launch(arrayOf("application/json","text/plain","application/octet-stream"))}){Text("Importar JSON")};OutlinedButton(onClick={zipExport.launch("Planidroid-backup.zip")}){Text("Exportar ZIP con archivos")};OutlinedButton(onClick={zipImport.launch(arrayOf("application/zip","application/x-zip-compressed","application/octet-stream"))}){Text("Importar ZIP")}}
        GlassCard{Text("Recuperación y datos locales",style=MaterialTheme.typography.titleLarge);Text("Las restauraciones destructivas conservan una copia de seguridad previa en el teléfono. No modifican ninguna cuenta Google.");OutlinedButton(onClick={recovery.launch("Planidroid-recuperacion.json")}){Text("Exportar última recuperación")};if(scope=="local")TextButton(onClick={clear=true}){Text("Vaciar espacio local")}}
        NotificationSettings(model,scope)
        AiSettings(scope)
        GlassCard{Text("Acerca de",style=MaterialTheme.typography.titleLarge);Text("Planidroid ${es.planidroid.app.BuildConfig.VERSION_NAME} · Kotlin / Material 3");Text("Sin telemetría por defecto. Documentación y licencias de dependencias en el proyecto.")}
    }
    backup?.let{b->AlertDialog(onDismissRequest={backup=null;imported?.directory?.deleteRecursively();imported=null},title={Text("Revisar copia")},text={Column{Text("${b.entries.size} registros · esquema ${b.schemaVersion} · ${imported?.count?:0} archivos");Kind.entries.forEach{k->Text("${k.name}: ${b.entries.count{it.kind==k}}")};Text("Combinar conserva IDs existentes. Como nuevos crea IDs independientes. El ZIP conserva los archivos importados.");if(scope=="local")TextButton(onClick={replace=b;backup=null;imported=null}){Text("Reemplazar datos locales…")}}},confirmButton={TextButton(onClick={model.restore(b,false,imported!=null);backup=null;imported=null}){Text("Combinar")}},dismissButton={TextButton(onClick={model.restore(b,true,imported!=null);backup=null;imported=null}){Text("Como nuevos")}})}
    replace?.let{b->Confirm("Reemplazar todos los datos locales","Se guardará una copia de seguridad previa y después se sustituirán tus tareas, sesiones, muebles y compras. Esta acción no modifica la nube.",{replace=null},{model.restoreLocalReplace(b)})}
    if(clear)Confirm("Vaciar datos locales","Se guardará una copia de recuperación y se eliminará el contenido del espacio local. No se modifica Google.",{clear=false},{model.clearLocal()})
    if(confirmLogout)Confirm("Cerrar sesión","Los datos permanecen en el espacio privado de esa cuenta en este dispositivo.",{confirmLogout=false},{model.logout()})
}
