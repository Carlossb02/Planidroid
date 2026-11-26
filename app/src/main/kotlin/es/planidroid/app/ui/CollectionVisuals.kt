package es.planidroid.app.ui
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*

@Composable fun FurnitureGlyph(type:FurnitureType,modifier:Modifier=Modifier){
    val accent=when(type){
        FurnitureType.FRIDGE,FurnitureType.FREEZER -> Blue
        FurnitureType.WASHER -> Lilac
        FurnitureType.WARDROBE,FurnitureType.DRAWER,FurnitureType.CABINET -> Peach
        FurnitureType.PANTRY,FurnitureType.SHELF -> Mint
        FurnitureType.BOX,FurnitureType.OTHER -> MaterialTheme.colorScheme.primary
    }
    val colors=MaterialTheme.colorScheme
    val color by animateColorAsState(lerp(colors.onSurface,accent,.55f),label="Furniture color")
    val fill=soft(accent,.19f)
    Canvas(modifier.background(soft(accent,.10f),RoundedCornerShape(12.dp)).padding(4.dp)){
        val w=size.width;val h=size.height;val stroke=Stroke(width=w*.061f,cap=StrokeCap.Round)
        fun rect(x:Float,y:Float,ww:Float,hh:Float){
            val origin=Offset(w*x,h*y);val bounds=Size(w*ww,h*hh);val radius=CornerRadius(w*.067f)
            drawRoundRect(fill,origin,bounds,radius)
            drawRoundRect(color,origin,bounds,radius,style=stroke)
        }
        fun line(x:Float,y:Float,x2:Float,y2:Float){drawLine(color,Offset(w*x,h*y),Offset(w*x2,h*y2),strokeWidth=w*.054f,cap=StrokeCap.Round)}
        when(type){
            FurnitureType.FRIDGE->{
                rect(.21f,.08f,.58f,.84f)
                line(.22f,.40f,.78f,.40f)
                line(.34f,.23f,.34f,.33f)
                line(.34f,.49f,.34f,.65f)
            }
            FurnitureType.FREEZER->{
                rect(.15f,.23f,.70f,.56f)
                line(.15f,.39f,.85f,.39f)
                line(.5f,.48f,.5f,.70f)
                line(.40f,.53f,.60f,.65f)
                line(.40f,.65f,.60f,.53f)
            }
            FurnitureType.WASHER->{rect(.13f,.12f,.74f,.76f);line(.13f,.29f,.87f,.29f);drawCircle(color,w*.19f,Offset(w*.5f,h*.56f),style=stroke);line(.25f,.21f,.38f,.21f)}
            FurnitureType.WARDROBE->{rect(.13f,.08f,.74f,.82f);line(.50f,.08f,.50f,.90f);line(.39f,.44f,.39f,.56f);line(.61f,.44f,.61f,.56f)}
            FurnitureType.CABINET->{rect(.13f,.22f,.74f,.56f);line(.50f,.22f,.50f,.78f);line(.37f,.50f,.43f,.50f);line(.57f,.50f,.63f,.50f);line(.22f,.78f,.22f,.89f);line(.78f,.78f,.78f,.89f)}
            FurnitureType.DRAWER->{rect(.12f,.17f,.76f,.66f);for(i in 1..2)line(.12f,.17f+i*.22f,.88f,.17f+i*.22f);for(i in 0..2)line(.43f,.28f+i*.22f,.57f,.28f+i*.22f)}
            FurnitureType.PANTRY->{rect(.14f,.08f,.72f,.82f);for(i in 1..3)line(.14f,.08f+i*.205f,.86f,.08f+i*.205f);rect(.23f,.17f,.14f,.12f);rect(.60f,.58f,.12f,.12f)}
            FurnitureType.SHELF->{line(.15f,.12f,.15f,.88f);line(.85f,.12f,.85f,.88f);for(i in 0..2)line(.15f,.24f+i*.26f,.85f,.24f+i*.26f)}
            FurnitureType.BOX->{rect(.12f,.28f,.76f,.55f);rect(.08f,.18f,.84f,.15f);line(.42f,.44f,.58f,.44f)}
            FurnitureType.OTHER->{rect(.14f,.14f,.72f,.72f);line(.37f,.5f,.63f,.5f)}
        }
    }
}
internal fun productIconType(name:String,symbol:String?):ProductType =
    when(symbol){
        "🍌" -> ProductType.FRUIT
        "🥕","🍅" -> ProductType.VEGETABLES
        "🍗" -> ProductType.MEAT
        "🍦" -> ProductType.FROZEN
        "🍾" -> ProductType.DRINKS
        "🍚","🍝" -> ProductType.PANTRY
        else -> ProductType.entries.find{it.symbol==symbol}?:productType(name)
    }

