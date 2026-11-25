package es.planidroid.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*
import java.time.LocalDate
@Composable fun PhaseDetail(p:Phase,close:()->Unit,save:(Phase)->Unit){var phase by remember(p){mutableStateOf(p)};var goal by remember{mutableStateOf("")};var planned by remember{mutableStateOf(p.minutes.toString())};var actual by remember{mutableStateOf(p.actualMinutes.toString())};val valid=phase.title.isNotBlank()&&(planned.toIntOrNull()?:-1) in 0..100000&&(actual.toIntOrNull()?:-1) in 0..100000&&(phase.target.isBlank()||runCatching{LocalDate.parse(phase.target)}.isSuccess);AlertDialog(onDismissRequest=close,title={Text("Fase")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Field(phase.title,{phase=phase.copy(title=it)},"Título");Field(phase.description,{phase=phase.copy(description=it)},"Descripción",false);Field(planned,{planned=it},"Minutos previstos");Field(actual,{actual=it},"Minutos realizados");DateField(phase.target,{phase=phase.copy(target=it)},"Fecha objetivo");phase.checks.forEach{c->Row{Checkbox(c.done,{v->phase=phase.copy(checks=phase.checks.map{if(it.id==c.id)it.copy(done=v)else it})});Text(c.title,Modifier.weight(1f));TextButton(onClick={phase=phase.copy(checks=phase.checks-c)}){Text("Quitar")}}};Field(goal,{goal=it},"Nuevo objetivo");TextButton(enabled=goal.isNotBlank(),onClick={phase=phase.copy(checks=phase.checks+Check(title=goal));goal=""}){Text("Añadir")}}},confirmButton={TextButton(enabled=valid,onClick={save(phase.copy(minutes=planned.toInt(),actualMinutes=actual.toInt()))}){Text("Guardar")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})}
