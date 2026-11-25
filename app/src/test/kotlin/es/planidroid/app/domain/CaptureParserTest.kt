package es.planidroid.app.domain
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
class CaptureParserTest {
    private val today=LocalDate.of(2026,11,25)
    @Test fun simpleTask(){val t=CaptureParser.parse("Llamar al dentista",today).single();assertEquals("Llamar al dentista",t.title);assertEquals("",t.due);assertEquals(0,t.minutes)}
    @Test fun explicitDateAndDuration(){val t=CaptureParser.parse("Preparar examen el 20 de diciembre, dedicar 8 horas",today).single();assertEquals("2026-12-20",t.due);assertEquals(480,t.minutes);assertEquals(Category.DEADLINE,t.category)}
    @Test fun dailyNotTotal(){val t=CaptureParser.parse("Estudiar 2 horas al día hasta el 20 de enero",today).single();assertEquals("2027-01-20",t.due);assertEquals(0,t.minutes)}
    @Test fun list(){val t=CaptureParser.parse("- Comprar leche\n- Llamar a Ana mañana",today);assertEquals(2,t.size);assertEquals("2026-11-26",t[1].due)}
    @Test fun invalidDate(){assertEquals("",CaptureParser.parse("Revisar el 31 de febrero",today).single().due)}
}
