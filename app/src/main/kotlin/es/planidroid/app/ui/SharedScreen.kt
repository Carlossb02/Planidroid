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
import es.planidroid.app.domain.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable fun SharedScreen(uid:String){
    val online=LocalOnlineAccess.current;val ctx=LocalContext.current; val scope=rememberCoroutineScope(); val api=remember(uid){runCatching{SharedCloud(ctx)}.getOrNull()}
    var spaces by remember(uid){mutableStateOf(emptyList<SharedSpace>())};var invites by remember(uid){mutableStateOf(emptyList<Membership>())};var current by remember(uid){mutableStateOf<SharedSpace?>(null)};var members by remember(uid){mutableStateOf(emptyList<Membership>())};var comments by remember(uid){mutableStateOf(emptyList<SharedComment>())};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var create by remember{mutableStateOf(false)}
    suspend fun refresh(){val result=api!!.list();spaces=result.first.filter{it.kind in listOf("task","project","home","shopping")};invites=result.second;current=current?.let{old->spaces.find{it.id==old.id}};current?.let{s->comments=api.comments(s);members=if(s.role in listOf("owner","admin"))api.participants(s)else emptyList()}}
    fun work(action:suspend ()->Unit){if(api==null||busy)return;busy=true;scope.launch{try{error="";action();refresh()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.localizedMessage?:"Error de conexión"}finally{busy=false}}}
    // Explicit refresh instead of reading every time this screen opens.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Section("Compartido","Espacios explícitos, separados de tus tareas y calendario privados.")
        if(api==null){Text("Comparte solo lo que tú elijas.");Button(onClick={online("colaborar y acceder a contenido compartido")}){Text("Iniciar sesión")};return@Column}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        Row{OutlinedButton(enabled=!busy,onClick={work{}}){Text("Actualizar")};Spacer(Modifier.width(8.dp));Button(enabled=!busy,onClick={create=true}){Text("Nuevo espacio")}}
        invites.forEach{m->GlassCard(tint=Lilac){Text("Invitación · ${m.role}");Text("Espacio ${m.space.take(8)}");Row{TextButton(enabled=!busy,onClick={work{api.respond(m,true)}}){Text("Aceptar")};TextButton(enabled=!busy,onClick={work{api.respond(m,false)}}){Text("Rechazar")}}}}
        spaces.forEach{s->GlassCard(tint=Blue){Text(s.title,style=MaterialTheme.typography.titleLarge);Text("${s.kind} · ${s.role}");TextButton(enabled=!busy,onClick={work{current=s}}){Text("Abrir")}}}
        current?.let{s->key(s.id,s.version){var title by remember{mutableStateOf(s.title)};var content by remember{mutableStateOf(runCatching{decode<SharedContent>(s.body)}.getOrElse{SharedContent(notes=s.body)})};var target by remember{mutableStateOf("")};var role by remember{mutableStateOf("reader")};var comment by remember{mutableStateOf("")};val write=s.role!="reader";GlassCard{Text("Detalle · ${s.role}",style=MaterialTheme.typography.titleLarge);if(write){Field(title,{title=it},"Título");Field(content.notes,{content=content.copy(notes=it)},"Notas",false);SharedLines(s.kind,content,true){content=it};Button(enabled=!busy&&title.isNotBlank(),onClick={val payload=encode(content);work{api.update(s,title,payload)}}){Text("Guardar cambios")}}else{Text(s.title);Text(content.notes);SharedLines(s.kind,content,false){}};Text("Comentarios",style=MaterialTheme.typography.titleMedium);comments.forEach{Text("${it.author.take(8)} · ${it.body}")};if(write){Field(comment,{comment=it},"Comentario");TextButton(enabled=!busy&&comment.isNotBlank(),onClick={work{api.comment(s,comment);comment=""}}){Text("Enviar")}};if(s.role in listOf("owner","admin")){Text("Participantes",style=MaterialTheme.typography.titleMedium);members.forEach{m->Row{Text("${m.email} · ${m.role} · ${m.status}",Modifier.weight(1f));if(s.role=="owner"||m.role!="admin")TextButton(enabled=!busy,onClick={work{api.revoke(m)}}){Text("Revocar")}}};Field(target,{target=it},"Correo Google exacto");Choice("Permiso",if(s.role=="owner")listOf("reader","editor","admin") else listOf("reader","editor"),role,{it}){role=it};Button(enabled=!busy&&target.isNotBlank(),onClick={work{api.invite(s,target,role);target=""}}){Text("Invitar")}};if(s.role!="owner")TextButton(enabled=!busy,onClick={work{api.leave(s);current=null}}){Text("Salir del espacio")};TextButton(onClick={current=null}){Text("Cerrar detalle")}}}}
    }
    if(create){var title by remember{mutableStateOf("")};var body by remember{mutableStateOf("")};var kind by remember{mutableStateOf("task")};AlertDialog(onDismissRequest={create=false},title={Text("Nuevo espacio compartido")},text={Column{Choice("Tipo",listOf("task","project","home","shopping"),kind,{when(it){"task"->"Tarea";"project"->"Proyecto";"home"->"Hogar";else->"Compra"}}){kind=it};Field(title,{title=it},"Título");Field(body,{body=it},"Contenido",false);Text("Este contenido será visible solo tras aceptar una invitación. No se comparte tu inventario privado ni tu agenda.")}},confirmButton={TextButton(enabled=title.isNotBlank()&&!busy,onClick={create=false;work{api!!.create(kind,title,encode(SharedContent(notes=body)))}}){Text("Crear")}},dismissButton={TextButton(onClick={create=false}){Text("Cancelar")}})}
}
