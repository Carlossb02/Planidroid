package es.planidroid.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*
import es.planidroid.app.sync.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

private class CollectionAccess(val api:SharedCloud?,private val scope:CoroutineScope){
    var spaces by mutableStateOf(api?.cached()?.first.orEmpty())
        private set
    var invites by mutableStateOf(api?.cached()?.second.orEmpty())
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    fun work(refresh:Boolean=true,action:suspend (SharedCloud)->Unit={}){
        val cloud=api?:return
        if(busy)return
        busy=true;error=""
        scope.launch{
            try{action(cloud);val r=if(refresh)cloud.list()else cloud.cached();spaces=r.first;invites=r.second}
            catch(e:CancellationException){throw e}
            catch(e:com.google.firebase.firestore.FirebaseFirestoreException){error=if(e.code==com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)"Acceso denegado. Comprueba en Ajustes si tu cuenta está autorizada." else e.localizedMessage?:"No se pudo conectar"}
            catch(e:Exception){error=e.localizedMessage?:"No se pudo conectar"}
            finally{busy=false}
        }
    }
    suspend fun observe(){api?.observeCached()?.collect{r->spaces=r.first;invites=r.second}}
    suspend fun refreshNow(){val r=api!!.list();spaces=r.first;invites=r.second}
}
@Composable private fun collectionAccess(uid:String):CollectionAccess{
    val ctx=LocalContext.current;val scope=rememberCoroutineScope()
    val state=remember(uid){CollectionAccess(if(uid=="local")null else runCatching{SharedCloud(ctx)}.getOrNull(),scope)}
    LaunchedEffect(state){state.observe()}
    LaunchedEffect(state){if(state.api?.needsRefresh()==true&&Cloud.networkAvailable(ctx))state.work()}
    return state
}
@Composable private fun OnlineCollections(state:CollectionAccess,uid:String){
    val online=LocalOnlineAccess.current
    if(uid=="local"){
        OutlinedButton(onClick={online("recibir y compartir contenido")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.People,null);Spacer(Modifier.width(8.dp));Text("Compartido")}
    }else{
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("Compartido",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            IconButton(onClick={state.work()},enabled=!state.busy){Icon(Icons.Outlined.Refresh,"Actualizar")}
        }
        val pending=state.api?.pendingEdits()?.size?:0
        if(pending>0)Text("$pending cambios compartidos pendientes",style=MaterialTheme.typography.bodySmall)
        if(!Cloud.networkAvailable(LocalContext.current))Text("Sin conexión · copia local",style=MaterialTheme.typography.bodySmall)
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(state.error.isNotBlank())Text(state.error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        state.invites.forEach{m->GlassCard(tint=Lilac){
            Text("Invitación recibida",style=MaterialTheme.typography.titleMedium)
            Text("Permiso: "+roleLabel(m.role),style=MaterialTheme.typography.bodySmall)
            Row{TextButton(enabled=!state.busy,onClick={state.work{it.respond(m,true)}}){Text("Aceptar")};TextButton(enabled=!state.busy,onClick={state.work{it.respond(m,false)}}){Text("Rechazar")}}
        }}
    }
}
private fun roleLabel(role:String)=when(role){"owner"->"Propio";"reader"->"Solo lectura";"editor"->"Puede editar";"admin"->"Administrador";else->"Compartido"}
@Composable private fun CollectionNameDialog(title:String,close:()->Unit,save:(String)->Unit){
    var name by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text(title)},text={Field(name,{name=it.take(180)},"Nombre")},confirmButton={Button(enabled=name.isNotBlank(),onClick={save(name.trim());close()}){Text("Crear")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})
}
@Composable private fun CollectionInvite(space:SharedSpace,access:CollectionAccess,close:()->Unit){
    val api=access.api?:return
    val scope=rememberCoroutineScope();var email by remember{mutableStateOf("")};var role by remember{mutableStateOf("editor")}
    var people by remember(space.id){mutableStateOf(emptyList<Membership>())};var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    fun work(action:suspend ()->Unit){busy=true;error="";scope.launch{try{action();people=api.participants(space)}catch(e:CancellationException){throw e}catch(e:Exception){error=e.localizedMessage?:"No se pudo compartir"}finally{busy=false}}}
    LaunchedEffect(space.id){work{}}
    AlertDialog(onDismissRequest={if(!busy)close()},title={Text("Compartir "+space.title)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("Solo se comparte este contenido. Las personas invitadas deben aceptar.")
            Field(email,{email=it},"Correo de Google")
            Choice("Permiso",listOf("reader","editor"),role,::roleLabel){role=it}
            people.forEach{m->Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(m.email);Text(roleLabel(m.role)+" · "+m.status,style=MaterialTheme.typography.bodySmall)};TextButton(enabled=!busy,onClick={work{api.revoke(m)}}){Text("Revocar")}}}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={Button(enabled=!busy&&email.isNotBlank(),onClick={val target=email;work{api.invite(space,target,role);email=""}}){Text("Invitar")}},dismissButton={TextButton(enabled=!busy,onClick=close){Text("Cerrar")}})
}

