package es.planidroid.app.domain

import org.junit.Assert.*
import org.junit.Test

class FurnitureLayoutTest {
    @Test fun renamingAndRemovingACompartmentPreservesStock() {
        val shelf=Zone("Balda")
        val drawer=Zone("Cajón",ZoneType.DRAWER,y=1)
        val furniture=Furniture(name="Nevera",type=FurnitureType.FRIDGE,zones=listOf(shelf,drawer))
        val product=Item(name="Leche",furnitureId=furniture.id,compartment=shelf.name,amount=2.5,unit="l")
        val renamed=shelf.copy(name="Balda superior")
        val change=FurnitureLayout(furniture.zones).replace(shelf,renamed).remove(renamed,drawer.name)

        assertEquals(listOf(drawer),LayoutGrid.zones(change.furniture(furniture)))
        assertEquals(product.copy(compartment=drawer.name),change.item(product))
        assertEquals(product.copy(compartment=UnassignedCompartment),change.remove(drawer,UnassignedCompartment).item(product))
    }

    @Test fun lastLegacyCompartmentCanBeRemovedWithoutRecreatingIt() {
        val furniture=Furniture(name="Caja",type=FurnitureType.BOX,compartments=listOf("General"))
        val original=LayoutGrid.zones(furniture)
        val change=FurnitureLayout(original).remove(original.single(),UnassignedCompartment)
        val updated=change.furniture(furniture)
        assertTrue(updated.compartments.isEmpty())
        assertTrue(LayoutGrid.zones(updated).isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            change.furniture(furniture.copy(compartments=listOf("Cajón")))
        }
    }
}
