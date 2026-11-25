package es.planidroid.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
class CaptureTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun textOnlyCapture(){
        compose.onNodeWithText("Continuar con Google").assertIsDisplayed()
        compose.onAllNodesWithText("Añadir",substring=false).assertCountEquals(0)
    }
}
