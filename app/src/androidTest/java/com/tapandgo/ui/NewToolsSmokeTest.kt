package com.tapandgo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tapandgo.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator smoke test for Premium, Root Manager, NDEF Lab, emulation and
 * workflows: each screen renders a truthful state on a device without root,
 * billing or (on the emulator) NFC. Not hardware validation.
 */
@RunWith(AndroidJUnit4::class)
class NewToolsSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun click(label: String) = rule.onAllNodesWithText(label).onFirst().performScrollTo().performClick()
    private fun back() = rule.onNodeWithContentDescription("Back").performClick()

    private fun shows(text: String) {
        rule.waitUntil(5_000) { rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasText(text, substring = true)).onFirst().assertIsDisplayed()
    }

    @Test
    fun premiumRootLabAndWorkflowsRender() {
        rule.onAllNodesWithText("Tools").onFirst().performClick()
        click("Premium"); shows("Purchases are not available in this build"); back()
        click("Root Manager"); shows("Root access not checked"); back()
        click("NDEF Lab"); shows("Premium feature"); back()
        click("Tag emulation"); shows("Premium feature"); back()
        rule.onAllNodesWithText("Tasks").onFirst().performClick()
        click("Open workflows"); shows("New workflow")
    }
}
