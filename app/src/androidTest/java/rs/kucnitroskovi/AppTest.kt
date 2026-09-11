package rs.kucnitroskovi

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
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
        screenshot("promet")
        compose.onNodeWithText("Pregled", useUnmergedTree = true).performClick()
        screenshot("pregled")
        compose.onNodeWithText("Još", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Tamna").performScrollTo().performClick()
        screenshot("tamna-tema")
        compose.onNodeWithText("Sistemska").performClick()
        compose.onNodeWithText("Promet", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Obriši unos").performClick()
        compose.onNodeWithText("Obriši", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Test kupovina").fetchSemanticsNodes().isEmpty() }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val file = File(compose.activity.getExternalFilesDir("screenshots"), "$name.png")
        FileOutputStream(file).use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
