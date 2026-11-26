package es.planidroid.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*

@Composable internal fun ConsumeButton(item:Item,action:()->Unit,enabled:Boolean=true){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically){
        if(item.amount<=0)Text("Agotado",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.error)
        else FilledTonalButton(onClick=action,enabled=enabled){Icon(Icons.Default.RemoveCircleOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Consumir")}
    }
}

@Composable internal fun ConsumeDialog(item:Item,close:()->Unit,save:(Double,(String?)->Unit)->Unit){
    var amount by remember(item.id,item.amount){mutableStateOf(amountText(minOf(1.0,item.amount)))}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    val n=amount.replace(',','.').toDoubleOrNull()
    val valid=n!=null&&n.isFinite()&&n>0&&n<=item.amount
    fun submit(value:Double){busy=true;error="";save(value){failure->busy=false;if(failure==null)close()else error=failure}}
    AlertDialog(onDismissRequest={if(!busy)close()},title={Text("Consumir "+item.name)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            ProductGlyph(item.name,Modifier.size(36.dp))
            Column{Text("Disponible",style=MaterialTheme.typography.labelMedium);Text(amountText(item.amount)+" "+item.unit,style=MaterialTheme.typography.titleLarge)}
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(amount,{amount=it.take(24)},Modifier.weight(1f),label={Text("Consumir")},singleLine=true,enabled=!busy,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal))
            Text(item.unit,style=MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(selected=n==item.amount,onClick={amount=amountText(item.amount)},enabled=!busy,label={Text("Todo")})
            if(item.amount>1)FilterChip(selected=n==1.0,onClick={amount="1"},enabled=!busy,label={Text("1 "+item.unit)})
        }
        if(valid){val left=InventoryConsumption.consume(item,n!!).amount
            Text(if(left==0.0)"Se agotará. Podrás añadirlo a la compra." else "Quedarán "+amountText(left)+" "+item.unit,style=MaterialTheme.typography.bodyMedium)
        }
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    }},confirmButton={TextButton(enabled=!busy&&valid,onClick={submit(n!!)}){Text("Consumir")}},dismissButton={TextButton(enabled=!busy,onClick=close){Text("Cancelar")}})
}

@Composable internal fun RestockBanner(
    items:List<Item>,replenish:(Item,Boolean)->Unit,dismiss:(Item)->Unit,enabled:Boolean=true
){
    var index by remember{mutableIntStateOf(0)}
    var expanded by remember{mutableStateOf(false)}
    val item=items.getOrNull(index.coerceAtMost(items.lastIndex.coerceAtLeast(0)))?:return
    if(!expanded){
        Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.secondaryContainer,
            onClick={expanded=true},enabled=enabled){
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Icon(Icons.Default.ShoppingCart,null,Modifier.size(20.dp))
                Text(items.size.toString()+if(items.size==1)" producto para reponer" else " productos para reponer",
                    Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                Icon(Icons.Default.ExpandMore,null)
            }
        }
        return
    }
    GlassCard(tint=Peach){
        Row(verticalAlignment=Alignment.CenterVertically){
            ProductGlyph(item.name,Modifier.size(34.dp),item.icon)
            Column(Modifier.weight(1f).padding(start=8.dp)){
                Text(item.name,style=MaterialTheme.typography.titleSmall)
                Text("Sin existencias",style=MaterialTheme.typography.labelSmall)
            }
            if(items.size>1)IconButton(onClick={index=(index+1)%items.size}){Icon(Icons.Default.ChevronRight,"Siguiente sugerencia")}
            IconButton(onClick={expanded=false}){Icon(Icons.Default.ExpandLess,"Ocultar sugerencias")}
        }
        val recipe=InventoryConsumption.kind(item)==InventoryKind.RECIPE||
            RecipeBook.matches(item.name,LocalRecipeLibrary.current.recipes).isNotEmpty()
        ActionFlow {
            TextButton(enabled=enabled,onClick={replenish(item,false)}){Text("Añadir a la compra")}
            if(recipe)TextButton(enabled=enabled,onClick={replenish(item,true)}){Text("Añadir ingredientes")}
            TextButton(enabled=enabled,onClick={dismiss(item)}){Text("Descartar")}
        }
    }
}

