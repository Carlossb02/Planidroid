package es.planidroid.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

internal data class AssistantReply(val answer:String,val action:AssistantAction?)

internal sealed interface AssistantAction {
    data class Tasks(val entries:List<Task>):AssistantAction
    data class ShoppingItems(val entries:List<Shopping>):AssistantAction
    data class Plan(val taskId:String):AssistantAction
}

@Serializable private data class AssistantPayload(
    val answer:String="",
    val action:String="",
    val tasks:List<AssistantTaskData> = emptyList(),
    val products:List<AssistantShoppingData> = emptyList(),
    val taskId:String=""
)

@Serializable private data class AssistantTaskData(val title:String="",val due:String="",val minutes:Int=0)
@Serializable private data class AssistantShoppingData(val name:String="",val quantity:Double?=null,val unit:String="")

internal object AssistantReplies {
    private val json=Json{ignoreUnknownKeys=true;coerceInputValues=true}

    fun read(raw:String,allowedTaskIds:Set<String>):AssistantReply {
        val payload=json.decodeFromString<AssistantPayload>(raw)
        val action=when(payload.action){
            "tasks" -> payload.tasks.take(12).mapNotNull { draft ->
                val title=draft.title.trim().take(180)
                if(title.isBlank())return@mapNotNull null
                val due=runCatching{LocalDate.parse(draft.due)}.getOrNull()?.takeIf{it.year in 1900..2200}
                Task(title=title,due=due?.toString().orEmpty(),category=if(due==null)Category.INBOX else Category.DEADLINE,
                    minutes=draft.minutes.coerceIn(0,100000))
            }.takeIf{it.isNotEmpty()}?.let{AssistantAction.Tasks(it)}
            "shopping" -> payload.products.take(30).mapNotNull { draft ->
                val name=draft.name.trim().take(180)
                if(name.isBlank())return@mapNotNull null
                val quantity=draft.quantity?.takeIf{it.isFinite()&&it>0&&it<=100000}
                Shopping(name=name,quantity=quantity?:1.0,
                    unit=if(quantity==null)"" else SpanishUnits.canonical(draft.unit).orEmpty(),
                    quantitySpecified=quantity!=null,icon=guessIcon(name))
            }.takeIf{it.isNotEmpty()}?.let{AssistantAction.ShoppingItems(it)}
            "plan" -> payload.taskId.takeIf{it in allowedTaskIds}?.let{AssistantAction.Plan(it)}
            else -> null
        }
        return AssistantReply(payload.answer.trim().take(5000),action)
    }
}