@Composable fun ShoppingListsScreen(uid:String,lists:List<ShoppingList>,shopping:List<Shopping>,history:List<Purchase>,inventory:List<Item>,model:PlanidroidModel){
    val access=collectionAccess(uid);val online=LocalOnlineAccess.current
    val legacy=ShoppingList(id="legacy-shopping",name="Mi compra")
    val local=lists+if(lists.isEmpty()||shopping.any{it.listId.isBlank()}||history.any{it.listId.isBlank()})listOf(legacy)else emptyList()
    val shared=access.spaces.filter{it.kind=="shopping_list"}
    var selected by remember(uid){mutableStateOf<ShoppingList?>(null)}
    var remote by remember(uid){mutableStateOf<String?>(null)}
    var create by remember{mutableStateOf(false)}
    var publish by remember{mutableStateOf<ShoppingList?>(null)}
    var invite by remember{mutableStateOf<String?>(null)}
    val currentRemote=shared.find{it.id==remote}
    fun key(l:ShoppingList)=if(l.id==legacy.id)"" else l.id
    BackHandler(selected!=null||remote!=null){selected=null;remote=null}
    if(currentRemote!=null){
        SharedShoppingDetail(currentRemote,access,{remote=null},{invite=currentRemote.id})
    }else if(selected!=null){
        val l=selected!!
        ShoppingScreen(shopping.filter{it.listId==key(l)},history.filter{it.listId==key(l)},inventory,model,key(l),l.name,{selected=null},{if(uid=="local")online("compartir esta lista")else publish=l})
    }else{
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Text("Tus listas",Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium);FilledIconButton(onClick={create=true}){Icon(Icons.Outlined.Add,"Crear lista")}}
            local.filter{l->shared.none{it.role=="owner"&&it.sourceId==l.id}}.forEach{l->
                val count=shopping.count{it.listId==key(l)&&ShoppingLogic.needed(it,inventory.find{p->p.id==it.itemId})>0}
                CollectionTile(l.name,count.toString()+" pendientes",false,{selected=l}){Icon(Icons.Outlined.ShoppingBasket,null)}
            }
            shared.filter{it.role=="owner"}.forEach{s->CollectionTile(s.title,"Propia · en la nube",false,{remote=s.id}){Icon(Icons.Outlined.ShoppingBasket,null)}}
            OnlineCollections(access,uid)
            shared.filter{it.role!="owner"}.forEach{s->CollectionTile(s.title,roleLabel(s.role),true,{remote=s.id}){Icon(Icons.Outlined.ShoppingBasket,null)}}
        }
    }
    if(create)CollectionNameDialog("Nueva lista",{create=false},{model.save(ShoppingList(name=it))})
    publish?.let{l->Confirm("Compartir esta lista","Se creará una lista colaborativa con los productos actuales, sin enlaces a tu inventario privado. La copia local se conserva como recuperación; en adelante edita la versión compartida. El historial previo permanece privado.",{publish=null},{access.work{api->val s=api.publishShopping(l,shopping.filter{it.listId==key(l)});selected=null;remote=s.id;invite=s.id}})}
    invite?.let{id->shared.find{it.id==id}?.let{s->CollectionInvite(s,access){invite=null}}}
}

