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
        setContent {
            val dark = when (vm.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            TokiTheme(dark = dark, dynamic = vm.dynamicColor) {
                LaunchedEffect(Unit) { if (vm.checkOnLaunch) vm.checkUpdate(manual = false) }
                TokiApp(vm, dark = dark)
            }
        }
    }
}
