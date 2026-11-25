package es.planidroid.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
class CaptureTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun textOnlyCapture(){
        compose.onNodeWithText("Añadir",substring=false).performClick()
        compose.onNodeWithText("¿Qué tienes en mente?").assertIsDisplayed()
        compose.onAllNodesWithText("Cámara").assertCountEquals(0)
        compose.onAllNodesWithText("OCR de imagen").assertCountEquals(0)
        compose.onAllNodesWithText("Archivo").assertCountEquals(0)
        compose.onNode(hasSetTextAction()).performTextInput("Llamar al dentista mañana")
        compose.onNodeWithText("Organizar",substring=false).performClick()
        compose.onNodeWithText("Tu propuesta").assertIsDisplayed()
        compose.onNodeWithText("Guardar tarea").assertIsDisplayed()
    }
}