@Composable private fun SharedShoppingDetail(s:SharedSpace,access:CollectionAccess,back:()->Unit,invite:()->Unit){
    val data=remember(s.id,s.version){decode<SharedShopping>(s.body)}
    val write=s.role in listOf("owner","editor","admin")
    var quick by remember(s.id){mutableStateOf("")}
    var scan by remember(s.id){mutableStateOf(false)}
    var editing by remember(s.id){mutableStateOf<Shopping?>(null)}
    var historyOpen by remember{mutableStateOf(false)}
    var recipes by remember{mutableStateOf(false)}
    fun update(next:SharedShopping){access.work(refresh=false){it.update(s,s.title,encode(next))}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        TextButton(onClick=back){Text("‹ Listas")}
        Row(verticalAlignment=Alignment.CenterVertically){Text(s.title,Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium);if(s.role=="owner")IconButton(onClick=invite){Icon(Icons.Outlined.Share,"Compartir")}}
        Text(roleLabel(s.role),style=MaterialTheme.typography.labelMedium)
        if(write)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(quick,{quick=it},Modifier.weight(1f),singleLine=true,placeholder={Text("Añadir producto…")},shape=RoundedCornerShape(18.dp))
            FilledIconButton(enabled=!access.busy&&quick.isNotBlank(),onClick={update(data.copy(items=data.items+ShoppingText.parse(quick)));quick=""}){Icon(Icons.Outlined.Add,"Añadir")}
            IconButton(enabled=!access.busy,onClick={scan=true}){Icon(Icons.Outlined.CameraAlt,"Leer pizarra")}
        }
        if(access.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(access.error.isNotBlank())Text(access.error,color=MaterialTheme.colorScheme.error)
        data.items.filter{it.pending}.forEach{p->
            Surface(shape=RoundedCornerShape(18.dp),color=soft(if(s.role=="owner")Mint else Lilac,.12f)){
                Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){
                    Checkbox(false,{if(it&&write){val h=Purchase(shoppingId=p.id,name=p.name,quantity=p.quantity,unit=p.unit,routine=p.routine,icon=p.icon,quantitySpecified=p.quantitySpecified);update(data.copy(items=data.items.map{v->if(v.id==p.id)v.copy(pending=false)else v},history=(data.history+h).takeLast(100))) }},enabled=write&&!access.busy)
                    ProductGlyph(p.name,Modifier.size(28.dp));Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)){Text(p.name,style=MaterialTheme.typography.titleMedium);if(p.quantitySpecified)Text((amountText(p.quantity)+" "+p.unit).trim(),style=MaterialTheme.typography.bodySmall)}
                    if(write)IconButton(enabled=!access.busy,onClick={editing=p}){Icon(Icons.Outlined.Edit,"Editar")}
                }
            }
        }
        if(write)OutlinedButton(onClick={recipes=true}){Icon(Icons.Outlined.AutoAwesome,null);Text(" Recetas con Gemini")}
        if(data.items.none{it.pending})Text("Todo al día",style=MaterialTheme.typography.titleMedium)
        TextButton(onClick={historyOpen=!historyOpen}){Text(if(historyOpen)"Ocultar historial" else "Historial de compras")}
        if(historyOpen)data.history.asReversed().forEach{p->
            Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.name);Text(sessionDate(p.boughtAt).toString(),style=MaterialTheme.typography.bodySmall)};if(write&&!p.undone)TextButton(enabled=!access.busy,onClick={val old=data.items.find{it.id==p.shoppingId};val next=if(old!=null)data.items.map{if(it.id==old.id)it.copy(pending=true)else it}else data.items+Shopping(name=p.name,quantity=p.quantity,unit=p.unit,quantitySpecified=p.quantitySpecified,icon=p.icon);update(data.copy(items=next,history=data.history.map{if(it.id==p.id)it.copy(undone=true)else it}))}){Text("Deshacer")}}
        }
    }
    if(recipes)RecipeSuggestions(Cloud.auth(LocalContext.current)?.currentUser?.uid?:"local",data.items.filter{it.pending},"",{recipes=false},{update(data.copy(items=data.items+it))})
    if(scan)ShoppingScan("",{scan=false},{update(data.copy(items=data.items+it));scan=false})
    editing?.let{p->ShoppingEditor(p,emptyList(),{editing=null},{next->update(data.copy(items=data.items.map{if(it.id==p.id)next.copy(itemId="")else it}));editing=null})}
}

