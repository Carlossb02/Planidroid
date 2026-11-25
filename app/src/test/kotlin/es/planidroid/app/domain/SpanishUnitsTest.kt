package es.planidroid.app.domain
import org.junit.Assert.*
import org.junit.Test
class SpanishUnitsTest{
    @Test fun metricAndRetailAliases(){
        assertEquals("L",SpanishUnits.canonical("litros"));assertEquals("g",SpanishUnits.canonical("gramos"));assertEquals("ud",SpanishUnits.canonical("unidades"));assertEquals("cucharada",SpanishUnits.canonical("cucharadas"));assertEquals("brik",SpanishUnits.canonical("brick"));assertNull(SpanishUnits.canonical("oz"))
    }
    @Test fun noSilentQuantityConversion(){val s=Shopping(name="Leche",quantity=2.0,unit="litros",referenceUrl="https://example.org",referenceTitle="Leche");val next=decode<Shopping>(encode(s));assertEquals(2.0,next.quantity,0.0);assertEquals(s.referenceUrl,next.referenceUrl)}
    @Test fun missedSessionIsNotReserved(){val t=Task(title="Estudio",minutes=120);val s=Session(taskId=t.id,start=0,end=3600000);assertEquals(120,TaskConstraints.remaining(t,listOf(s),7200000));assertEquals(60,TaskConstraints.remaining(t,listOf(s.copy(done=true)),7200000))}
}
