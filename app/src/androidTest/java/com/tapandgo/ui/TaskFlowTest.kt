package com.tapandgo.ui

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tapandgo.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end UI flow on an emulator/device: create a profile (Room +
 * kotlinx.serialization), run it and see it in the log. Uses an in-app action
 * only, so nothing leaves the app. No NFC hardware involved.
 */
@RunWith(AndroidJUnit4::class)
class TaskFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun click(text: String) = rule.onAllNodesWithText(text).onFirst().performClick()

    private fun waitFor(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun scrollTo(text: String) =
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text, substring = true))

    @Test
    fun createRunAndLogProfile() {
        val name = "Lobby " + System.currentTimeMillis() % 100_000
        click("Tasks"); waitFor("New profile"); click("New profile")
        waitFor("Add an action")
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(name)
        scrollTo("Show text"); click("Show text")
        scrollTo("Add record")
        rule.onAllNodes(hasSetTextAction()).onLast().performTextInput("Welcome to the lobby")
        click("Add record")
        waitFor("Show text: Welcome to the lobby")
        scrollTo("Save"); click("Save")

        waitFor(name)
        scrollTo(name)
        rule.onAllNodesWithText("Run").onFirst().performClick()
        waitFor("Welcome to the lobby")
        click("Continue")
        waitFor("Run finished")
        click("Done")
        waitFor("Execution log")
        scrollTo("$name · #1")
    }
}
