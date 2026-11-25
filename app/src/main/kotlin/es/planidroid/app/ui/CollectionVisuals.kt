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
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*

/** Uniform outlined furniture silhouettes; no decorative coloured bitmaps. */
@Composable fun FurnitureGlyph(type:FurnitureType,modifier:Modifier=Modifier){
    val color=MaterialTheme.colorScheme.onSurface
    Canvas(modifier.padding(4.dp)){
        val w=size.width;val h=size.height;val stroke=Stroke(width=w*.045f)
        fun rect(x:Float,y:Float,ww:Float,hh:Float){drawRoundRect(color,Offset(w*x,h*y),Size(w*ww,h*hh),CornerRadius(w*.045f),style=stroke)}
        fun line(x:Float,y:Float,x2:Float,y2:Float){drawLine(color,Offset(w*x,h*y),Offset(w*x2,h*y2),strokeWidth=w*.045f,cap=StrokeCap.Round)}
        when(type){
            FurnitureType.FRIDGE,FurnitureType.FREEZER->{rect(.22f,.08f,.56f,.84f);line(.22f,.38f,.78f,.38f);line(.33f,.18f,.33f,.30f);line(.33f,.48f,.33f,.62f);if(type==FurnitureType.FREEZER){line(.5f,.64f,.5f,.82f);line(.41f,.69f,.59f,.77f);line(.41f,.77f,.59f,.69f)}}
            FurnitureType.WASHER->{rect(.13f,.12f,.74f,.76f);line(.13f,.29f,.87f,.29f);drawCircle(color,w*.19f,Offset(w*.5f,h*.56f),style=stroke);line(.25f,.21f,.38f,.21f)}
            FurnitureType.WARDROBE,FurnitureType.CABINET->{rect(.13f,.08f,.74f,.82f);line(.5f,.08f,.5f,.9f);line(.42f,.44f,.42f,.56f);line(.58f,.44f,.58f,.56f)}
            FurnitureType.DRAWER->{rect(.12f,.17f,.76f,.66f);for(i in 1..2)line(.12f,.17f+i*.22f,.88f,.17f+i*.22f);for(i in 0..2)line(.43f,.28f+i*.22f,.57f,.28f+i*.22f)}
            FurnitureType.PANTRY,FurnitureType.SHELF->{rect(.14f,.08f,.72f,.82f);for(i in 1..3)line(.14f,.08f+i*.205f,.86f,.08f+i*.205f)}
            FurnitureType.BOX->{rect(.12f,.28f,.76f,.55f);rect(.08f,.18f,.84f,.15f);line(.42f,.44f,.58f,.44f)}
            FurnitureType.OTHER->{rect(.14f,.14f,.72f,.72f);line(.37f,.5f,.63f,.5f)}
        }
    }
}
@Composable fun ProductGlyph(name:String,modifier:Modifier=Modifier){
    val type=productType(name)
    if(type in listOf(ProductType.YOGURT,ProductType.CHEESE,ProductType.MEAT,ProductType.VEGETABLES)){FoodOutline(type,modifier);return}
    val icon=when(type){
        ProductType.DAIRY->Icons.Outlined.LocalDrink
        ProductType.YOGURT->Icons.Outlined.LocalDrink
        ProductType.CHEESE->Icons.Outlined.Restaurant
        ProductType.EGGS->Icons.Outlined.Egg
        ProductType.FRUIT->Icons.Outlined.Apple
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
    Icon(icon,null,modifier,tint=MaterialTheme.colorScheme.onSurface)
}
@Composable fun CollectionTile(title:String,subtitle:String,shared:Boolean,onClick:()->Unit,icon:@Composable ()->Unit){
    Surface(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(22.dp),color=soft(if(shared)Lilac else Mint,if(shared).16f else .10f)){
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){icon()}
            Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(shared)Icon(Icons.Outlined.People,"Compartido",Modifier.size(20.dp))
            Icon(Icons.Outlined.ChevronRight,null)
        }
    }
}

@Composable private fun FoodOutline(type:ProductType,modifier:Modifier){
    val c=MaterialTheme.colorScheme.onSurface
    Canvas(modifier.padding(2.dp)){
        val w=size.width;val h=size.height;val stroke=Stroke(w*.065f,cap=StrokeCap.Round)
        fun line(a:Float,b:Float,x:Float,y:Float)=drawLine(c,Offset(w*a,h*b),Offset(w*x,h*y),w*.065f,StrokeCap.Round)
        fun oval(a:Float,b:Float,x:Float,y:Float)=drawOval(c,Offset(w*a,h*b),Size(w*x,h*y),style=stroke)
        fun poly(vararg xy:Float){val p=androidx.compose.ui.graphics.Path();p.moveTo(w*xy[0],h*xy[1]);for(i in 2 until xy.size step 2)p.lineTo(w*xy[i],h*xy[i+1]);p.close();drawPath(p,c,style=stroke)}
        when(type){
            ProductType.YOGURT->{poly(.19f,.29f,.81f,.29f,.69f,.88f,.31f,.88f);line(.13f,.22f,.87f,.22f);line(.60f,.19f,.80f,.06f)}
            ProductType.CHEESE->{poly(.10f,.50f,.62f,.13f,.91f,.49f,.91f,.82f,.10f,.82f);line(.10f,.50f,.91f,.49f);oval(.33f,.58f,.14f,.13f);oval(.66f,.62f,.12f,.12f)}
            ProductType.MEAT->{oval(.10f,.17f,.76f,.68f);oval(.25f,.30f,.37f,.32f);line(.66f,.33f,.77f,.55f)}
            else->{poly(.19f,.40f,.52f,.32f,.67f,.49f,.24f,.91f);line(.50f,.33f,.60f,.10f);line(.55f,.33f,.83f,.17f);line(.59f,.38f,.89f,.36f);line(.30f,.52f,.40f,.60f);line(.23f,.66f,.32f,.73f)}
        }
    }
}
