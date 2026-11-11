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

    @Test fun library() = shot("library", false, 0)
    @Test fun libraryDark() = shot("library-dark", true, 0)
    @Test fun install() = shot("install", false, 1)
    @Test fun installDark() = shot("install-dark", true, 1)
    @Test fun settings() = shot("settings", false, 2)
    @Test fun settingsDark() = shot("settings-dark", true, 2)

    @Test fun tutorialIndex() {
        val vm = ready(false)
        vm.installPage = "tutorials"
        show(vm, 1)
        rule.onRoot().captureRoboImage("build/shots/tutorials.png")
    }

    @Test fun tutorialsDark() {
        val vm = ready(true)
        vm.installPage = "tutorials"
        show(vm, 1)
        rule.onRoot().captureRoboImage("build/shots/tutorials-dark.png")
    }

    @Test fun tutorial() {
        val vm = ready(false)
        vm.installPage = "tutorial:samfonts"
        show(vm, 1)
        rule.onRoot().captureRoboImage("build/shots/tutorial.png")
    }

    @Test fun tutorialDark() {
        val vm = ready(true)
        vm.installPage = "tutorial:samfonts"
        show(vm, 1)
        rule.onRoot().captureRoboImage("build/shots/tutorial-dark.png")
    }

    @Test fun detailShowsBundledFont() {
        val vm = ready(false)
        vm.openFamilyId = "google-sans"
        show(vm, 0)
        rule.onRoot().captureRoboImage("build/shots/detail.png")
    }

    @Test fun detailDark() {
        val vm = ready(true)
        vm.openFamilyId = "google-sans"
        show(vm, 0)
        rule.onRoot().captureRoboImage("build/shots/detail-dark.png")
    }

    private fun ready(dark: Boolean): FontsViewModel {
        val vm = FontsViewModel(RuntimeEnvironment.getApplication())
        vm.prepareForTest()
        vm.dynamicColor = false
        vm.checkOnLaunch = false
        vm.persistTheme(if (dark) 2 else 1)
        return vm
    }

    private fun show(vm: FontsViewModel, tab: Int) {
        // dark 必须显式传给 TokiApp 且与主题用同一个值，否则毛玻璃 tint 的深浅会跟系统走、与主题脱节。
        rule.setContent { TokiTheme(dark = vm.themeMode == 2, dynamic = false) { TokiApp(vm, tab, dark = vm.themeMode == 2) } }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
    }

    private fun shot(name: String, dark: Boolean, tab: Int) {
        show(ready(dark), tab)
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }
}
