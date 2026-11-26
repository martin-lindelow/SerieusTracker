package com.mlw.serieustracker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun getString(resId: Int, vararg formatArgs: Any): String =
        composeTestRule.activity.getString(resId, *formatArgs)

    @Test
    fun testNavigateToSettings() {
        val settingsActionDesc = getString(R.string.action_settings)
        val settingsTitle = getString(R.string.title_settings)
        val languageSection = getString(R.string.section_language)

        // Click settings icon button in top app bar
        composeTestRule.onNodeWithContentDescription(settingsActionDesc).performClick()

        // Verify that Settings screen title is displayed in TopAppBar
        composeTestRule.onNodeWithText(settingsTitle).assertIsDisplayed()

        // Scroll to and verify that the Language section is displayed
        composeTestRule.onNodeWithText(languageSection).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testNavigateToSearch() {
        val searchActionDesc = getString(R.string.action_search)
        val searchTitle = getString(R.string.title_search)
        val animeSourceLabel = getString(R.string.source_anime)
        val seriesSourceLabel = getString(R.string.source_series)

        // Click search icon in top app bar
        composeTestRule.onNodeWithContentDescription(searchActionDesc).performClick()

        // Verify Search screen title and source selector buttons
        composeTestRule.onNodeWithText(searchTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(animeSourceLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(seriesSourceLabel).assertIsDisplayed()
    }

    @Test
    fun testMainTabsDisplayed() {
        val upcomingTab = getString(R.string.tab_upcoming)
        val watchNextTab = getString(R.string.tab_watch_next)
        val myListTab = getString(R.string.tab_my_list)

        composeTestRule.onNodeWithText(upcomingTab).assertIsDisplayed()
        composeTestRule.onNodeWithText(watchNextTab).assertIsDisplayed()
        composeTestRule.onNodeWithText(myListTab).assertIsDisplayed()
    }
}
