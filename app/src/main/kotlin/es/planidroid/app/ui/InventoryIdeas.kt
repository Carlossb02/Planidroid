package es.planidroid.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*
import es.planidroid.app.sync.AiConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

internal fun usablePantryItem(item:Item):Boolean =
    item.amount>0 && InventoryConsumption.kind(item)!=InventoryKind.OTHER &&
        (item.expiry.isBlank() || runCatching { LocalDate.parse(item.expiry)>=LocalDate.now() }.getOrDefault(false))

@Composable internal fun InventoryIdeas(uid:String,furniture:String,inventory:List<Item>,close:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val products=inventory.filter(::usablePantryItem).sortedBy{it.expiry.ifBlank{"9999"}}.take(40)
    val dated=products.filter{it.expiry.isNotBlank()}.take(4)
    var request by remember{mutableStateOf("")}
    var answer by remember{mutableStateOf("")}
    var error by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var connect by remember{mutableStateOf(false)}
    fun ask(){
        if(busy||products.isEmpty())return
        if(!AiConfig.configured(context,uid)){connect=true;return}
        val query=request.trim().ifBlank{"Dame dos ideas sencillas para aprovechar lo que tengo."}
        val stock=products.joinToString("\n"){
            it.name.take(180)+": "+amountText(it.amount)+" "+it.unit.take(30)+
                if(it.expiry.isNotBlank())"; fecha "+it.expiry else ""
        }
        busy=true
        error=""
        scope.launch{
            try{
                answer=AiConfig.provider(context,uid).askAboutInventory(query,stock)
            }catch(cancellation:CancellationException){
                throw cancellation
            }catch(exception:Exception){
                error=exception.localizedMessage?:"No se pudo consultar Gemini."
            }finally{
                busy=false
            }
        }
    }
    AlertDialog(onDismissRequest=close,title={Text("Aprovechar $furniture")},text={
        Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            if(dated.isNotEmpty()){
                Text("Primeras fechas registradas",style=MaterialTheme.typography.titleSmall)
                dated.forEach{item->Text(item.name+" · "+item.expiry,style=MaterialTheme.typography.bodyMedium)}
            }
            OutlinedTextField(request,{request=it.take(1200)},Modifier.fillMaxWidth(),maxLines=4,
                label={Text("¿Qué quieres preparar? (opcional)")},enabled=!busy)
            Text("La consulta envía nombres, cantidades y fechas de hasta 40 alimentos de este mueble.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            if(answer.isNotBlank())Text(answer)
        }
    },confirmButton={TextButton(onClick=::ask,enabled=!busy&&products.isNotEmpty()){Text(if(error.isBlank())"Consultar Gemini" else "Reintentar")}},
        dismissButton={TextButton(onClick=close){Text("Cerrar")}})
    if(connect)PersonalGeminiDialog(uid){connect=false}
}