@Composable fun ProductGlyph(name:String,modifier:Modifier=Modifier,symbol:String?=null){
    val type=productIconType(name,symbol)
    val accent=productAccent(type)
    val ink=lerp(MaterialTheme.colorScheme.onSurface,accent,.56f)
    Box(modifier.background(soft(accent,.15f),RoundedCornerShape(12.dp)).padding(4.dp),contentAlignment=Alignment.Center){
        ProductMark(type,ink,Modifier.fillMaxSize())
    }
}

@Composable private fun ProductMark(type:ProductType,ink:Color,modifier:Modifier){
    if(type in listOf(ProductType.YOGURT,ProductType.CHEESE,ProductType.MEAT,ProductType.VEGETABLES,ProductType.FRUIT)){FoodOutline(type,ink,modifier);return}
    val icon=when(type){
        ProductType.DAIRY->Icons.Outlined.LocalDrink
        ProductType.YOGURT->Icons.Outlined.LocalDrink
        ProductType.CHEESE->Icons.Outlined.Restaurant
        ProductType.EGGS->Icons.Outlined.Egg
        ProductType.FRUIT->Icons.Outlined.Spa
        ProductType.VEGETABLES->Icons.Outlined.Spa
        ProductType.MEAT->Icons.Outlined.RestaurantMenu
        ProductType.FISH->Icons.Outlined.SetMeal
        ProductType.BAKERY->Icons.Outlined.BakeryDining
        ProductType.FROZEN->Icons.Outlined.AcUnit
        ProductType.DRINKS->Icons.Outlined.LocalCafe
        ProductType.PANTRY->Icons.Outlined.Inventory2
        ProductType.CLEANING->Icons.Outlined.CleaningServices
        ProductType.HYGIENE->Icons.Outlined.Soap
        ProductType.PAPER->Icons.Outlined.Description
        ProductType.MEDICINE->Icons.Outlined.Medication
        ProductType.CLOTHES->Icons.Outlined.Checkroom
        ProductType.TOOLS->Icons.Outlined.Build
        ProductType.OTHER->Icons.Outlined.ShoppingBasket
    }
    Icon(icon,null,modifier,tint=ink)
}
@Composable fun CollectionTile(title:String,subtitle:String,shared:Boolean,onClick:()->Unit,icon:@Composable ()->Unit){
    Surface(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface){
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
            Box(Modifier.size(44.dp).background(soft(if(shared)Lilac else Mint,.15f),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){icon()}
            Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(shared)Icon(Icons.Outlined.People,"Compartido",Modifier.size(20.dp))
            Icon(Icons.Outlined.ChevronRight,null)
        }
    }
}

@Composable private fun FoodOutline(type:ProductType,c:Color,modifier:Modifier){
    Canvas(modifier.padding(2.dp)){
        val w=size.width;val h=size.height;val stroke=Stroke(w*.065f,cap=StrokeCap.Round)
        fun line(a:Float,b:Float,x:Float,y:Float)=drawLine(c,Offset(w*a,h*b),Offset(w*x,h*y),w*.065f,StrokeCap.Round)
        fun oval(a:Float,b:Float,x:Float,y:Float)=drawOval(c,Offset(w*a,h*b),Size(w*x,h*y),style=stroke)
        fun poly(vararg xy:Float){val p=androidx.compose.ui.graphics.Path();p.moveTo(w*xy[0],h*xy[1]);for(i in 2 until xy.size step 2)p.lineTo(w*xy[i],h*xy[i+1]);p.close();drawPath(p,c.copy(alpha=.16f));drawPath(p,c,style=stroke)}
        when(type){
            ProductType.FRUIT->{oval(.12f,.28f,.76f,.62f);line(.50f,.28f,.56f,.10f);oval(.56f,.09f,.27f,.14f)}
            ProductType.YOGURT->{poly(.19f,.29f,.81f,.29f,.69f,.88f,.31f,.88f);line(.13f,.22f,.87f,.22f);line(.60f,.19f,.80f,.06f)}
            ProductType.CHEESE->{poly(.10f,.50f,.62f,.13f,.91f,.49f,.91f,.82f,.10f,.82f);line(.10f,.50f,.91f,.49f);oval(.33f,.58f,.14f,.13f);oval(.66f,.62f,.12f,.12f)}
            ProductType.MEAT->{oval(.10f,.17f,.76f,.68f);oval(.25f,.30f,.37f,.32f);line(.66f,.33f,.77f,.55f)}
            else->{poly(.19f,.40f,.52f,.32f,.67f,.49f,.24f,.91f);line(.50f,.33f,.60f,.10f);line(.55f,.33f,.83f,.17f);line(.59f,.38f,.89f,.36f);line(.30f,.52f,.40f,.60f);line(.23f,.66f,.32f,.73f)}
        }
    }
}
