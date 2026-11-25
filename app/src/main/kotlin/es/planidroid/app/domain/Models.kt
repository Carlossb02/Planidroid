package es.planidroid.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

fun newId() = UUID.randomUUID().toString()
val codec = Json { ignoreUnknownKeys = false; encodeDefaults = true }
@Serializable enum class Kind { TASK, SESSION, FURNITURE, ITEM, GROUP, SHOPPING, PURCHASE, HOME, SHOPPING_LIST, RECIPE }
@Serializable enum class Category(val label: String) { INBOX("Bandeja"), DEADLINE("Con fecha límite"), SOON("Próximamente"), SOMEDAY("Sin fecha") }
@Serializable enum class Status(val label: String) { PENDING("Pendiente"), ACTIVE("En curso"), BLOCKED("Bloqueada"), DONE("Completada"), ARCHIVED("Archivada") }
@Serializable enum class Priority(val label: String) { NONE("Sin prioridad"), LOW("Baja"), NORMAL("Normal"), HIGH("Alta"), URGENT("Urgente") }
@Serializable data class Check(val id: String = newId(), val title: String, val done: Boolean = false)
@Serializable data class Phase(val id: String = newId(), val title: String, val minutes: Int = 0, val checks: List<Check> = emptyList(), val done: Boolean = false, val description:String="", val actualMinutes:Int=0, val target:String="")
@Serializable data class Attachment(val name: String, val uri: String, val mime: String)
@Serializable data class Task(val id: String = newId(), val title: String, val description: String = "", val category: Category = Category.INBOX, val status: Status = Status.PENDING, val priority: Priority = Priority.NONE, val due: String = "", val minutes: Int = 0, val actualMinutes: Int = 0, val tags: List<String> = emptyList(), val checks: List<Check> = emptyList(), val phases: List<Phase> = emptyList(), val attachments: List<Attachment> = emptyList(), val contact: String = "", val phone: String = "", val groupId: String = "", val reminder: Boolean = false, val startDate:String="", val goal:String="", val dependencies:List<String> = emptyList(), val reminderBeforeDays:Int=0, val reminderTime:String="09:00", val recurrence:Recurrence=Recurrence.NONE, val seriesId:String="", val planHints:PlanHints=PlanHints(), val overlapWith:List<String> = emptyList()) {
    fun progress(): Float { if (status == Status.DONE) return 1f; val n = checks.size + phases.size; return if(n == 0) 0f else (checks.count { it.done } + phases.sumOf { p->if(p.done)1.0 else if(p.checks.isEmpty())0.0 else p.checks.count{it.done}.toDouble()/p.checks.size }).toFloat() / n }
}
@Serializable data class Session(val id: String = newId(), val taskId: String, val start: Long, val end: Long, val done: Boolean = false, val phaseId: String = "", val calendarTitle: String = "", val isBreak:Boolean=false)
@Serializable enum class FurnitureType(val label: String) { FRIDGE("Nevera"), FREEZER("Congelador"), WARDROBE("Armario"), DRAWER("Cajonera"), PANTRY("Despensa"), SHELF("Estantería"), CABINET("Mueble"), BOX("Caja"), WASHER("Lavadora"), OTHER("Otro") }
@Serializable data class Furniture(val id: String = newId(), val name: String, val type: FurnitureType, val room: String = "", val compartments: List<String> = listOf("General"), val notes: String = "", val zones: List<Zone> = emptyList(), val photo: String = "", val homeId:String="")
@Serializable data class Item(val id: String = newId(), val furnitureId: String, val name: String, val compartment: String = "General", val quantity: String = "1", val expiry: String = "", val notes: String = "", val amount: Double = 1.0, val unit: String = "ud", val icon: String = "📦", val photo: String = "",val contentKind:InventoryKind=InventoryKind.AUTO,val restockAmount:Double=0.0)
@Serializable data class Group(val id: String = newId(), val name: String, val description: String = "")
@Serializable data class Entry(val id: String, val kind: Kind, val payload: String)
@Serializable data class Backup(val schemaVersion: Int = 1, val exportDate: String, val appVersion: String = "unknown", val entries: List<Entry>, val theme: String = "Sistema", val glass: Boolean = false)
inline fun <reified T> encode(value: T) = codec.encodeToString(kotlinx.serialization.serializer<T>(), value)
inline fun <reified T> decode(value: String): T = codec.decodeFromString(kotlinx.serialization.serializer<T>(), value)

@Serializable data class Home(val id:String=newId(),val name:String)
@Serializable data class ShoppingList(val id:String=newId(),val name:String)
