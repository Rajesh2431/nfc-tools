package com.tapandgo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tapandgo.MainActivity
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
        shows("Quick actions")
        tab("Read"); shows("Read")
        tab("Write"); shows("Add a record")
        tab("Other"); shows("Copy tag")
        tab("Tasks"); shows("New profile")
        tab("History"); shows("Search content or technology")
        rule.onNodeWithContentDescription("Settings").performClick(); shows("Save scans to history")
        rule.onNodeWithContentDescription("Back").performClick()
        tab("Read"); shows("Read")
    }

    @Test
    fun invalidLanguageIsRejectedWithoutCrash() {
        tab("Write")
        rule.onAllNodesWithText("Add a record").onFirst().performClick()
        rule.onAllNodesWithText("Plain text with a language code").onFirst().performClick()
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("hello")
        fields[1].performTextReplacement("e n")
        rule.onAllNodesWithText("Add record").onLast().performClick()
        shows("Some fields are missing or invalid")
    }

    @Test
    fun wifiRecordIsBuiltAndListed() {
        tab("Write")
        rule.onAllNodesWithText("Add a record").onFirst().performClick()
        rule.onAllNodesWithText("Network name and password (WSC)").onFirst().performClick()
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("CafeNet")
        fields[1].performTextInput("password123")
        rule.onAllNodesWithText("Add record").onLast().performClick()
        shows("CafeNet")
        shows("Encoded size")
    }
}
