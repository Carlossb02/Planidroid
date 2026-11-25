package es.planidroid.app.data

import es.planidroid.app.domain.*
import java.time.LocalDate
import java.time.Instant

object BackupIO {
    fun validate(text: String): Backup {
        require(text.length <= 8_000_000) { "Copia demasiado grande" }
        val b = decode<Backup>(text)
        require(b.schemaVersion == 1 && b.entries.size <= 20000) { "Versión o tamaño no admitidos" }
        val ids = b.entries.map { it.id }.toSet()
        require(ids.size == b.entries.size)
        val types = b.entries.associate { it.id to it.kind }
        for (e in b.entries) {
            require(e.id.matches(Regex("[A-Za-z0-9_-]{1,100}")) && e.payload.length < 200000)
            when(e.kind) {
                Kind.TASK -> decode<Task>(e.payload).let { require(it.id==e.id && it.title.isNotBlank() && it.minutes in 0..100000); if(it.due.isNotBlank()) LocalDate.parse(it.due); require(it.groupId.isEmpty() || types[it.groupId]==Kind.GROUP);require(it.dependencies.all{d->types[d]==Kind.TASK});require(it.reminderBeforeDays in 0..30);java.time.LocalTime.parse(it.reminderTime);if(it.startDate.isNotBlank())LocalDate.parse(it.startDate) }
                Kind.SESSION -> decode<Session>(e.payload).let { require(it.id==e.id && it.end>it.start && types[it.taskId]==Kind.TASK) }
                Kind.FURNITURE -> decode<Furniture>(e.payload).let { require(it.id==e.id && it.name.isNotBlank() && it.compartments.isNotEmpty() && it.zones.map{z->z.name}.distinct().size==it.zones.size && it.zones.all{z->LayoutGrid.fits(z,it.zones)}) }
                Kind.ITEM -> decode<Item>(e.payload).let { require(it.id==e.id && it.name.isNotBlank() && types[it.furnitureId]==Kind.FURNITURE && it.amount.isFinite() && it.amount>=0); if(it.expiry.isNotBlank()) LocalDate.parse(it.expiry) }
                Kind.GROUP -> decode<Group>(e.payload).let { require(it.id==e.id && it.name.isNotBlank()) }
                Kind.SHOPPING -> decode<Shopping>(e.payload).let { require(it.id==e.id && it.name.isNotBlank() && it.quantity>0 && it.minimum>=0 && it.target>=it.minimum) }
                Kind.PURCHASE -> decode<Purchase>(e.payload).let { require(it.id==e.id && it.quantity>0 && it.name.isNotBlank()) }
            }
        }
        val tasks=b.entries.filter{it.kind==Kind.TASK}.map{decode<Task>(it.payload)};require(tasks.none{TaskConstraints.cycle(it,tasks)}){"Dependencias circulares"}
        return b
    }
    fun create(rows: List<Record>, theme: String, glass: Boolean): Backup = Backup(exportDate=Instant.now().toString(), appVersion=es.planidroid.app.BuildConfig.VERSION_NAME, entries=rows.filterNot { it.deleted }.map { Entry(it.id,Kind.valueOf(it.kind),it.payload) },theme=theme,glass=glass)
    fun remap(b: Backup,keepFiles:Boolean=false): List<Entry> {
        val map = b.entries.associate { it.id to newId() }
        return b.entries.map { e ->
            val id=map.getValue(e.id)
            val payload=when(e.kind) {
                Kind.TASK -> encode(decode<Task>(e.payload).let { it.copy(id=id,groupId=map[it.groupId].orEmpty(), attachments=it.attachments.map{a->a.copy(uri=if(keepFiles)a.uri else "")},dependencies=it.dependencies.mapNotNull{d->map[d]},seriesId="") })
                Kind.SESSION -> encode(decode<Session>(e.payload).let { it.copy(id=id,taskId=map.getValue(it.taskId)) })
                Kind.FURNITURE -> encode(decode<Furniture>(e.payload).let{it.copy(id=id,photo=if(keepFiles)it.photo else "")})
                Kind.ITEM -> encode(decode<Item>(e.payload).let { it.copy(id=id,furnitureId=map.getValue(it.furnitureId),photo=if(keepFiles)it.photo else "") })
                Kind.GROUP -> encode(decode<Group>(e.payload).copy(id=id))
                Kind.SHOPPING -> encode(decode<Shopping>(e.payload).let { it.copy(id=id,itemId=map[it.itemId].orEmpty()) })
                Kind.PURCHASE -> encode(decode<Purchase>(e.payload).let { it.copy(id=id,shoppingId=map[it.shoppingId].orEmpty(),itemId=map[it.itemId].orEmpty(),stockBefore=null,stockAfter=null) })
            }; Entry(id,e.kind,payload)
        }
    }
}
