package es.planidroid.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun ShoppingScreen(shopping:List<Shopping>,history:List<Purchase>,inventory:List<Item>,model:PlanidroidModel){
    var view by remember{mutableStateOf("Lista")};var edit by remember{mutableStateOf<Shopping?>(null)};var remove by remember{mutableStateOf<Shopping?>(null)};var undo by remember{mutableStateOf<Purchase?>(null)}
    val pending=shopping.filter{ShoppingLogic.needed(it,inventory.find{p->p.id==it.itemId})>0}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Section("La compra","Una lista que siempre tienes a mano.");GlassCard(tint=Mint){Text("${pending.size} productos pendientes",style=MaterialTheme.typography.headlineSmall);Text("${shopping.count{it.routine}} compras de rutina");Button(onClick={edit=Shopping(name="")}){Icon(Icons.Default.Add,null);Text(" Añadir producto")}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Lista","Rutina","Historial").forEach{v->FilterChip(view==v,{view=v},label={Text(v)})}}}
        if(view=="Historial") {if(history.isEmpty())item{Text("Aún no hay compras registradas.")};history.sortedByDescending{it.boughtAt}.groupBy{sessionDate(it.boughtAt)}.forEach{(day,list)->item{Text(day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy",java.util.Locale("es","ES"))),style=MaterialTheme.typography.titleMedium)};items(list,key={it.id}){p->GlassCard(tint=Mint){Text("${p.icon}  ${p.name}",style=MaterialTheme.typography.titleMedium);Text("${amountText(p.quantity)} ${p.unit} · ${time(p.boughtAt)} · ${if(p.routine)"Rutina" else "Puntual"}${if(p.undone)" · Anulada" else ""}");Row{TextButton(onClick={model.repeatPurchase(p,false)}){Text("Volver a añadir")};if(!p.undone)TextButton(onClick={undo=p}){Text("Deshacer compra")}}}}}}
        else {val shown=if(view=="Rutina")shopping.filter{it.routine} else pending;if(shown.isEmpty())item{GlassCard(tint=Mint){Text(if(view=="Rutina")"Crea tus compras habituales" else "Todo al día",style=MaterialTheme.typography.titleMedium);Text("Las reposiciones aparecen cuando el stock baja del mínimo.")}};items(shown.sortedBy{it.name.lowercase()},key={it.id}){s->val i=inventory.find{it.id==s.itemId};val n=ShoppingLogic.needed(s,i);GlassCard(tint=Mint){Row(verticalAlignment=Alignment.CenterVertically){if(view=="Lista")Checkbox(false,{if(it)model.buy(s.id)});Text(s.icon,style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(s.name,style=MaterialTheme.typography.titleMedium);Text("${amountText(if(n>0)n else s.quantity)} ${s.unit} · ${if(s.routine)"Rutina" else "Puntual"}",style=MaterialTheme.typography.labelMedium);if(i!=null)Text("Stock ${amountText(i.amount)} · mínimo ${amountText(s.minimum)}",style=MaterialTheme.typography.bodySmall)};IconButton(onClick={edit=s}){Icon(Icons.Default.Edit,"Editar")}};Row{if(view=="Rutina"&&n<=0)TextButton(onClick={model.save(s.copy(pending=true))}){Text("Añadir a la lista")};TextButton(onClick={remove=s}){Text("Eliminar")}}}}}
    }
    edit?.let{s->ShoppingEditor(s,inventory,{edit=null},{model.save(it);edit=null})};remove?.let{s->Confirm("Quitar ${s.name}","Se conserva el historial de compras.",{remove=null},{model.delete(s.id,Kind.SHOPPING)})};undo?.let{p->Confirm("Deshacer compra","Volverá a la lista. Se revertirá el stock si no ha cambiado después de esta compra.",{undo=null},{model.repeatPurchase(p,true)})}
}
@Composable fun ShoppingEditor(initial:Shopping,inventory:List<Item>,close:()->Unit,save:(Shopping)->Unit){var s by remember(initial){mutableStateOf(initial)};var q by remember{mutableStateOf(amountText(s.quantity))};var min by remember{mutableStateOf(amountText(s.minimum))};var target by remember{mutableStateOf(amountText(s.target))};val n=q.replace(',','.').toDoubleOrNull();val m=min.replace(',','.').toDoubleOrNull();val t=target.replace(',','.').toDoubleOrNull();val ok=s.name.isNotBlank()&&n!=null&&n.isFinite()&&n>0&&m!=null&&m.isFinite()&&m>=0&&t!=null&&t.isFinite()&&t>=m
    AlertDialog(onDismissRequest=close,title={Text("Producto de compra")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Field(s.name,{s=s.copy(name=it,icon=guessIcon(it))},"Nombre");Field(q,{q=it},"Cantidad");Field(s.unit,{s=s.copy(unit=it)},"Unidad");Row(verticalAlignment=Alignment.CenterVertically){Switch(s.routine,{s=s.copy(routine=it)});Text(" Compra de rutina")};if(s.routine){Choice("Vincular al inventario",listOf("")+inventory.map{it.id},s.itemId,{id->inventory.find{it.id==id}?.name?:"Sin vincular"}){id->val i=inventory.find{it.id==id};s=s.copy(itemId=id,name=i?.name?:s.name,unit=i?.unit?:s.unit,icon=i?.icon?:s.icon)};if(s.itemId.isNotBlank()){Field(min,{min=it},"Stock mínimo");Field(target,{target=it},"Stock deseado");Text("Al comprar, el stock vinculado aumentará.",style=MaterialTheme.typography.bodySmall)}}}},confirmButton={TextButton(enabled=ok,onClick={save(s.copy(name=s.name.trim(),quantity=n!!,minimum=m!!,target=t!!,itemId=if(s.routine)s.itemId else ""))}){Text("Guardar")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})
}
