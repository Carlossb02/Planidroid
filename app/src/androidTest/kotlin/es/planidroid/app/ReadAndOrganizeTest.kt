package es.planidroid.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*
import es.planidroid.app.ui.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReadAndOrganizeTest {
    @get:Rule val compose=createComposeRule()

    @Test fun windowFrameFillsItsSafeWidth() {
        compose.setContent {
            PlanidroidTheme("Claro",false){
                BoxWithWidth {
                    WindowFrame { Box(Modifier.fillMaxSize().testTag("app frame")) }
                }
            }
        }
        val window=compose.onNodeWithTag("window").getUnclippedBoundsInRoot()
        val frame=compose.onNodeWithTag("app frame").getUnclippedBoundsInRoot()
        assertTrue("The frame must fill the host width",frame.right-frame.left>=window.right-window.left-48.dp)
    }

    @Composable private fun BoxWithWidth(body:@Composable ()->Unit){
        Box(Modifier.fillMaxSize().testTag("window")){body()}
    }

    @Test fun recentEventOpensDetailsBeforeEditing() {
        val task=Task(title="Consulta",goal="Centro de salud",description="Llevar documentación")
        val now=System.currentTimeMillis()
        val entry=Session(taskId=task.id,start=now-7_200_000,end=now-3_600_000,calendarTitle=task.title)
        var edited=false
        compose.setContent {
            PlanidroidTheme("Claro",false){
                var details by remember{mutableStateOf<Session?>(null)}
                Home(listOf(task),listOf(entry),{details=it},{},{},{},{},0,{},{})
                details?.let{AgendaEntryDetails(it,task,{details=null},{edited=true},{})}
            }
        }
        compose.onNodeWithText("Recientes").assertIsDisplayed()
        compose.onNodeWithText(task.title).performClick()
        compose.onNodeWithText(task.description).assertIsDisplayed()
        compose.onAllNodesWithText(task.goal).filterToOne(hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.runOnIdle{assertFalse(edited)}
        compose.onNodeWithContentDescription("Editar evento").performClick()
        compose.runOnIdle{assertTrue(edited)}
    }

    @Test fun homeCombinesDeadlinesAndEventsAndLinksToRecipes(){
        val today=LocalDate.now()
        val task=Task(title="Entregar el trabajo",due=today.plusDays(2).toString())
        val start=today.plusDays(1).atTime(9,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val event=Session(taskId="appointment",start=start,end=start+3_600_000,calendarTitle="Reunión")
        var recipeOpened=false
        compose.setContent{
            PlanidroidTheme("Claro",false){
                Home(listOf(task),listOf(event),{},{},{},{},{},0,{},{recipeOpened=true})
            }
        }
        compose.onNodeWithText("Mañana").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("En 2 días").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Vencimientos").assertDoesNotExist()
        compose.onNodeWithText("Gemini").assertDoesNotExist()
        compose.onNodeWithText("Recetario").performScrollTo().performClick()
        compose.runOnIdle{assertTrue(recipeOpened)}
    }

    @Test fun focusStartsAndPausesAFreeSessionWithAnAmbience(){
        compose.setContent{PlanidroidTheme("Claro",false){FocusScreen(emptyList()){_,_->}}}
        compose.onNodeWithText("Mar").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Volumen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Iniciar").performScrollTo().performClick()
        compose.onNodeWithText("En marcha").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pausar").performClick()
        compose.onNodeWithText("Continuar").assertIsDisplayed()
    }

    @Test fun recipeTitleOpensReaderWithIngredientsAndSteps() {
        val recipe=SavedRecipe(title="Ensalada de tomate",servings=2,ingredients=listOf(RecipeIngredient("Tomate",2.0,"ud")),
            steps=List(7){index->"Paso "+(index+1)+": corta el tomate y añade el aliño antes de servir."})
        compose.setContent {
            PlanidroidTheme("Claro",false){
                CompositionLocalProvider(LocalRecipeLibrary provides RecipeLibrary(listOf(recipe),null)){
                    RecipeSuggestions("local",emptyList(),"",{}, {})
                }
            }
        }
        compose.onNodeWithText("Crear receta").assertIsDisplayed()
        compose.onNodeWithText("Escanear receta").assertIsDisplayed()
        compose.onNodeWithText(recipe.title).performClick()
        compose.onNodeWithText("Número de personas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Aumentar número de personas").performClick()
        compose.onNodeWithText("3 ud").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tomate",substring=false).assertIsDisplayed()
        compose.onNodeWithText(recipe.steps.last()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Editar receta").assertIsDisplayed()
        compose.onNodeWithText("Guardar receta").assertDoesNotExist()
        compose.onNodeWithText("Elegir ingredientes para la compra").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Añadir 1 ingrediente").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Buscar receta").assertIsDisplayed()
    }

    @Test fun recipeActionsWrapAtCompactWidth() {
        var created=false
        var scanned=false
        compose.setContent {
            PlanidroidTheme("Claro",false){
                Box(Modifier.width(200.dp)){
                    RecipeLibraryActions(true,{created=true},{scanned=true})
                }
            }
        }
        val create=compose.onNodeWithText("Crear receta").assertIsDisplayed()
        val scan=compose.onNodeWithText("Escanear receta").assertIsDisplayed()
        assertTrue(scan.getUnclippedBoundsInRoot().top>=create.getUnclippedBoundsInRoot().bottom)
        create.performClick()
        scan.performClick()
        compose.runOnIdle{assertTrue(created&&scanned)}
    }

    @Test fun removeLastOccupiedCompartmentKeepsItsProducts() {
        val shelf=Zone("Balda superior")
        val furniture=Furniture(name="Nevera",type=FurnitureType.FRIDGE,zones=listOf(shelf))
        val item=Item(name="Leche",furnitureId=furniture.id,compartment=shelf.name,amount=2.0)
        var saved:FurnitureLayout?=null
        compose.setContent {
            PlanidroidTheme("Claro",false){
                LayoutEditor(furniture,listOf(item),{}){change,done->saved=change;done(null)}
            }
        }
        compose.onNodeWithContentDescription("Eliminar "+shelf.name).performClick()
        compose.onNodeWithText("Destino: Sin asignar").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText(shelf.name).assertIsDisplayed()
        compose.onNodeWithContentDescription("Eliminar "+shelf.name).performClick()
        compose.onNodeWithText("Eliminar",substring=false).performClick()
        compose.onNodeWithText("El interior no tiene compartimentos.").assertIsDisplayed()
        compose.onNodeWithText("Guardar distribución").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle{
            assertTrue(LayoutGrid.zones(checkNotNull(saved).furniture(furniture)).isEmpty())
            assertEquals(item.copy(compartment=UnassignedCompartment),checkNotNull(saved).item(item))
        }
    }
}
