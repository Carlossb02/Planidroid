package es.planidroid.app.domain

import java.time.LocalDate

/** Conservative, offline interpretation: never guesses a deadline or duration. */
object LocalParser {
    fun parse(text:String,today:LocalDate=LocalDate.now()):Task {
        val lower=text.lowercase(java.util.Locale("es","ES"));val iso=Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b").find(text)?.value
        val date=when{iso!=null->runCatching{LocalDate.parse(iso)}.getOrNull();"pasado mañana" in lower->today.plusDays(2);Regex("\\bmañana\\b").containsMatchIn(lower)->today.plusDays(1);Regex("\\bhoy\\b").containsMatchIn(lower)->today;else->null}
        val duration=Regex("\\b(\\d{1,4})\\s*(horas?|h|minutos?|min)\\b").find(lower)
        val minutes=duration?.let{val n=it.groupValues[1].toInt();if(it.groupValues[2].startsWith("h"))n*60 else n}?:0
        return Task(title=text.lines().firstOrNull{it.isNotBlank()}?.trim()?.take(180)?:"Pendiente",description=text.trim(),due=date?.toString().orEmpty(),category=if(date!=null)Category.DEADLINE else Category.INBOX,minutes=if("al día" in lower||"cada día" in lower||"por día" in lower)0 else minutes)
    }
}
