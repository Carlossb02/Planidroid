package es.planidroid.app.domain
import java.time.LocalDate

class LocalAi:AiProvider {
    override suspend fun parseNaturalLanguage(text:String):AiProposal {
        val lines=text.lines().map{it.trim()}.filter{it.isNotBlank()}.take(30)
        val tasks=CaptureParser.parse(lines.joinToString("\n"))
        return AiProposal(tasks,uncertainties=if(tasks.any{it.minutes==0})listOf("La duración no está indicada inequívocamente.")else emptyList(),assumptions=listOf("Cada línea se interpreta como una tarea independiente."),confidence=0.5)
    }
    override suspend fun suggestTaskBreakdown(task:Task):AiProposal {
        val parts=task.checks.filterNot{it.done}.map{Phase(title=it.title)}
        return AiProposal(listOf(task.copy(phases=if(task.phases.isNotEmpty())task.phases else parts)),uncertainties=if(parts.isEmpty())listOf("No hay objetivos para desglosar. Añádelos manualmente.")else listOf("Indica duración de cada fase."),confidence=0.5)
    }
    override suspend fun estimateEffort(task:Task)=AiProposal(listOf(task.copy(minutes=task.minutes.takeIf{it>0}?:task.phases.sumOf{it.minutes})),uncertainties=listOf("La estimación solo usa datos que has introducido."),confidence=0.5)
    override suspend fun suggestSchedule(task:Task)=estimateEffort(task)
    override suspend fun summarizeTask(task:Task)="${task.title}: ${task.status.label}; ${task.minutes} min estimados; ${task.phases.size} fases; ${task.checks.count{it.done}}/${task.checks.size} objetivos."
    override suspend fun answerWithUserContext(question:String,tasks:List<Task>):String {val active=tasks.filter{it.status !in listOf(Status.DONE,Status.ARCHIVED)};val today=LocalDate.now().toString();return when{question.contains("atras",true)->active.filter{it.due.isNotBlank()&&it.due<today}.joinToString("\n"){"• ${it.title}: ${it.due}"}.ifBlank{"No hay tareas atrasadas."};question.contains("pendiente",true)->"${active.size} pendientes.\n"+active.take(20).joinToString("\n"){"• ${it.title}"};else->"Usa el planificador para distribuir sesiones o escribe «pendientes» / «atrasadas». En local no se interpretan instrucciones complejas."}}
}
