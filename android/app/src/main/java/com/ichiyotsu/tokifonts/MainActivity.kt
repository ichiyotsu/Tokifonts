package com.ichiyotsu.tokifonts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    private val vm: FontsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 启动窗口底色在 themes.xml 里只能跟系统深浅走；若用户在应用内强制了相反的主题，
        // 这里按已存的 themeMode 尽早把窗口背景改成对应的语义 canvas 色，缩短首帧闪色。
        // 动态取色开时首帧仍用静态底色近似，Compose 画出后即被真实 scheme 覆盖。
        val mode = getSharedPreferences(FontStore.PREFS, MODE_PRIVATE).getInt("theme", 0)
        val sysDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val startDark = when (mode) { 1 -> false; 2 -> true; else -> sysDark }
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(
            if (startDark) 0xFF141218.toInt() else 0xFFFEF7FF.toInt(),
        ))
        setContent {
            val dark = when (vm.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            TokiTheme(dark = dark, dynamic = vm.dynamicColor, seedIndex = vm.seedIndex, reduceMotion = vm.reduceMotion) {
                // 启动自动后台检查更新：没有开关，有新版就自动弹出下载页（UpdateDialog）。
                LaunchedEffect(Unit) { vm.checkUpdate(manual = false) }
                TokiApp(vm)
            }
        }
    }
}
