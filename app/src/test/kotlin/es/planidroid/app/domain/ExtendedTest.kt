package es.planidroid.app.domain
import org.junit.Test
import org.junit.Assert.*
import java.time.*
class ExtendedTest {
 @Test fun compartmentCollisionAndDoor(){val z=Zone("Balda",h=2);assertFalse(LayoutGrid.fits(Zone("Cajón",y=1),listOf(z)));assertTrue(LayoutGrid.fits(Zone("Puerta",door=true),listOf(z)));assertEquals(2,LayoutGrid.place("Cajón",ZoneType.DRAWER,false,listOf(z),4,2)?.y)}
 @Test fun automaticShopping(){val i=Item(furnitureId="f",name="Leche",amount=1.0);val s=Shopping(name="Leche",routine=true,pending=false,minimum=2.0,target=5.0,itemId=i.id);assertEquals(4.0,ShoppingLogic.needed(s,i),0.0);assertEquals(0.0,ShoppingLogic.needed(s,i.copy(amount=5.0)),0.0)}
 @Test fun localParsing(){val d=LocalDate.of(2027,1,4);val t=LocalParser.parse("Estudiar mañana 2 horas",d);assertEquals("2027-01-05",t.due);assertEquals(120,t.minutes);assertEquals(0,LocalParser.parse("Estudiar 2 horas al día",d).minutes)}
 @Test fun multiTaskNoOverlap(){val d=LocalDate.of(2027,1,4);val r=AgendaPlanner.plan(listOf(AgendaInput("a","Primera",90,d,0,listOf("fase" to 45)),AgendaInput("b","Segunda",90,d.plusDays(1),4)),d,d.plusDays(1),setOf(DayOfWeek.MONDAY,DayOfWeek.TUESDAY),LocalTime.of(9,0),LocalTime.of(10,0),60,emptyList(),ZoneOffset.UTC,0);assertEquals("a",r.blocks.first().taskId);assertTrue(r.blocks.zipWithNext().all{(a,b)->a.end<=b.start});assertEquals(60,r.deficits.sumOf{it.minutes})}
}
