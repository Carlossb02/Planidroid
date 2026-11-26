package es.planidroid.app.domain

import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.UUID

internal data class CalendarDraft(
    val sourceId:String,
    val title:String,
    val start:Long,
    val end:Long,
    val allDay:Boolean=false,
    val location:String="",
    val notes:String="",
    val reminder:Boolean=false,
    val blocksTime:Boolean=true
)

internal data class CalendarFile(
    val events:List<CalendarDraft>,
    val skipped:Int,
    val recurrence:Boolean,
    val assumedTimeZone:Boolean
)

internal object CalendarFiles {
    const val FREE_TAG="planidroid-calendar-free"
    const val MAX_BYTES=1_048_576
    const val MAX_EVENTS=200
    private val datePattern=DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT)
    private val dateTimePattern=DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmm[ss]").withResolverStyle(ResolverStyle.STRICT)
    private val dateOnly=Regex("[0-9]{8}")
    private val offsetAtEnd=Regex("[+-][0-9]{4}$")
    private val durationPattern=Regex("P(?:(\\d{1,3})W)?(?:(\\d{1,3})D)?(T[0-9HMS]+)?")
    private val legacyZones=mapOf(
        "Romance Standard Time" to "Europe/Madrid",
        "W. Europe Standard Time" to "Europe/Berlin",
        "GMT Standard Time" to "Europe/London",
        "Eastern Standard Time" to "America/New_York",
        "Central Standard Time" to "America/Chicago",
        "Pacific Standard Time" to "America/Los_Angeles"
    )
    private data class Property(val name:String,val value:String,val parameters:Map<String,String>)
    private data class DateValue(val time:ZonedDateTime,val allDay:Boolean,val assumedZone:Boolean=false)

    fun parse(bytes:ByteArray,zone:ZoneId=ZoneId.systemDefault()):CalendarFile {
        require(bytes.isNotEmpty()&&bytes.size<=MAX_BYTES){"El archivo está vacío o supera 1 MB."}
        val lines=legacyLines(unfold(bytes).toString(Charsets.UTF_8).removePrefix("\uFEFF"))
        var calendar=false
        var calendarFound=false
        var cancellation=false
        var atEvent=false
        var nesting=0
        var skipped=0
        var recurrence=false
        var assumedTimeZone=false
        var eventCount=0
        val current=linkedMapOf<String,Property>()
        val events=linkedMapOf<String,CalendarDraft>()
        for(line in lines){
            val property=property(line)?:continue
            val component=property.value.trim().uppercase(Locale.ROOT)
            when {
                property.name=="BEGIN"&&component=="VCALENDAR"&&!atEvent -> {
                    calendar=true;calendarFound=true;cancellation=false
                }
                property.name=="END"&&component=="VCALENDAR" -> {
                    if(atEvent){skipped++;atEvent=false}
                    calendar=false
                }
                calendar&&!atEvent&&property.name=="METHOD" -> cancellation=component=="CANCEL"
                calendar&&property.name=="BEGIN"&&component=="VEVENT"&&!atEvent -> {
                    require(++eventCount<=MAX_EVENTS){"El archivo supera los 200 eventos. Exporta un intervalo más corto."}
                    atEvent=true;nesting=0;current.clear()
                }
                atEvent&&property.name=="END"&&component=="VEVENT"&&nesting==0 -> {
                    atEvent=false
                    if(cancellation||current["STATUS"]?.value.equals("CANCELLED",true)){skipped++;continue}
                    val found=runCatching{event(current,zone)}.getOrNull()
                    if(found==null){skipped++;continue}
                    recurrence=recurrence||current.keys.any{it in setOf("RRULE","RDATE","EXRULE","EXDATE")}
                    assumedTimeZone=assumedTimeZone||found.second
                    val draft=found.first
                    if(events.putIfAbsent(draft.sourceId,draft)!=null)skipped++
                }
                atEvent&&property.name=="BEGIN" -> nesting++
                atEvent&&property.name=="END" -> if(nesting>0)nesting-- else skipped++
                atEvent&&nesting==0 -> current.putIfAbsent(property.name,property)
            }
        }
        require(calendarFound){"El archivo no tiene formato iCalendar o vCalendar."}
        if(atEvent)skipped++
        return CalendarFile(events.values.sortedBy{it.start},skipped,recurrence,assumedTimeZone)
    }

    private fun event(properties:Map<String,Property>,zone:ZoneId):Pair<CalendarDraft,Boolean>{
        val startProperty=requireNotNull(properties["DTSTART"])
        val start=date(startProperty,zone)
        val end=properties["DTEND"]?.let{date(it,zone,start.time.zone)}
        require(end==null||end.allDay==start.allDay)
        val finish=end?.time?:properties["DURATION"]?.value?.let{durationEnd(start.time,it)}
            ?:if(start.allDay)start.time.plusDays(1)else start.time.plusHours(1)
        require(start.time.year in 1900..2200&&finish.year in 1900..2200)
        val from=start.time.toInstant().toEpochMilli()
        val until=finish.toInstant().toEpochMilli()
        require(until>from&&until-from<=367L*86_400_000L)
        val title=properties["SUMMARY"]?.let(::text)?.trim()?.take(180)?.ifBlank{"Evento"}?:"Evento"
        val location=properties["LOCATION"]?.let(::text).orEmpty().take(300)
        val notes=properties["DESCRIPTION"]?.let(::text).orEmpty().take(8000)
        val uid=properties["UID"]?.value?.trim()?.take(512).orEmpty()
        val occurrence=properties["RECURRENCE-ID"]?.value?.trim()?.take(120).orEmpty()
        val identity=if(uid.isBlank())"fields:"+listOf(title,from,until,location).joinToString("|") else "uid:"+uid+"|"+occurrence
        val id=UUID.nameUUIDFromBytes(("planidroid-calendar:"+identity).toByteArray(Charsets.UTF_8)).toString()
        val blocksTime=!properties["TRANSP"]?.value?.trim().equals("TRANSPARENT",true)
        return CalendarDraft(id,title,from,until,start.allDay,location,notes,blocksTime=blocksTime) to (start.assumedZone||end?.assumedZone==true)
    }

    private fun date(property:Property,zone:ZoneId,defaultZone:ZoneId=zone):DateValue {
        val value=property.value.trim().uppercase(Locale.ROOT)
        if(dateOnly.matches(value))return DateValue(LocalDate.parse(value,datePattern).atStartOfDay(zone),true)
        val timeZone=property.parameters["TZID"]?.trim('"')
        if(value.endsWith("Z"))return DateValue(LocalDateTime.parse(value.dropLast(1),dateTimePattern).atZone(ZoneOffset.UTC),false)
        val offset=offsetAtEnd.find(value)
        if(offset!=null){
            val parsed=LocalDateTime.parse(value.substring(0,offset.range.first),dateTimePattern)
            return DateValue(parsed.atZone(ZoneOffset.of(offset.value)),false)
        }
        val named=timeZone?.let{runCatching{ZoneId.of(legacyZones[it]?:it)}.getOrNull()}
        return DateValue(LocalDateTime.parse(value,dateTimePattern).atZone(named?:defaultZone),false,timeZone!=null&&named==null)
    }

    private fun durationEnd(start:ZonedDateTime,value:String):ZonedDateTime {
        val match=requireNotNull(durationPattern.matchEntire(value.trim().uppercase(Locale.ROOT)))
        val weeks=match.groupValues[1].toLongOrNull()?:0
        val days=match.groupValues[2].toLongOrNull()?:0
        val clock=match.groupValues[3].takeIf{it.isNotEmpty()}?.let{Duration.parse("P"+it)}?:Duration.ZERO
        return start.plusDays(weeks*7+days).plus(clock)
    }

    private fun property(line:String):Property?{
        var quoted=false
        var colon=-1
        for(index in line.indices){
            if(line[index]=='"')quoted=!quoted
            if(line[index]==':'&&!quoted){colon=index;break}
        }
        if(colon<1)return null
        val header=line.substring(0,colon).split(';')
        val name=header.first().uppercase(Locale.ROOT)
        val parameters=header.drop(1).mapNotNull{token->
            val eq=token.indexOf('=')
            if(eq<1)null else token.substring(0,eq).uppercase(Locale.ROOT) to token.substring(eq+1)
        }.toMap()
        return Property(name,line.substring(colon+1),parameters)
    }

    private fun text(p:Property):String {
        val value=if(p.parameters["ENCODING"].equals("QUOTED-PRINTABLE",true))quotedPrintable(p)else p.value
        return buildString {
            var i=0
            while(i<value.length){
                val character=value[i++]
                if(character=='\\'&&i<value.length){
                    val escaped=value[i++]
                    if(escaped=='n'||escaped=='N')append('\n')else append(escaped)
                }else append(character)
            }
        }
    }

    private fun quotedPrintable(p:Property):String{
        val output=ByteArrayOutputStream(p.value.length)
        var index=0
        while(index<p.value.length){
            if(p.value[index]=='='&&index+2<p.value.length){
                val byte=p.value.substring(index+1,index+3).toIntOrNull(16)
                if(byte!=null){output.write(byte);index+=3;continue}
            }
            output.write(p.value[index++].code and 255)
        }
        val charset=if(p.parameters["CHARSET"].equals("ISO-8859-1",true))Charsets.ISO_8859_1 else Charsets.UTF_8
        return output.toByteArray().toString(charset)
    }

    private fun legacyLines(text:String)=sequence {
        val lines=text.lineSequence().iterator()
        while(lines.hasNext()){
            var line=lines.next()
            val header=line.substringBefore(':')
            if(header.contains("ENCODING=QUOTED-PRINTABLE",ignoreCase=true)&&line.endsWith('=')){
                val joined=StringBuilder(line)
                while(joined.endsWith('=')&&lines.hasNext()){
                    joined.setLength(joined.length-1)
                    joined.append(lines.next())
                }
                line=joined.toString()
            }
            yield(line)
        }
    }

    private fun unfold(bytes:ByteArray):ByteArray {
        val out=ByteArrayOutputStream(bytes.size)
        var index=0
        while(index<bytes.size){
            val value=bytes[index++].toInt() and 255
            if(value==13||value==10){
                if(value==13&&bytes.getOrNull(index)?.toInt()==10)index++
                val next=bytes.getOrNull(index)?.toInt()
                if(next==32||next==9)index++ else out.write(10)
            }else out.write(value)
        }
        return out.toByteArray()
    }
}
