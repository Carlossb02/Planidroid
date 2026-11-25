package es.planidroid.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import es.planidroid.app.sync.SharedCloud
import es.planidroid.app.sync.PendingSharedEdit
@Composable fun SharedPendingSettings(uid:String){
    val ctx=LocalContext.current;val api=remember(uid){runCatching{SharedCloud(ctx)}.getOrNull()}?:return
    var edits by remember(uid){mutableStateOf(api.pendingEdits())}
    var remove by remember(uid){mutableStateOf<PendingSharedEdit?>(null)}
    GlassCard{
        Text("Cambios compartidos pendientes")
        Text("Si otro usuario ha cambiado el contenido, se conserva tu propuesta sin sobrescribir la nube. Puedes copiarla antes de descartarla.")
        TextButton(onClick={edits=api.pendingEdits()}){Text("Actualizar")}
        edits.forEach{edit->Text(edit.title);TextButton(onClick={ctx.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText(edit.title,edit.body))}){Text("Copiar propuesta")};TextButton(onClick={remove=edit}){Text("Descartar cambio")}}
        if(edits.isEmpty())Text("No hay cambios pendientes")
    }
    remove?.let{edit->Confirm("Descartar cambio","Se eliminará únicamente la propuesta pendiente de este dispositivo.",{remove=null},{api.discardPending(edit.id);edits=api.pendingEdits()})}
}