@Composable internal fun InventoryRestockDialog(uid:String,item:Item,lists:List<ShoppingList>,close:()->Unit,save:(List<Shopping>,String,(String?)->Unit)->Unit,shared:Boolean=false,initialRecipeMode:Boolean=false){
    val destinations=lists.ifEmpty{listOf(ShoppingList(id="",name="Mi compra"))}
    var listId by remember(item.id){mutableStateOf(destinations.first().id)}
    val isRecipe=InventoryConsumption.kind(item)==InventoryKind.RECIPE||RecipeBook.matches(item.name,LocalRecipeLibrary.current.recipes).isNotEmpty()
    var recipeMode by remember(item.id){mutableStateOf(initialRecipeMode)}
    var amount by remember(item.id){mutableStateOf(amountText(item.restockAmount))}
    var unit by remember(item.id){mutableStateOf(SpanishUnits.canonical(item.unit)?:item.unit)}
    var name by remember{mutableStateOf("")};var ingredientAmount by remember{mutableStateOf("1")};var ingredientUnit by remember{mutableStateOf("ud")}
    var products by remember(item.id){mutableStateOf(emptyList<Shopping>())}
    var ai by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    val n=amount.replace(',','.').toDoubleOrNull()
    val ingredientN=ingredientAmount.replace(',','.').toDoubleOrNull()
    val canAdd=name.isNotBlank()&&ingredientN!=null&&ingredientN.isFinite()&&ingredientN>0&&ingredientN<=100000&&products.size<60
    val valid=if(recipeMode)products.isNotEmpty()else n!=null&&n.isFinite()&&n>0&&n<=100000&&SpanishUnits.canonical(unit)!=null
    fun submit(){busy=true;error="";val selected=if(recipeMode)products else listOf(Shopping(name=item.name,quantity=n!!,unit=unit,icon=guessIcon(item.name),itemId=item.id));save(selected,listId){failure->busy=false;if(failure==null)close()else error=failure}}
    if(ai){
        RecipeSuggestions(uid,emptyList(),listId,{ai=false},{result->products=result;ai=false},dishName=item.name)
    }else AlertDialog(onDismissRequest={if(!busy)close()},title={Text(if(recipeMode)"Reponer "+item.name else "Añadir a la compra")},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(item.name,style=MaterialTheme.typography.titleMedium)
        Choice("Lista de compra",destinations.map{it.id},listId,{id->destinations.find{it.id==id}?.name?:"Mi compra"}){listId=it}
        if(isRecipe){
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(recipeMode,{recipeMode=true},label={Text("Ingredientes")},enabled=!busy);FilterChip(!recipeMode,{recipeMode=false},label={Text("Plato preparado")},enabled=!busy)}
        }
        if(recipeMode){
            FilledTonalButton(enabled=!busy,onClick={ai=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text("Elegir receta o sugerir ingredientes")}
            products.forEach{p->Row(verticalAlignment=Alignment.CenterVertically){ProductGlyph(p.name,Modifier.size(24.dp));Column(Modifier.weight(1f).padding(horizontal=8.dp)){Text(p.name);Text(amountText(p.quantity)+" "+p.unit,style=MaterialTheme.typography.labelSmall)};IconButton(onClick={products=products.filterNot{it.id==p.id}},enabled=!busy){Icon(Icons.Default.Close,"Quitar ingrediente")}}}
            Field(name,{name=it.take(180)},"Ingrediente")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Field(ingredientAmount,{ingredientAmount=it.take(18)},"Cantidad")};Column(Modifier.weight(1f)){UnitSelector(ingredientUnit,{ingredientUnit=it},allowEmpty=false)}}
            TextButton(enabled=canAdd&&!busy,onClick={products=products+Shopping(name=name.trim(),quantity=ingredientN!!,unit=ingredientUnit,icon=guessIcon(name));name="";ingredientAmount="1"}){Icon(Icons.Default.Add,null);Text(" Añadir ingrediente")}
        }else Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Field(amount,{amount=it.take(24)},"Cantidad")};Column(Modifier.weight(1f)){UnitSelector(unit,{unit=it})}}
        if(shared)Text("Se añadirá a tu lista personal, sin vincular las existencias compartidas.",style=MaterialTheme.typography.bodySmall)
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    }},confirmButton={TextButton(enabled=valid&&!busy,onClick={submit()}){Text(if(recipeMode)"Añadir ingredientes"else "Añadir")}},dismissButton={TextButton(enabled=!busy,onClick=close){Text("Ahora no")}})
}


@Composable internal fun MoveInventoryDialog(
    item:Item,destinations:List<Furniture>,close:()->Unit,
    move:(Furniture,String,(String?)->Unit)->Unit
){
    var targetId by remember(item.id){mutableStateOf(destinations.firstOrNull()?.id.orEmpty())}
    val target=destinations.find{it.id==targetId}
    val zones=target?.compartments?.ifEmpty{listOf("General")}.orEmpty()
    var zone by remember(targetId){mutableStateOf(zones.firstOrNull().orEmpty())}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest={if(!busy)close()},title={Text("Mover "+item.name)},text={
        Column(Modifier.heightIn(max=330.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(destinations.isEmpty())Text("Añade otro mueble a esta casa para mover productos.")
            else{
                Choice("Mueble",destinations.map{it.id},targetId,
                    {id->destinations.find{it.id==id}?.let{value->if(value.room.isBlank())value.name else value.name+" · "+value.room}.orEmpty()}){if(!busy)targetId=it}
                if(zones.isNotEmpty())Choice("Compartimento",zones,zone,{it}){if(!busy)zone=it}
            }
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={Button(enabled=!busy&&target!=null&&zone in zones,onClick={
        val destination=target?:return@Button
        busy=true;error=""
        move(destination,zone){failure->busy=false;if(failure==null)close()else error=failure}
    }){Text("Mover")}},dismissButton={TextButton(enabled=!busy,onClick=close){Text("Cancelar")}})
}
