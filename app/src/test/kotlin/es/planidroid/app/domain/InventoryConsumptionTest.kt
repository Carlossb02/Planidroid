package es.planidroid.app.domain
import org.junit.Assert.*
import org.junit.Test
class InventoryConsumptionTest{
    @Test fun partialAndFullConsumption(){
        val i=Item(furnitureId="f",name="Leche",amount=1.5,unit="L",expiry="2027-01-01")
        val remaining=InventoryConsumption.consume(i,0.5)
        assertEquals(1.0,remaining.amount,0.0);assertFalse(InventoryConsumption.pending(remaining))
        val empty=InventoryConsumption.consume(remaining,1.0)
        assertEquals(0.0,empty.amount,0.0);assertEquals("0",empty.quantity);assertTrue(InventoryConsumption.pending(empty));assertEquals("",empty.expiry);assertEquals("f",empty.furnitureId)
    }
    @Test fun decimalsAndInvalidAmounts(){
        val i=Item(furnitureId="f",name="Arroz",amount=0.3,unit="kg")
        val next=InventoryConsumption.consume(i,0.1)
        assertEquals(0.2,next.amount,0.0)
        assertTrue(runCatching{InventoryConsumption.consume(i,0.4)}.isFailure)
        assertTrue(runCatching{InventoryConsumption.consume(i,Double.NaN)}.isFailure)
    }
    @Test fun distinguishRecipesAndIngredients(){
        val i=Item(furnitureId="f",name="Arroz")
        assertEquals(InventoryKind.INGREDIENT,InventoryConsumption.kind(i))
        assertEquals(InventoryKind.RECIPE,InventoryConsumption.kind(i.copy(name="Paella")))
        assertEquals(InventoryKind.OTHER,InventoryConsumption.kind(i.copy(name="Detergente")))
        assertEquals(InventoryKind.RECIPE,InventoryConsumption.kind(i.copy(contentKind=InventoryKind.RECIPE)))
    }
    @Test fun avoidDuplicatePendingAndPreserveState(){
        val i=InventoryConsumption.consume(Item(furnitureId="f",name="Leche",amount=1.0,unit="L"),1.0)
        val copy=decode<Item>(encode(i));assertTrue(InventoryConsumption.pending(copy))
        val s=Shopping(name="LECHE",quantity=1.0,unit="litros",listId="l")
        val p=Shopping(name="Leche",quantity=1.0,unit="L",listId="l",itemId=i.id)
        assertTrue(InventoryConsumption.alreadyPending(p,listOf(s),i))
        assertFalse(InventoryConsumption.alreadyPending(p,listOf(s.copy(pending=false)),i))
        assertFalse(InventoryConsumption.alreadyPending(p,listOf(s.copy(listId="other")),i))
    }
}
