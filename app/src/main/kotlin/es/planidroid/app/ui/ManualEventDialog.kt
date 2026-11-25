package es.planidroid.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.*
@Composable fun ManualEventDialog(day:LocalDate,close:()->Unit,save:(String,Long,Long,Boolean)->Unit){
    var title by remember{mutableStateOf("")};var date by remember{mutableStateOf(day.toString())}
    var start by remember{mutableStateOf("09:00")};var duration by remember{mutableStateOf("60")}
    var reminder by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text("Nuevo evento")},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Field(title,{title=it.take(180)},"Título");DateField(date,{date=it},"Fecha",false)
        Field(start,{start=it},"Hora HH:mm");Field(duration,{duration=it},"Duración en minutos")
        Row{Checkbox(reminder,{reminder=it});Text("Recordatorio al inicio",Modifier.padding(top=12.dp))}
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(enabled=title.isNotBlank(),onClick={runCatching{val begin=LocalDate.parse(date).atTime(LocalTime.parse(start)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();val n=duration.toInt();require(n in 1..1440);begin to begin+n*60_000L}.onSuccess{save(title,it.first,it.second,reminder)}.onFailure{error="Fecha, hora o duración inválidas"}}){Text("Guardar")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})
}