private data class HouseCard(val key:String,val title:String,val local:Home?,val cloud:SharedSpace?,val children:List<SharedSpace>,val owned:Boolean)
@Composable fun HomesScreen(uid:String,homes:List<Home>,furniture:List<Furniture>,inventory:List<Item>,model:PlanidroidModel){
    val access=collectionAccess(uid);val online=LocalOnlineAccess.current
    val legacy=Home(id="legacy-home",name="Mi hogar")
    val own=homes+if(furniture.any{it.homeId.isBlank()} )listOf(legacy)else emptyList()
    val houses=access.spaces.filter{it.kind=="house"}
    val remoteFurniture=access.spaces.filter{it.kind=="furniture"}
    val groups=buildList<HouseCard>{
        own.forEach{h->val cloud=houses.find{it.role=="owner"&&it.sourceId==h.id};add(HouseCard("local:"+h.id,h.name,h,cloud,remoteFurniture.filter{it.parentId==cloud?.id},true))}
        houses.filter{h->none{it.cloud?.id==h.id}}.forEach{h->add(HouseCard("cloud:"+h.id,h.title,null,h,remoteFurniture.filter{it.parentId==h.id},h.role=="owner"))}
        remoteFurniture.groupBy{it.parentId}.filterKeys{parent->none{it.cloud?.id==parent}}.forEach{(parent,children)->add(HouseCard("cloud:"+parent,children.first().homeName.ifBlank{"Hogar compartido"},null,null,children,false))}
    }
    var selected by remember(uid){mutableStateOf<String?>(null)}
    var privateOpen by remember(uid){mutableStateOf(false)}
    var remote by remember(uid){mutableStateOf<String?>(null)}
    var create by remember{mutableStateOf(false)}
    var createFurniture by remember{mutableStateOf(false)}
    var publishHome by remember{mutableStateOf(false)}
    var publishFurniture by remember{mutableStateOf<Furniture?>(null)}
    var invite by remember{mutableStateOf<String?>(null)}
    val current=groups.find{it.key==selected}
    val currentFurniture=remoteFurniture.find{it.id==remote}
    val localFurniture=if(current?.local!=null)furniture.filter{it.homeId==(if(current.local.id==legacy.id)"" else current.local.id)&&current.children.none{c->c.role=="owner"&&c.sourceId==it.id}}else emptyList()
    BackHandler(selected!=null||remote!=null||privateOpen){when{remote!=null->remote=null;privateOpen->privateOpen=false;else->selected=null}}
    if(currentFurniture!=null)SharedFurnitureDetail(currentFurniture,access,{remote=null},{invite=currentFurniture.id})
    else if(current!=null&&privateOpen&&current.local!=null){
        InventoryScreen(localFurniture,inventory.filter{it.furnitureId in localFurniture.map{f->f.id}},model,if(current.local.id==legacy.id)""else current.local.id,current.title,{privateOpen=false},{f->if(uid=="local")online("compartir este mueble")else publishFurniture=f})
    }else{
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            if(current==null){
                Row(verticalAlignment=Alignment.CenterVertically){Text("Casas",Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium);FilledIconButton(onClick={create=true}){Icon(Icons.Outlined.Add,"Crear hogar")}}
                groups.filter{it.owned}.forEach{h->CollectionTile(h.title,"Hogar propio",false,{selected=h.key}){Icon(Icons.Outlined.Home,null)}}
                if(groups.none{it.owned})Text("Crea un hogar para organizar tus muebles.")
                OnlineCollections(access,uid)
                groups.filterNot{it.owned}.forEach{h->CollectionTile(h.title,h.children.size.toString()+" muebles disponibles",true,{selected=h.key}){Icon(Icons.Outlined.Home,null)}}
            }else{
                TextButton(onClick={selected=null}){Text("‹ Casas")}
                Row(verticalAlignment=Alignment.CenterVertically){Text(current.title,Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium);if(current.owned)IconButton(onClick={if(uid=="local")online("compartir este hogar")else if(current.local!=null)publishHome=true else current.cloud?.let{invite=it.id}}){Icon(Icons.Outlined.Share,"Compartir hogar")}}
                if(!current.owned)Text(if(current.cloud==null)"Solo los muebles que te han compartido" else roleLabel(current.cloud.role),style=MaterialTheme.typography.bodySmall)
                if(current.local!=null){
                    CollectionTile("Muebles privados",localFurniture.size.toString()+" muebles · añadir o editar",false,{privateOpen=true}){Icon(Icons.Outlined.Inventory2,null)}
                }
                current.children.forEach{c->val d=runCatching{decode<SharedFurniture>(c.body)}.getOrNull()
                    CollectionTile(c.title,roleLabel(c.role),c.role!="owner",{remote=c.id}){FurnitureGlyph(d?.furniture?.type?:FurnitureType.OTHER,Modifier.size(36.dp))}
                }
                if(current.cloud?.role=="owner")OutlinedButton(onClick={createFurniture=true},enabled=!access.busy){Icon(Icons.Outlined.Add,null);Text(" Añadir mueble al hogar online")}
                if(access.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(access.error.isNotBlank())Text(access.error,color=MaterialTheme.colorScheme.error)
            }
        }
    }
    if(create)CollectionNameDialog("Nuevo hogar",{create=false},{model.save(Home(name=it))})
    if(createFurniture&&current?.cloud!=null)FurnitureEditor(Furniture(name="",type=FurnitureType.OTHER,homeId=current.cloud.sourceId),{createFurniture=false},{f->access.work{it.addFurniture(requireNotNull(current.cloud),f)};createFurniture=false})
    if(publishHome&&current?.local!=null)Confirm("Compartir hogar","Se publicarán todos los muebles y su contenido de este hogar, sin fotografías privadas. Los originales locales se conservan como recuperación. Quien acepte tendrá acceso a los muebles publicados y a los que añadas al hogar online.",{publishHome=false},{val h=current.local;val fs=furniture.filter{it.homeId==(if(h.id==legacy.id)""else h.id)};access.work{api->val s=api.publishHome(h,fs,inventory);invite=s.id}})
    publishFurniture?.let{f->if(current?.local!=null)Confirm("Compartir solo "+f.name,"Se publicará exclusivamente este mueble y su contenido, sin fotos locales. El destinatario verá el nombre del hogar, pero no otros muebles. El original local se conserva como recuperación.",{publishFurniture=null},{val h=current.local;access.work{api->val s=api.publishFurniture(h,f,inventory);privateOpen=false;remote=s.id;invite=s.id}})}
    invite?.let{id->access.spaces.find{it.id==id}?.let{s->CollectionInvite(s,access){invite=null}}}
}
@Composable private fun SharedFurnitureDetail(s:SharedSpace,access:CollectionAccess,back:()->Unit,invite:()->Unit){
    val data=remember(s.id,s.version){decode<SharedFurniture>(s.body)}
    val f=data.furniture;val write=s.role in listOf("owner","editor","admin")
    var layout by remember(s.id){mutableStateOf(false)}
    var edit by remember(s.id){mutableStateOf<Item?>(null)}
    var selected by remember(s.id){mutableStateOf<String?>(null)}
    fun update(next:SharedFurniture){access.work(refresh=false){it.update(s,next.furniture.name,encode(next.copy(furniture=next.furniture.copy(photo=""),items=next.items.map{i->i.copy(photo="")})))}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        TextButton(onClick=back){Text("‹ Muebles")}
        Row(verticalAlignment=Alignment.CenterVertically){FurnitureGlyph(f.type,Modifier.size(50.dp));Column(Modifier.weight(1f)){Text(s.title,style=MaterialTheme.typography.headlineMedium);Text(roleLabel(s.role),style=MaterialTheme.typography.bodySmall)};if(s.role=="owner")IconButton(onClick=invite){Icon(Icons.Outlined.Share,"Compartir mueble")}}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Column(Modifier.weight(1f)){Text("Interior");ZoneBoard(LayoutGrid.zones(f).filterNot{it.door},data.items,false,selected,{selected=it.name},{})}
            if(LayoutGrid.zones(f).any{it.door})Column(Modifier.weight(1f)){Text("Puerta");ZoneBoard(LayoutGrid.zones(f).filter{it.door},data.items,false,selected,{selected=it.name},{})}
        }
        if(selected!=null)TextButton(onClick={selected=null}){Text("Mostrar todos")}
        if(write)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(enabled=!access.busy,onClick={edit=Item(furnitureId=f.id,name="",compartment=selected?:f.compartments.firstOrNull()?:"General")}){Text("Añadir contenido")};TextButton(enabled=!access.busy,onClick={layout=true}){Text("Distribución")}}
        data.items.filter{selected==null||it.compartment==selected}.forEach{i->GlassCard(tint=if(s.role=="owner")Mint else Lilac){
            Row(verticalAlignment=Alignment.CenterVertically){ProductGlyph(i.name,Modifier.size(28.dp));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(i.name,style=MaterialTheme.typography.titleMedium);Text(amountText(i.amount)+" "+i.unit+" · "+i.compartment,style=MaterialTheme.typography.bodySmall)};if(write)IconButton(enabled=!access.busy,onClick={edit=i}){Icon(Icons.Outlined.Edit,"Editar")}}
        }}
        if(access.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(access.error.isNotBlank())Text(access.error,color=MaterialTheme.colorScheme.error)
    }
    if(layout)LayoutEditor(f,data.items,{layout=false},{update(data.copy(furniture=it));layout=false})
    edit?.let{i->ItemEditor(i,f,{edit=null},{v->update(data.copy(items=if(data.items.any{it.id==v.id})data.items.map{if(it.id==v.id)v else it}else data.items+v));edit=null})}
}
