package dev.nfcstudio.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nfcstudio.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator UI smoke test: every top-level screen renders its real empty or
 * waiting state, and invalid writer input is rejected without a crash.
 * It does not touch tags and is not hardware validation.
 */
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun tab(label: String) = rule.onAllNodesWithText(label).onFirst().performClick()

    private fun shows(text: String) {
        rule.waitUntil(5_000) { rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasText(text, substring = true)).onFirst().assertIsDisplayed()
    }

    @Test
    fun allTopLevelScreensRender() {
        shows("NFC Studio")
        tab("Scan"); shows("Reader mode is active")
        tab("Write"); shows("Compose records")
        tab("Tasks"); shows("New profile")
        tab("History"); shows("Search content or technology")
        tab("Settings"); shows("Save scans to history")
        tab("Home"); shows("Recent scans")
    }

    @Test
    fun invalidLanguageIsRejectedWithoutCrash() {
        tab("Write")
        shows("Compose records")
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("hello")
        fields[1].performTextReplacement("e n")
        rule.onAllNodesWithText("Add record").onFirst().performClick()
        shows("Use a language code")
    }
}
