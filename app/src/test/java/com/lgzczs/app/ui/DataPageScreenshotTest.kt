package com.lgzczs.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.lgzczs.app.model.PlatformStatus
import com.lgzczs.app.util.TokenManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DataPageScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun captureDataPage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tokenManager = TokenManager(context)

        tokenManager.alertDialogEnabled = true
        tokenManager.notificationEnabled = true
        tokenManager.floatWindowEnabled = true
        tokenManager.soundEnabled = true

        composeTestRule.setContent {
            MaterialTheme {
                DataPage(
                    tokenManager = tokenManager,
                    huiStatus = PlatformStatus.LOGGED_IN,
                    youkaStatus = PlatformStatus.NOT_LOGGED_IN
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("screenshots/DataPage.png")
    }
}
