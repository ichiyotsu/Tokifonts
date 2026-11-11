package com.ichiyotsu.tokifonts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean, tab: Int) {
        val vm = FontsViewModel(RuntimeEnvironment.getApplication())
        vm.prepareForTest()
        vm.dynamicColor = false
        vm.persistTheme(if (dark) 2 else 1)
        rule.setContent { TokiTheme(dark = dark, dynamic = false) { TokiApp(vm, tab) } }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test fun library() = shot("library", false, 0)
    @Test fun install() = shot("install", false, 1)
    @Test fun settings() = shot("settings", true, 2)

    @Test fun detailShowsBundledFont() {
        val vm = FontsViewModel(RuntimeEnvironment.getApplication())
        vm.prepareForTest()
        vm.dynamicColor = false
        vm.persistTheme(1)
        vm.openFamilyId = "google-sans"
        rule.setContent { TokiTheme(dark = false, dynamic = false) { TokiApp(vm, 0) } }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/detail.png")
    }
}
