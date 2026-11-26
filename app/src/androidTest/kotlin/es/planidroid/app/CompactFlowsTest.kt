package es.planidroid.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.Shopping
import es.planidroid.app.domain.Zone
import es.planidroid.app.ui.ActionFlow
import es.planidroid.app.ui.FurnitureMap
import es.planidroid.app.ui.PlanidroidTheme
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

    @Test fun furnitureDoorCanBeOpenedAndSelectedOnCompactWidth() {
        var selected:String?=null
        compose.setContent {
            PlanidroidTheme("Oscuro",false) {
                Box(Modifier.width(230.dp)) {
                    FurnitureMap(listOf(Zone("Balda",h=2),Zone("Botellas",door=true,h=2)),emptyList(),null){selected=it}
                }
            }
        }
        compose.onNodeWithText("Puerta").performClick()
        compose.onNodeWithText("Botellas").assertIsDisplayed().performClick()
        compose.runOnIdle{assertEquals("Botellas",selected)}
    }
}
