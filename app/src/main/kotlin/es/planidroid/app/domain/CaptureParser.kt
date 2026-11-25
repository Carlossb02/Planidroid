package es.planidroid.app.domain

import java.time.LocalDate
import java.util.Locale

/** Local capture: explicit dates and total duration only; preserves source text. */
object CaptureParser {
    private val months=listOf("enero","febrero","marzo","abril","mayo","junio","julio","agosto","septiembre","octubre","noviembre","diciembre")
    private val datePattern=Regex("\\b(\\d{1,2})\\s+de\\s+(${months.joinToString("|")})(?:\\s+de\\s+(\\d{4}))?\\b",RegexOption.IGNORE_CASE)
    private val bullet=Regex("^\\s*(?:[-•*]|\\d+[.)])\\s+")
    fun parse(text:String,today:LocalDate=LocalDate.now()):List<Task> = text.lines().map{it.replace(bullet,"").trim()}.filter{it.isNotEmpty()}.take(30).map{line->
        val original=LocalParser.parse(line,today)
        val lower=line.lowercase(Locale("es","ES"))
        val match=datePattern.find(lower)
        val explicit=match?.let{m->runCatching{val month=months.indexOf(m.groupValues[2])+1;val year=m.groupValues[3].toIntOrNull();val d=LocalDate.of(year?:today.year,month,m.groupValues[1].toInt());if(year==null&&d<today)d.plusYears(1)else d}.getOrNull()}
        val date=explicit?.toString()?:original.due
        val title=line.replace(Regex("^(tengo que|necesito|quiero|debo)\\s+",RegexOption.IGNORE_CASE),"").substringBefore(',').trim().take(180).replaceFirstChar{it.uppercase()}.ifBlank{"Pendiente"}
        val recurring=Regex("\\b(al día|cada día|por día|diarias?|cada semana|semanales?|a la semana|por semana)\\b").containsMatchIn(lower)
        original.copy(title=title,description=if(title.equals(line,true))"" else line,category=if(date.isNotBlank())Category.DEADLINE else if(lower.contains("próximamente"))Category.SOON else Category.INBOX,due=date,minutes=if(recurring)0 else original.minutes)
    }
}
