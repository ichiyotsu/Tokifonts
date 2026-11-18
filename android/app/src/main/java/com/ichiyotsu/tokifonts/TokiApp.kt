package com.ichiyotsu.tokifonts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private val Pages = listOf(
    NavEntry("text_fields", "字体"),
    NavEntry("download", "安装"),
    NavEntry("settings", "设置"),
)

/**
 * App 骨架（沉浸式边到边，对照 TokiStudio 的 TokiStudioApp）：
 * - 顶栏统一：全 App 只用一个顶栏（主页面、字体详情、教程列表与详情），
 *   标题只用 headlineMedium 一个角色，返回键是 56dp 圆 chip。返回键与标题
 *   默认就带同一块磨砂（或实色退路），不随滚动出现或消失，两者间距固定
 *   12dp。高度实测值统一作各页内容顶部内边距。
 * - 悬浮导航 = 内容之上的 overlay：胶囊右侧 8dp 处一颗独立圆形玻璃按钮，
 *   功能随页面变。玻璃关闭时底栏改叫莫奈悬浮底栏，独立按钮填底栏边框色。
 * - 模糊：内容根挂 hazeSource。尺寸会变的表面（底栏胶囊、顶栏标题胶囊）
 *   用 Haze 的内容源采样（Sources），尺寸动画里不黑边；固定尺寸的表面
 *   （独立按钮）用 Backdrop 采窗口里已经画好的像素，采不到再回退到这个
 *   内容根。不是 Modifier.blur。
 * - 页面切换走 AnimatedContent，规格读 motionScheme。
 * - 页面底色是 tokiPageBackground 的 surface 纵向台阶，不是纯平背景。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TokiApp(vm: FontsViewModel, startTab: Int = -1) {
    LaunchedEffect(startTab) { if (startTab >= 0) vm.tab = startTab }
    val haze = remember { HazeState() }
    val glass = tokiChrome(haze, vm.backdropBlur)
    // 底栏胶囊的玻璃单独走内容源采样：胶囊宽度随标签展开/收起连续
    // 变化，平台窗口背景采样在尺寸动画里边缘会发黑（见 Glass.kt）。
    val barGlass = tokiContentChrome(haze, vm.backdropBlur)
    val frostedChrome = vm.backdropBlur && TokiBlurCapability.canSampleInWindow
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
        val wide = maxWidth >= TokiSpacing.expandedWindow
        val density = LocalDensity.current
        val navPx = maxOf(WindowInsets.navigationBars.getBottom(density), WindowInsets.systemGestures.getBottom(density))
        val nav = with(density) { navPx.toDp() }
        var topBarHeight by remember { mutableStateOf(0.dp) }
        val padding = PaddingValues(
            start = if (wide) 96.dp else 0.dp, // 宽窗口：官方 NavigationRail 80dp + 16dp 间距。
            // 顶部留白按顶栏实测高度算（实测值已含状态栏 inset）。
            top = topBarHeight,
            // 底部留白按底栏高度算：导航栏 + 胶囊 72.dp + 悬空 8.dp + 16.dp 间隙。
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
        // 没写颜色的文字取 LocalContentColor。MaterialExpressiveTheme 不提供它，
        // 默认是黑色——深色模式下直接放在背景上的文字（教程标题和步骤）就是黑字
        // 压深底、无法阅读。这里按背景提供 onBackground，Surface、按钮、列表项
        // 内部仍用各自的内容色，不受影响。
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            LocalHazeState provides haze,
            LocalBackdropBlur provides vm.backdropBlur,
        ) {
            TokiAppContent(
                vm, padding, haze, glass, barGlass, frostedChrome, snackbar, nav, wide,
                actionIcon, actionLabel,
                onHeaderHeight = { topBarHeight = it },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TokiAppContent(
    vm: FontsViewModel,
    padding: PaddingValues,
    haze: HazeState,
    glass: Modifier,
    barGlass: Modifier,
    frostedChrome: Boolean,
    snackbar: SnackbarHostState,
    nav: Dp,
    wide: Boolean,
    actionIcon: String,
    actionLabel: String,
    onHeaderHeight: (Dp) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val headerTitle: String = when {
        vm.tab == 0 && vm.openFamilyId != null -> vm.catalog?.family(vm.openFamilyId!!)?.name ?: "字体"
        vm.tab == 0 -> "字体"
        vm.tab == 1 && vm.installPage.startsWith("tutorial:") ->
            InstallerTools.firstOrNull { it.id == vm.installPage.removePrefix("tutorial:") }?.name ?: "教程"
        vm.tab == 1 && vm.installPage == "tutorials" -> "教程"
        vm.tab == 1 -> "安装"
        else -> "设置"
    }
    val headerBack: (() -> Unit)? = when {
        vm.tab == 0 && vm.openFamilyId != null -> { { vm.openFamilyId = null } }
        vm.tab == 1 && vm.installPage.startsWith("tutorial:") -> { { vm.installPage = "tutorials" } }
        vm.tab == 1 && vm.installPage == "tutorials" -> { { vm.installPage = "fonts" } }
        else -> null
    }
    // 页面键：AnimatedContent 按它切页；详情/教程把 id 写进键里，过渡期间
    // 新旧两页各按自己的键渲染，不会串页。
    val pageKey = when {
        vm.catalog == null && vm.loadError == null -> "loading"
        vm.catalog == null -> "error"
        vm.tab == 0 && vm.openFamilyId != null -> "detail:${vm.openFamilyId}"
        vm.tab == 0 -> "library"
        vm.tab == 1 && vm.installPage.startsWith("tutorial:") -> vm.installPage
        vm.tab == 1 && vm.installPage == "tutorials" -> "tutorials"
        vm.tab == 1 -> "install"
        else -> "settings"
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = pageKey,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) +
                    slideInHorizontally(motion.defaultSpatialSpec()) { it / 14 }) togetherWith
                    (fadeOut(motion.defaultEffectsSpec()) +
                        slideOutHorizontally(motion.defaultSpatialSpec()) { -it / 14 })
            },
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(haze)
                .background(tokiPageBackground()),
            label = "page",
        ) { key ->
            when {
                key == "loading" -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
                key == "error" -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            vm.loadError ?: "字库索引读取失败",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = vm::retryLoad,
                            modifier = Modifier.heightIn(min = 56.dp),
                        ) {
                            Text("重试", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                key.startsWith("detail:") -> FontDetail(vm, key.removePrefix("detail:"), padding)
                key == "library" -> LibraryScreen(vm, padding)
                key.startsWith("tutorial:") -> InstallTutorial(vm, key.removePrefix("tutorial:"), padding)
                key == "tutorials" -> InstallTutorialIndex(vm, padding)
                key == "install" -> InstallScreen(vm, padding)
                else -> SettingsScreen(vm, padding)
            }
        }
        OverlayTitleHeader(
            title = headerTitle,
            onBack = headerBack,
            onHeightMeasured = onHeaderHeight,
            backdropBlur = vm.backdropBlur,
            hazeState = haze,
            modifier = Modifier.align(Alignment.TopCenter).padding(start = if (wide) 96.dp else 0.dp),
        )
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
            TokiNavRail(Pages, vm.tab, select, actionIcon, actionLabel, act, glass, !frostedChrome, Modifier.align(Alignment.CenterStart))
        } else {
            ExpressiveBottomBar(Pages, vm.tab, select, actionIcon, actionLabel, act, glass, barGlass, !frostedChrome, Modifier.align(Alignment.BottomCenter))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (wide) 24.dp else nav + BottomBarHeight + 16.dp))
        UpdateDialog(vm)
    }
}

@Composable
fun PaddingValues.asPagePadding(horizontal: Dp = TokiSpacing.pageMargin, vertical: Dp = TokiSpacing.sm): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + horizontal,
        end = calculateEndPadding(direction) + horizontal,
        top = calculateTopPadding() + vertical,
        bottom = calculateBottomPadding() + vertical,
    )
}
