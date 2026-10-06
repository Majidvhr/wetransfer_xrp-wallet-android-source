package com.vaultxrplwallet.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test

class OnboardingUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun welcomeShowsHoldXrpCopy() {
        rule.onNodeWithText("Hold XRP.\nOwn the keys.", substring = true).assertIsDisplayed()
    }
}
