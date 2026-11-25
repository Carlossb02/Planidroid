package es.planidroid.app.ui
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*
import kotlinx.coroutines.delay
@Composable fun FocusScreen(tasks:List<Task>,log:(String,Int)->Unit){val active=tasks.filter{it.status !in listOf(Status.DONE,Status.ARCHIVED)};var task by rememberSaveable{mutableStateOf("")};var target by rememberSaveable{mutableIntStateOf(25)};var started by rememberSaveable{mutableLongStateOf(0L)};var elapsed by rememberSaveable{mutableLongStateOf(0L)};var tick by remember{mutableLongStateOf(SystemClock.elapsedRealtime())};val running=started>0;val validTask=active.any{it.id==task};val seconds=(elapsed+if(running)(tick-started).coerceAtLeast(0)else 0)/1000;val remaining=(target*60-seconds).coerceAtLeast(0);val p by animateFloatAsState((seconds.toFloat()/(target*60)).coerceIn(0f,1f),label="focus progress");LaunchedEffect(running){while(running){tick=SystemClock.elapsedRealtime();delay(1000)}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp),horizontalAlignment=Alignment.CenterHorizontally){Section("Concentración","Elige una tarea y dedica un bloque de tiempo.");Choice("Tarea",listOf("")+active.map{it.id},task,{id->active.find{it.id==id}?.title?:"Selecciona una tarea"}){if(!running&&elapsed==0L)task=it};Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(15,25,45,60,90,120).forEach{n->FilterChip(target==n,{if(!running&&elapsed==0L)target=n},label={Text("$n min")})}};Box(Modifier.size(210.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(progress={p},modifier=Modifier.fillMaxSize(),strokeWidth=12.dp,color=Mint,trackColor=soft(Mint,.15f));Column(horizontalAlignment=Alignment.CenterHorizontally){Text("%02d:%02d".format(remaining/60,remaining%60),style=MaterialTheme.typography.headlineLarge);Text(if(remaining==0L)"Objetivo alcanzado" else if(running)"En marcha" else "Preparado")}};Button(enabled=validTask,onClick={if(running){elapsed+=(SystemClock.elapsedRealtime()-started).coerceAtLeast(0);started=0}else{tick=SystemClock.elapsedRealtime();started=tick}}){Text(if(running)"Pausar" else if(elapsed>0)"Continuar" else "Iniciar")};OutlinedButton(enabled=validTask&&seconds>=60,onClick={val minutes=(seconds/60).toInt().coerceAtMost(1440);log(task,minutes);started=0;elapsed=0}){Text("Finalizar y registrar ${seconds/60} min")};TextButton(enabled=running||elapsed>0,onClick={started=0;elapsed=0}){Text("Descartar sesión")};if(!validTask&&task.isNotBlank())Text("La tarea ya no está disponible. Descarta esta sesión para elegir otra.",color=MaterialTheme.colorScheme.error);Text("El tiempo se registra al finalizar; no se añade automáticamente al calendario.",style=MaterialTheme.typography.bodySmall)}
}
