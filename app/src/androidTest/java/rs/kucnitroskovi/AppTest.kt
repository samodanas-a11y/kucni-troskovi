package rs.kucnitroskovi

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class AppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun userCanAddExpenseAndSeeBothCurrencies() {
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Novi unos").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Novi unos").performClick()
        compose.onNodeWithText("Iznos u RSD").performTextInput("1250,50")
        compose.onNodeWithText("Beleška (opciono)").performTextInput("Test kupovina")
        compose.onNodeWithText("Sačuvaj").performClick()
        compose.onNodeWithText("Promet").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Test kupovina").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Test kupovina").assertIsDisplayed()
        compose.onNodeWithText("1.250,50 RSD").assertIsDisplayed()
        compose.onAllNodes(hasText("EUR", substring = true)).onFirst().assertExists()
        compose.onNodeWithContentDescription("Obriši unos").performClick()
        compose.onNodeWithText("Obriši", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Test kupovina").fetchSemanticsNodes().isEmpty() }
    }
}
