package es.planidroid.app
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test
class NavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun calendarOpens(){compose.onNodeWithText("Calendario").performClick();compose.onNodeWithText("Planificar mi agenda").assertIsDisplayed()}
}
