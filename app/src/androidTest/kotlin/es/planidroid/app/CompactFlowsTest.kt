package es.planidroid.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.Shopping
import es.planidroid.app.domain.Item
import es.planidroid.app.domain.ShoppingList
import es.planidroid.app.domain.Zone
import es.planidroid.app.ui.ActionFlow
import es.planidroid.app.ui.FurnitureCompartment
import es.planidroid.app.ui.furnitureSections
import es.planidroid.app.ui.PlanidroidTheme
import es.planidroid.app.ui.ReviewAssistantShopping
import es.planidroid.app.ui.ShoppingPaste
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompactFlowsTest {
    @get:Rule val compose=createComposeRule()

    @Test fun longActionsWrapOnNarrowScreens() {
        compose.setContent {
            PlanidroidTheme("Claro",false) {
                Box(Modifier.width(230.dp)) {
                    ActionFlow {
                        Button(onClick={}){Text("Añadir al inventario")}
                        Button(onClick={}){Text("Consultar con Gemini")}
                    }
                }
            }
        }
        val add=compose.onNodeWithText("Añadir al inventario").assertIsDisplayed().getUnclippedBoundsInRoot()
        val ask=compose.onNodeWithText("Consultar con Gemini").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(ask.top>=add.bottom)
        assertTrue(ask.right<=230.dp)
    }

    @Test fun pastedShoppingListKeepsQuantitiesAndDestination() {
        var saved=emptyList<Shopping>()
        compose.setContent {
            PlanidroidTheme("Claro",false) {
                ShoppingPaste("week",{}, { saved=it })
            }
        }
        compose.onNodeWithText("Un producto por línea").performTextInput("2 kg de arroz\nleche")
        compose.onNodeWithText("Añadir",substring=false).performClick()
        compose.runOnIdle {
            assertEquals(listOf("arroz","leche"),saved.map{it.name})
            assertEquals(listOf("week","week"),saved.map{it.listId})
            assertEquals(2.0,saved.first().quantity,0.0001)
            assertEquals("kg",saved.first().unit)
            assertFalse(saved.last().quantitySpecified)
        }
    }

    @Test fun furnitureSectionsIncludeEmptyCompartmentsAndDistinguishTheDoor() {
        val zones=listOf(Zone("Botellas",door=true),Zone("Balda"))
        val sections=furnitureSections(zones,listOf(Item(name="Zumo",furnitureId="fridge",compartment="Botellas")))
        compose.setContent {
            PlanidroidTheme("Oscuro",false) {
                Column(Modifier.width(230.dp)){
                    sections.forEach{section->FurnitureCompartment(section){Text(it.name)}}
                }
            }
        }
        compose.onNodeWithText("Balda").assertIsDisplayed()
        compose.onNodeWithText("Interior · 0 productos").assertIsDisplayed()
        compose.onNodeWithText("Compartimento vacío").assertIsDisplayed()
        compose.onNodeWithText("Botellas").assertIsDisplayed()
        compose.onNodeWithText("Puerta · 1 producto").assertIsDisplayed()
        val bottles=compose.onNodeWithTag("compartment:Botellas").getUnclippedBoundsInRoot()
        val juice=compose.onNode(hasText("Zumo") and hasAnyAncestor(hasTestTag("compartment:Botellas")))
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(juice.top>bottles.top && juice.bottom<=bottles.bottom && juice.left>bottles.left)
        compose.onNodeWithText("Botellas").performClick()
        compose.onNodeWithText("Zumo").assertDoesNotExist()
        compose.onNodeWithText("Botellas").performClick()
        compose.onNodeWithText("Zumo").assertIsDisplayed()
        assertEquals(listOf("Balda","Botellas"),sections.map{it.name})
    }

    @Test fun assistantShoppingCanBeCorrectedBeforeApplyingTheProposal() {
        var selectedList:String?=null
        var saved:List<Shopping>?=null
        val entries=listOf(Shopping(name="Arroz",quantity=2.0,unit="kg"))
        val lists=listOf(ShoppingList(id="personal",name="Despensa"))
        compose.setContent {
            PlanidroidTheme("Claro",false) {
                ReviewAssistantShopping(entries,lists,{}){products,list,create,done->
                    assertFalse(create)
                    saved=products
                    selectedList=list.id
                    done(null)
                }
            }
        }
        compose.onNodeWithText("Editar",substring=false).performClick()
        compose.onNodeWithText("Cantidad",substring=false).performScrollTo().performTextReplacement("1,5")
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithText("Añadir",substring=false).performClick()
        compose.runOnIdle {
            assertEquals("personal",selectedList)
            assertEquals(1.5,saved!!.single().quantity,0.001)
        }
    }
}
