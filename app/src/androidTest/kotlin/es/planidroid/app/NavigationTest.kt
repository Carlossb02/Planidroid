package es.planidroid.app

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import es.planidroid.app.domain.CalendarDraft
import es.planidroid.app.domain.CalendarFile
import es.planidroid.app.ui.CalendarFileDialog
import es.planidroid.app.ui.CalendarScreen
import es.planidroid.app.ui.PlanidroidTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class NavigationTest {
    @get:Rule val compose=createComposeRule()

    @Test fun calendarOpensEventAndFileEntryPoints(){
        var day:LocalDate?=null
        var opened=false
        compose.setContent {
            PlanidroidTheme("Claro",false){
                CalendarScreen(emptyList(),emptyList(),{},{},{},{},addEvent={day=it},importFile={opened=true})
            }
        }
        compose.onNodeWithText("Planificar").assertIsDisplayed()
        compose.onNodeWithText("Nuevo evento").performClick()
        compose.onNodeWithContentDescription("Importar archivo de calendario").performClick()
        compose.runOnIdle{assertEquals(LocalDate.now(),day);assertTrue(opened)}
    }

    @Test fun eventCanBeEditedBeforeItIsAdded(){
        val sample=CalendarDraft("ical-event","Reunión",1796122800000L,1796126400000L)
        var saved:List<CalendarDraft>?=null
        compose.setContent {
            PlanidroidTheme("Claro",false) {
                CalendarFileDialog(CalendarFile(listOf(sample),0,false,false),{}){events,done->
                    saved=events;done(null)
                }
            }
        }
        compose.onNodeWithContentDescription("Editar Reunión").performClick()
        compose.onNodeWithText("Título").performTextReplacement("Reunión revisada")
        compose.onNodeWithText("Guardar",substring=false).performClick()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithText("Añadir evento",substring=false).performClick()
        compose.runOnIdle{assertEquals("Reunión revisada",saved?.single()?.title)}
    }

    @Test fun androidRoutesCalendarAttachmentsToPlanidroid(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val intent=Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://calendar.example/fixture.ics"),"text/calendar")
            .addCategory(Intent.CATEGORY_DEFAULT).setPackage(context.packageName)
        assertEquals(MainActivity::class.java.name,intent.resolveActivity(context.packageManager)?.className)
    }
}
