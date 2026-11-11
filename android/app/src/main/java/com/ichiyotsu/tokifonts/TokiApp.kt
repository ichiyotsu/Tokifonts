package com.ichiyotsu.tokifonts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private val Pages = listOf(
    NavEntry("text_fields", "字体"),
    NavEntry("download", "安装"),
    NavEntry("settings", "设置"),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TokiApp(vm: FontsViewModel, startTab: Int = -1, dark: Boolean = isSystemInDarkTheme()) {
    LaunchedEffect(startTab) { if (startTab >= 0) vm.tab = startTab }
    val haze = remember { HazeState() }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.messageSeq) {
        val text = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.consumeMessage()
    }
    BackHandler(enabled = vm.openFamilyId != null || vm.searchOpen || (vm.tab == 1 && vm.installPage != "fonts")) {
        when {
            vm.tab == 1 && vm.installPage.startsWith("tutorial:") -> vm.installPage = "tutorials"
            vm.tab == 1 && vm.installPage != "fonts" -> vm.installPage = "fonts"
            vm.openFamilyId != null -> vm.openFamilyId = null
            vm.searchOpen -> vm.searchOpen = false
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val density = LocalDensity.current
        val navPx = maxOf(WindowInsets.navigationBars.getBottom(density), WindowInsets.systemGestures.getBottom(density))
        val nav = with(density) { navPx.toDp() }
        val padding = PaddingValues(
            start = if (wide) 112.dp else 0.dp,
            top = WindowInsets.statusBars.let { with(density) { it.getTop(density).toDp() } },
            // 底部留白按底栏实测高度算：导航栏 + 胶囊 72.dp + 悬空 8.dp + 16.dp 间隙。
            bottom = if (wide) 24.dp else nav + BottomBarHeight + 24.dp,
        )
        val actionIcon = when (vm.tab) {
            0 -> "search"
            1 -> "menu_book"
            else -> "palette"
        }
        val actionLabel = when (vm.tab) {
            0 -> "搜索"
            1 -> "教程"
            else -> "切换动态取色"
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                when {
                    vm.catalog == null && vm.loadError == null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                    vm.catalog == null -> {
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(vm.loadError ?: "字库索引读取失败", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = vm::retryLoad) { Text("重试") }
                        }
                    }
                    vm.tab == 0 && vm.openFamilyId != null -> FontDetail(vm, vm.openFamilyId!!, padding)
                    vm.tab == 0 -> LibraryScreen(vm, padding)
                    vm.tab == 1 && vm.installPage.startsWith("tutorial:") -> InstallTutorial(vm, vm.installPage.removePrefix("tutorial:"), padding)
                    vm.tab == 1 && vm.installPage == "tutorials" -> InstallTutorialIndex(vm, padding)
                    vm.tab == 1 -> InstallScreen(vm, padding)
                    else -> SettingsScreen(vm, padding)
                }
            }
            val select = { index: Int ->
                vm.tab = index
                vm.openFamilyId = null
                vm.installPage = "fonts"
                // 搜索框属于字体页：切走就关掉，否则返回键会关一个看不见的搜索框。
                vm.searchOpen = false
            }
            val act = {
                when (vm.tab) {
                    0 -> vm.searchOpen = !vm.searchOpen
                    1 -> vm.installPage = "tutorials"
                    else -> vm.toggleDynamic()
                }
            }
            if (wide) {
                ExpressiveNavRail(Pages, vm.tab, select, actionIcon, actionLabel, act, Modifier.align(Alignment.CenterStart))
            } else {
                ExpressiveBottomBar(Pages, vm.tab, select, actionIcon, actionLabel, act, haze, dark, Modifier.align(Alignment.BottomCenter))
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (wide) 24.dp else nav + BottomBarHeight + 16.dp))
            UpdateDialog(vm)
        }
    }
}

@Composable
fun PaddingValues.asPagePadding(horizontal: Dp = 20.dp, vertical: Dp = 12.dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + horizontal,
        end = calculateEndPadding(direction) + horizontal,
        top = calculateTopPadding() + vertical,
        bottom = calculateBottomPadding() + vertical,
    )
}

