package es.planidroid.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.planidroid.app.domain.*

val Blue=Color(0xFF376DAB); val Mint=Color(0xFF32816A); val Peach=Color(0xFFAD663D); val Lilac=Color(0xFF7955A5)
private val Light=lightColorScheme(primary=Blue, onPrimary=Color.White,primaryContainer=Color(0xFFE1EBFC),onPrimaryContainer=Color(0xFF24466F),secondary=Mint,secondaryContainer=Color(0xFFDCEFE5),tertiary=Lilac,background=Color(0xFFF4F6FA),surface=Color.White,surfaceVariant=Color(0xFFE9EDF3),onSurface=Color(0xFF222D3B),onSurfaceVariant=Color(0xFF596575),outline=Color(0xFF8794A5),outlineVariant=Color(0xFFDCE3EC),error=Color(0xFFB33A43),tertiaryContainer=Color(0xFFECE3FA),onTertiaryContainer=Color(0xFF4D336D))
private val Dark=darkColorScheme(primary=Color(0xFFA9C8F6),secondary=Color(0xFF9AD5B9),tertiary=Color(0xFFD4BAF7),background=Color(0xFF141922),surface=Color(0xFF202733),surfaceVariant=Color(0xFF303947),onSurface=Color(0xFFE7EBF3),onSurfaceVariant=Color(0xFFB9C4D3),outline=Color(0xFF8C9AAA),outlineVariant=Color(0xFF414C5B),primaryContainer=Color(0xFF294A71),onPrimaryContainer=Color(0xFFD9E8FF),secondaryContainer=Color(0xFF244B40),onSecondaryContainer=Color(0xFFD3F4E4),tertiaryContainer=Color(0xFF493461),onTertiaryContainer=Color(0xFFEBDDFF))
val LocalGlass=compositionLocalOf { false }
@Composable fun PlanidroidTheme(theme:String,glass:Boolean,content:@Composable ()->Unit){val dark=when(theme){"Claro"->false;"Oscuro"->true;else->isSystemInDarkTheme()}; MaterialTheme(colorScheme=if(dark)Dark else Light,typography=Typography(headlineLarge=Typography().headlineLarge.copy(fontWeight=FontWeight.SemiBold,letterSpacing=(-1).sp),headlineMedium=Typography().headlineMedium.copy(fontWeight=FontWeight.SemiBold,letterSpacing=(-0.5f).sp),titleLarge=Typography().titleLarge.copy(fontWeight=FontWeight.SemiBold),titleMedium=Typography().titleMedium.copy(fontWeight=FontWeight.SemiBold))){CompositionLocalProvider(LocalGlass provides glass,content=content)}}
@Composable fun soft(tint:Color,alpha:Float=.12f)=lerp(MaterialTheme.colorScheme.surface,tint,alpha)
@Composable fun GlassCard(modifier:Modifier=Modifier,tint:Color=MaterialTheme.colorScheme.primary,content:@Composable ColumnScope.()->Unit){val shape=RoundedCornerShape(20.dp);Surface(modifier.fillMaxWidth(),shape=shape,color=soft(tint,if(LocalGlass.current).24f else .12f),border=null){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}}
@Composable fun categoryColor(category:Category)=when(category){Category.DEADLINE->Peach;Category.SOON->Mint;Category.SOMEDAY->Lilac;Category.INBOX->MaterialTheme.colorScheme.primary}
@Composable fun Field(value:String,change:(String)->Unit,label:String,single:Boolean=true){OutlinedTextField(value,change,Modifier.fillMaxWidth(),label={Text(label)},singleLine=single,shape=RoundedCornerShape(14.dp))}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun <T> Choice(label:String,values:List<T>,selected:T,title:(T)->String,onChange:(T)->Unit){var open by remember{mutableStateOf(false)};ExposedDropdownMenuBox(expanded=open,onExpandedChange={open=it}){OutlinedTextField(title(selected),{},Modifier.menuAnchor().fillMaxWidth(),readOnly=true,label={Text(label)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(open)},shape=RoundedCornerShape(16.dp));ExposedDropdownMenu(expanded=open,onDismissRequest={open=false}){values.forEach{v->DropdownMenuItem(text={Text(title(v))},onClick={onChange(v);open=false})}}}}
@Composable fun Section(title:String,subtitle:String=""){Text(title,style=MaterialTheme.typography.headlineSmall);if(subtitle.isNotBlank())Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable fun Confirm(title:String,body:String,close:()->Unit,yes:()->Unit){AlertDialog(onDismissRequest=close,title={Text(title)},text={Text(body)},confirmButton={TextButton(onClick={yes();close()}){Text("Confirmar")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateField(value:String,change:(String)->Unit,label:String,optional:Boolean=true){
    var open by remember{mutableStateOf(false)}
    val initial=remember(value){runCatching{java.time.LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()}.getOrNull()}
    OutlinedTextField(value,change,Modifier.fillMaxWidth(),label={Text(label)},singleLine=true,shape=RoundedCornerShape(16.dp),trailingIcon={TextButton(onClick={open=true}){Text("Elegir")}})
    if(open){val state=rememberDatePickerState(initialSelectedDateMillis=initial);DatePickerDialog(onDismissRequest={open=false},confirmButton={TextButton(enabled=state.selectedDateMillis!=null,onClick={state.selectedDateMillis?.let{change(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())};open=false}){Text("Aceptar")}},dismissButton={Row{if(optional)TextButton(onClick={change("");open=false}){Text("Quitar")};TextButton(onClick={open=false}){Text("Cancelar")}}}){DatePicker(state)}}
}
