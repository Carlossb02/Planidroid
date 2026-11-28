package es.planidroid.app.ui

import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import es.planidroid.app.domain.Status
import es.planidroid.app.domain.Task
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable fun FocusScreen(tasks:List<Task>,log:(String,Int)->Unit){
    val active=tasks.filter{it.status !in listOf(Status.DONE,Status.ARCHIVED)}
    var task by rememberSaveable{mutableStateOf("")}
    var target by rememberSaveable{mutableIntStateOf(25)}
    var started by rememberSaveable{mutableLongStateOf(0)}
    var elapsed by rememberSaveable{mutableLongStateOf(0)}
    var ambience by rememberSaveable{mutableStateOf(FocusAmbience.SILENT)}
    var volume by rememberSaveable{mutableFloatStateOf(.55f)}
    var animated by rememberSaveable{mutableStateOf(true)}
    var tick by remember{mutableLongStateOf(SystemClock.elapsedRealtime())}
    val running=started>0
    val validTask=active.any{it.id==task}
    val taskAvailable=task.isBlank()||validTask
    val duration=target*60_000L
    val total=(elapsed+if(running)(tick-started).coerceAtLeast(0) else 0).coerceAtMost(duration)
    val seconds=total/1000
    val remaining=target*60-seconds
    val complete=remaining==0L
    val progress by animateFloatAsState(total.toFloat()/duration,label="Progreso de concentración")
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle){mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}

    DisposableEffect(lifecycle){
        val observer=LifecycleEventObserver{source,_->
            foreground=source.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(started,duration,foreground){
        if(started==0L||!foreground)return@LaunchedEffect
        while(started!=0L){
            tick=SystemClock.elapsedRealtime()
            if(elapsed+tick-started>=duration){
                elapsed=duration
                started=0
            }else delay(250)
        }
    }
    val audioUnavailable=FocusSound(ambience,playing=foreground&&running&&!complete,volume)

    fun clear(){started=0;elapsed=0}
    fun toggleTimer(){
        if(running){
            elapsed=(elapsed+(SystemClock.elapsedRealtime()-started).coerceAtLeast(0)).coerceAtMost(duration)
            started=0
        }else{
            tick=SystemClock.elapsedRealtime()
            started=tick
        }
    }

    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){
        Column(Modifier.widthIn(max=660.dp).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start=20.dp,end=20.dp,top=12.dp,bottom=28.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)){
            FocusTimer(ambience,animated&&foreground,progress,remaining,
                when{
                    complete->"Objetivo alcanzado"
                    running->"En marcha"
                    total>0->"En pausa"
                    else->"Preparado"
                })
            ActionFlow{
                Button(enabled=taskAvailable&&!complete,onClick=::toggleTimer){
                    Icon(if(running)Icons.Default.Pause else Icons.Default.PlayArrow,null)
                    Spacer(Modifier.width(8.dp))
                    Text(if(running)"Pausar" else if(elapsed>0)"Continuar" else "Iniciar")
                }
                if(validTask){
                    OutlinedButton(enabled=seconds>=60,onClick={
                        log(task,(seconds/60).toInt());clear()
                    }){Text("Finalizar y registrar "+seconds/60+" min")}
                }
                if(running||elapsed>0)TextButton(onClick=::clear){
                    Text(if(task.isBlank())"Finalizar sesión" else "Descartar sesión")
                }
            }
            if(!taskAvailable)Text("La tarea ya no está disponible. Descarta esta sesión para elegir otra.",
                color=MaterialTheme.colorScheme.error)
            if(!running&&elapsed==0L){
                Choice("Tarea (opcional)",listOf("")+active.map{it.id},task,
                    {id->active.find{it.id==id}?.title?:"Sesión libre"}){task=it}
                ActionFlow{
                    listOf(15,25,45,60,90,120).forEach{minutes->
                        FilterChip(target==minutes,{target=minutes},label={Text("$minutes min")})
                    }
                }
            }
            GlassCard(tint=MaterialTheme.colorScheme.secondary){
                Text("Ambiente",style=MaterialTheme.typography.titleLarge)
                ActionFlow{
                    FocusAmbience.entries.forEach{scene->
                        FilterChip(ambience==scene,{ambience=scene},label={Text(scene.label)})
                    }
                }
                if(ambience!=FocusAmbience.SILENT){
                    Text("Volumen",style=MaterialTheme.typography.titleSmall)
                    Slider(volume,{volume=it},modifier=Modifier.fillMaxWidth().padding(horizontal=4.dp).semantics{contentDescription="Volumen del ambiente"})
                }
                if(audioUnavailable)Text("El audio no está disponible ahora.",color=MaterialTheme.colorScheme.error)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("Fondo animado",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                    Switch(animated,{animated=it},Modifier.semantics{contentDescription="Fondo animado"})
                }
            }
        }
    }
}

@Composable private fun FocusTimer(
    ambience:FocusAmbience,animated:Boolean,progress:Float,remaining:Long,status:String
){
    val palette=when(ambience){
        FocusAmbience.OCEAN->listOf(Color(0xFF0D344A),Color(0xFF145B67),Color(0xFF65DDCF))
        FocusAmbience.WIND->listOf(Color(0xFF283B3A),Color(0xFF426250),Color(0xFFBDDDA3))
        FocusAmbience.MUSIC->listOf(Color(0xFF30294B),Color(0xFF524370),Color(0xFFE0BEF5))
        FocusAmbience.SILENT->listOf(Color(0xFF22344F),Color(0xFF354B70),Color(0xFFBAD6FF))
    }
    val phase=if(animated&&ambience!=FocusAmbience.SILENT){
        val transition=rememberInfiniteTransition(label="Movimiento del fondo")
        transition.animateFloat(0f,1f,infiniteRepeatable(tween(12_000,easing=LinearEasing)),label="Ciclo")
    }else null
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)),contentAlignment=Alignment.Center){
        Canvas(Modifier.matchParentSize()){
            drawRect(Brush.verticalGradient(palette.take(2)))
            val offset=phase?.value?:0f
            for(line in 0..5){
                val path=Path()
                val base=size.height*(.19f+line*.13f)
                val height=size.height*(if(ambience==FocusAmbience.OCEAN).076f else .042f)
                val speed=if(line%2==0)1 else -1
                for(point in 0..30){
                    val x=point/30f
                    val angle=2*PI*(x*(1.05+line*.12)+offset*speed+line*.17)
                    val y=base+sin(angle).toFloat()*height
                    if(point==0)path.moveTo(0f,y)else path.lineTo(size.width*x,y)
                }
                if(ambience==FocusAmbience.OCEAN&&line>=3){
                    path.lineTo(size.width,size.height)
                    path.lineTo(0f,size.height)
                    path.close()
                    drawPath(path,palette[2].copy(alpha=.09f+line*.01f))
                }else drawPath(path,palette[2].copy(alpha=.13f+line*.025f),style=Stroke(width=2.dp.toPx()))
            }
        }
        Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(String.format(Locale.ROOT,"%02d:%02d",remaining/60,remaining%60),
                style=MaterialTheme.typography.displayMedium,color=Color.White)
            LinearProgressIndicator(progress={progress},modifier=Modifier.widthIn(max=220.dp).fillMaxWidth(),
                color=palette[2],trackColor=Color.White.copy(alpha=.22f))
            Text(status,style=MaterialTheme.typography.bodyMedium,color=Color.White)
        }
    }
}
