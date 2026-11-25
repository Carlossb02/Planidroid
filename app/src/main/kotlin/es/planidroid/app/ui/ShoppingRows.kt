package es.planidroid.app.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*

internal fun productLabel(type:ProductType)=when(type){
    ProductType.VEGETABLES->"Verduras y legumbres";ProductType.FRUIT->"Fruta";ProductType.DAIRY->"Lácteos";ProductType.YOGURT->"Yogures";ProductType.CHEESE->"Quesos";ProductType.EGGS->"Huevos";ProductType.MEAT->"Carne";ProductType.FISH->"Pescado";ProductType.BAKERY->"Panadería";ProductType.PANTRY->"Despensa";ProductType.DRINKS->"Bebidas";ProductType.FROZEN->"Congelados";ProductType.CLEANING->"Limpieza";ProductType.HYGIENE->"Higiene";ProductType.PAPER->"Papel";ProductType.MEDICINE->"Farmacia";ProductType.CLOTHES->"Ropa";ProductType.TOOLS->"Herramientas";ProductType.OTHER->"Otros"
}
internal fun productAccent(type:ProductType):Color=when(type){ProductType.VEGETABLES,ProductType.FRUIT->Mint;ProductType.MEAT,ProductType.FISH->Peach;ProductType.DAIRY,ProductType.YOGURT,ProductType.CHEESE,ProductType.EGGS->Blue;ProductType.CLEANING,ProductType.HYGIENE,ProductType.PAPER,ProductType.MEDICINE->Lilac;else->Color(0xFF8C7948)}
@Composable internal fun ShoppingInput(value:String,change:(String)->Unit,add:()->Unit,scan:()->Unit,enabled:Boolean=true){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
        OutlinedTextField(value,change,Modifier.weight(1f),singleLine=true,enabled=enabled,placeholder={Text("Añadir producto")},shape=RoundedCornerShape(18.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={if(value.isNotBlank())add()}),trailingIcon={IconButton(enabled=enabled&&value.isNotBlank(),onClick=add){Icon(Icons.Default.Add,"Añadir producto")}})
        FilledTonalIconButton(enabled=enabled,onClick=scan){Icon(Icons.Default.CameraAlt,"Leer lista por foto")}
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun ShoppingRow(product:Shopping,detail:String,selecting:Boolean,checked:Boolean,canWrite:Boolean=true,buy:(()->Unit)?=null,select:()->Unit={},edit:()->Unit={},remove:()->Unit={},repeat:(()->Unit)?=null){
    var menu by remember(product.id){mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().background(if(selecting&&checked)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).combinedClickable(enabled=canWrite,onClick={if(selecting)select()else edit()},onLongClick=select).padding(end=4.dp),verticalAlignment=Alignment.CenterVertically){
        if(selecting||buy!=null)Checkbox(checked=selecting&&checked,onCheckedChange={if(selecting)select()else if(it)buy?.invoke()},enabled=canWrite)
        else Spacer(Modifier.width(12.dp))
        Box(Modifier.size(40.dp).background(soft(productAccent(productType(product.name)),.18f),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){ProductGlyph(product.name,Modifier.size(25.dp))};Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical=12.dp)){
            Text(product.name,style=MaterialTheme.typography.bodyLarge)
            if(detail.isNotBlank())Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(canWrite&&!selecting)Box{
            IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Opciones de "+product.name)}
            DropdownMenu(menu,{menu=false}){
                DropdownMenuItem(text={Text("Editar")},onClick={menu=false;edit()})
                if(repeat!=null)DropdownMenuItem(text={Text("Añadir a la lista")},onClick={menu=false;repeat()})
                DropdownMenuItem(text={Text("Seleccionar")},onClick={menu=false;select()})
                DropdownMenuItem(text={Text("Eliminar",color=MaterialTheme.colorScheme.error)},onClick={menu=false;remove()})
            }
        }
    }
}
@Composable internal fun ShoppingSelection(count:Int,total:Int,selectAll:()->Unit,close:()->Unit,remove:()->Unit){
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.secondaryContainer){
        Row(Modifier.fillMaxWidth().padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=close){Icon(Icons.Default.Close,"Cancelar selección")};Text("$count seleccionados",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
            TextButton(onClick=selectAll){Text(if(count==total)"Ninguno" else "Todos")}
            IconButton(enabled=count>0,onClick=remove){Icon(Icons.Default.DeleteOutline,"Eliminar seleccionados",tint=MaterialTheme.colorScheme.error)}
        }
    }
}
