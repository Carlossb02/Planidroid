package es.planidroid.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.planidroid.app.sync.*
import es.planidroid.app.data.Record
import es.planidroid.app.domain.*
@Composable fun ConflictSettings(model:PlanidroidModel,uid:String){if(uid=="local")return;val ctx=LocalContext.current;var list by remember(uid){mutableStateOf(emptyList<CloudConflict>())};var show by remember(uid){mutableStateOf(false)};var message by remember(uid){mutableStateOf("")};var chosen by remember(uid){mutableStateOf<Pair<CloudConflict,Boolean>?>(null)};fun refresh(){model.run{list=ConflictResolution.list(ctx,uid);show=true;message=if(list.isEmpty())"No hay conflictos detectados" else "${list.size} registros en conflicto"}}
    fun summary(r:Record):String=if(r.deleted)"Eliminado" else runCatching{when(r.kind){Kind.TASK.name->decode<Task>(r.payload).let{"${it.title} · ${it.status.label} · ${it.due}"};Kind.SESSION.name->decode<Session>(r.payload).let{"${sessionDate(it.start)} ${time(it.start)}–${time(it.end)}"};Kind.FURNITURE.name->decode<Furniture>(r.payload).name;Kind.ITEM.name->decode<Item>(r.payload).let{"${it.name}: ${it.amount} ${it.unit}"};Kind.SHOPPING.name->decode<Shopping>(r.payload).name;Kind.GROUP.name->decode<Group>(r.payload).name;else->r.kind}}.getOrDefault(r.kind)
    GlassCard{Text("Conflictos de nube",style=MaterialTheme.typography.titleMedium);Text("Si dos dispositivos cambian el mismo dato, puedes conservar una versión después de compararlas. Las versiones se vuelven a verificar antes de aplicar.");OutlinedButton(onClick={refresh()}){Text("Buscar conflictos")};if(message.isNotBlank())Text(message)}
    if(show)AlertDialog(onDismissRequest={show=false},title={Text("Versiones en conflicto")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())){if(list.isEmpty())Text("No hay conflictos.");list.forEach{c->GlassCard{Text(c.local.kind,style=MaterialTheme.typography.labelSmall);Text("En este dispositivo: ${summary(c.local)}");Text("En la nube: ${summary(c.remote)}");TextButton(onClick={chosen=c to true;show=false}){Text("Conservar local")};TextButton(onClick={chosen=c to false;show=false}){Text("Conservar nube")}}}}},confirmButton={TextButton(onClick={show=false}){Text("Cerrar")}})
    chosen?.let{(c,local)->Confirm(if(local)"Conservar versión local" else "Conservar versión de nube",if(local)"Se intentará subir la versión local en la siguiente sincronización." else "Se guardará una copia de recuperación antes de reemplazar este registro.",{chosen=null},{model.run{ConflictResolution.resolve(ctx,uid,c,local);model.message.value="Conflicto resuelto";model.sync()}})}
}
