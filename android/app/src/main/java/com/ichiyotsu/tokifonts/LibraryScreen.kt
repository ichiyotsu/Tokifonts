@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.ichiyotsu.tokifonts

import android.graphics.Typeface as AndroidTypeface
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface as ComposeTypeface
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LibraryScreen(vm: FontsViewModel, padding: PaddingValues) {
    val catalog = vm.catalog ?: return
    val query = vm.query.trim()
    val sections = catalog.sections.filter { vm.groupId == "all" || it.group == vm.groupId }
    val families = catalog.families.filter { family ->
        (vm.groupId == "all" || family.group == vm.groupId) &&
            (vm.sectionId == "all" || family.section == vm.sectionId) &&
            (query.isEmpty() || family.matches(query))
    }
    Column(Modifier.fillMaxSize()) {
        val bulk = vm.bulk
        bulk?.let {
            BulkProgressCard(
                it,
                onCancel = vm::cancelBulkDownload,
                modifier = Modifier.padding(
                    start = TokiSpacing.pageMargin,
                    end = TokiSpacing.pageMargin,
                    top = padding.calculateTopPadding() + TokiSpacing.sm,
                    bottom = TokiSpacing.sm,
                ),
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = if (bulk != null) {
                // 进度卡已经占了顶栏之下的位置：列表不再重复垫顶栏高度，
                // 否则卡和第一项之间会空出一个顶栏的距离。
                val direction = LocalLayoutDirection.current
                PaddingValues(
                    start = padding.calculateStartPadding(direction) + TokiSpacing.pageMargin,
                    end = padding.calculateEndPadding(direction) + TokiSpacing.pageMargin,
                    top = 0.dp,
                    bottom = padding.calculateBottomPadding() + TokiSpacing.sm,
                )
            } else {
                padding.asPagePadding()
            },
            verticalArrangement = Arrangement.spacedBy(TokiSpacing.lg),
        ) {
        if (vm.searchOpen) {
            item {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("搜索字体、厂商或字重") },
                    shape = RoundedCornerShape(28.dp),
                )
            }
        }
        item {
            // 分组行：大分类（公司字库 / 品牌主题 / …）已移到顶栏菜单打开的左边栏，
            // 这里只留分组（苹果 / 谷歌 / …）的横向行，边缘淡出。
            val sectionState = rememberLazyListState()
            LazyRow(
                state = sectionState,
                modifier = Modifier.horizontalEdgeFade(
                    showStart = sectionState.canScrollBackward,
                    showEnd = sectionState.canScrollForward,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = vm.sectionId == "all", onClick = { vm.sectionId = "all" }, label = { Text("全部分组") })
                }
                items(sections, key = { it.id }) { section ->
                    FilterChip(
                        selected = vm.sectionId == section.id,
                        onClick = { vm.sectionId = section.id },
                        label = { Text(section.name) },
                    )
                }
            }
        }
        if (families.isEmpty()) {
            item {
                Text(
                    "没有匹配的字体",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(families, key = { it.id }) { family ->
            FamilyCard(vm, family)
        }
        }
    }
}

private fun FontFamily.matches(query: String): Boolean {
    val haystack = listOf(name, nameEn, alias, summary, credit, tags.joinToString(" "))
        .plus(faces.map { "${it.weightName} ${it.weightLabel} ${it.optical}" })
        .joinToString(" ")
    return haystack.contains(query, ignoreCase = true)
}

@Composable
private fun FamilyCard(vm: FontsViewModel, family: FontFamily) {
    // 预览用“已下载优先”的 face：在详情页下了别的字重，列表也要拿那个文件的字形来显示；
    // 旧逻辑只认 initialFace，下了非默认 face 时列表预览永远是系统字体，等于没按字体文件显示。
    val preview = vm.previewFace(family)
    val downloadTarget = family.initialFace()
    val ready = family.faces.count { vm.status(it.id) is FaceStatus.Ready }
    val previewReady = preview?.let { vm.status(it.id) is FaceStatus.Ready } ?: false
    var typeface by remember(preview?.id) { mutableStateOf(preview?.let { vm.cachedTypeface(it, null) }) }
    LaunchedEffect(preview?.id, previewReady) {
        typeface = if (previewReady) preview?.let { vm.typefaceFor(it, null) } else null
    }
    Surface(
        onClick = { vm.openFamilyId = family.id },
        shape = TokiCardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
            Text(
                family.name,
                style = MaterialTheme.typography.titleLarge,
            )
            Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                val loaded = typeface
                Text(
                    text = vm.previewFor(family.id).lineSequence().firstOrNull().orEmpty().ifBlank { DEFAULT_PREVIEW },
                    modifier = Modifier.fillMaxWidth().padding(TokiSpacing.lg),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = loaded?.let { ComposeFontFamily(ComposeTypeface(it)) },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 卡片只留下载：字体名只写中文名（不重复写英文名），
            // 文件数合到下载按钮右边，写成“已下载 n/全部”。
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!previewReady && downloadTarget != null) {
                    Button(onClick = { vm.download(downloadTarget) }) { Text("下载预览字体") }
                }
                if (family.faces.isNotEmpty()) {
                    Text(
                        "已下载 $ready/${family.faces.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun FontDetail(vm: FontsViewModel, familyId: String, padding: PaddingValues) {
    val context = LocalContext.current
    val catalog = vm.catalog ?: return
    val family = catalog.family(familyId) ?: return
    var selectedId by remember(family.id) { mutableStateOf(family.initialFace()?.id) }
    val selected = family.faces.firstOrNull { it.id == selectedId } ?: family.initialFace()
    var previewSize by remember(family.id) { mutableIntStateOf(32) }
    var axis by remember(selected?.id) { mutableFloatStateOf((selected?.weight ?: 400).toFloat()) }
    val state = selected?.let { vm.status(it.id) } ?: FaceStatus.Absent
    var typeface by remember(selected?.id) {
        mutableStateOf(selected?.let { vm.cachedTypeface(it, null) })
    }
    val selectedReady = state is FaceStatus.Ready
    LaunchedEffect(selected?.id, axis, selectedReady) {
        val face = selected ?: return@LaunchedEffect
        // 非 Ready 时必须把 typeface 清空：旧逻辑只在 Ready 时赋值，删掉本地文件后
        // 本地状态还攥着旧 Typeface 对象，预览会继续拿已删字体的字形充数。
        typeface = if (selectedReady) vm.typefaceFor(face, if (face.variable) axis.toInt() else null) else null
    }
    LaunchedEffect(selected?.id) {
        val face = selected ?: return@LaunchedEffect
        // 自动下载只在 Wi-Fi 下触发：移动数据下不静默耗流量，用户点卡片上的“下载”再下。
        if (face.bytes in 1..AUTO_PREVIEW_BYTES && vm.status(face.id) is FaceStatus.Absent && isWifiConnected(context)) vm.download(face)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding.asPagePadding()),
        verticalArrangement = Arrangement.spacedBy(TokiSpacing.lg),
    ) {
        PreviewEditor(vm, family.id, typeface, previewSize)
        if (selected == null) {
            Text(family.statusDetail.ifBlank { "这个名字还没有字体文件。" }, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("字号 ${previewSize}px", style = MaterialTheme.typography.titleMedium)
            Slider(value = previewSize.toFloat(), onValueChange = { previewSize = it.toInt() }, valueRange = 18f..72f)
            if (selected.variable) {
                Text("可变字重 ${axis.toInt()}", style = MaterialTheme.typography.titleMedium)
                Slider(value = axis, onValueChange = { axis = it }, valueRange = 100f..900f)
            }
            val opticals = family.faces.map { it.optical to it.opticalLabel }.distinct().filter { it.first.isNotBlank() }
            if (opticals.size > 1) {
                Text("光学尺寸", style = MaterialTheme.typography.titleMedium)
                ChipRow(fadeColor = MaterialTheme.colorScheme.surface) {
                    opticals.forEach { (optical, label) ->
                        FilterChip(
                            selected = selected.optical == optical,
                            onClick = {
                                selectedId = family.faces.firstOrNull {
                                    it.optical == optical && it.weight == selected.weight && it.italic == selected.italic && it.variable == selected.variable
                                }?.id ?: family.faces.first { it.optical == optical }.id
                            },
                            label = { Text(label.ifBlank { optical }) },
                        )
                    }
                }
            }
            val pool = family.faces.filter { selected.optical.isBlank() || it.optical == selected.optical }
            val weights = pool.distinctBy { it.variable to it.weight }.sortedWith(compareBy({ it.variable }, { it.weight }))
            Text("字重", style = MaterialTheme.typography.titleMedium)
            ChipRow(fadeColor = MaterialTheme.colorScheme.surface) {
                weights.forEach { chip ->
                    FilterChip(
                        selected = selected.variable == chip.variable && selected.weight == chip.weight,
                        onClick = {
                            selectedId = (pool.firstOrNull {
                                it.variable == chip.variable && it.weight == chip.weight && it.italic == selected.italic
                            } ?: pool.first { it.variable == chip.variable && it.weight == chip.weight }).id
                        },
                        label = { Text(if (chip.variable) "可变" else chip.weightLabel) },
                    )
                }
                if (pool.any { it.italic }) {
                    val twin = pool.any { it.weight == selected.weight && it.variable == selected.variable && it.italic != selected.italic }
                    FilterChip(
                        selected = selected.italic,
                        onClick = {
                            if (twin) {
                                selectedId = pool.first {
                                    it.weight == selected.weight && it.variable == selected.variable && it.italic != selected.italic
                                }.id
                            }
                        },
                        enabled = twin,
                        label = { Text("斜体") },
                    )
                }
            }
            DownloadBlock(vm, family, selected, state)
        }
        InfoCard("介绍", family.summary)
        InfoCard("许可", family.license)
    }
}

/**
 * 图一的预览卡：只展示，不可编辑——预设参考、输入框、说明文字都已去掉；
 * 预览文字仍读全局文字（或这套字体之前单独改过的），右下角 × 只在改过时
 * 出现，点一下恢复为全局预览文字。
 */
@Composable
private fun PreviewEditor(vm: FontsViewModel, familyId: String, typeface: AndroidTypeface?, size: Int) {
    Box(Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = vm.previewFor(familyId),
                modifier = Modifier.fillMaxWidth().padding(TokiSpacing.xl),
                style = TextStyle(
                    fontFamily = typeface?.let { ComposeFontFamily(ComposeTypeface(it)) },
                    fontSize = size.sp,
                    lineHeight = (size * 1.35f).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (vm.hasFamilyPreview(familyId)) {
            TokiLongPressLabel("恢复为全局预览文字") {
                IconButton(
                    onClick = { vm.clearFamilyPreview(familyId) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp)
                        .semantics { contentDescription = "恢复为全局预览文字" },
                ) { Symbol("close", size = 20.dp) }
            }
        }
    }
}

@Composable
private fun DownloadBlock(vm: FontsViewModel, family: FontFamily, face: FontFace, state: FaceStatus) {
    // 图一的下载行：主段永远是“下载字体”——没下载时点它下载当前字重，
    // 已下载时点它开菜单选别的字重；箭头段开多选下拉菜单。
    // 保存到下载、分享、下载全部都已去掉（本轮用户要求）。
    var menuOpen by remember(family.id) { mutableStateOf(false) }
    var menuSelection by remember(family.id) { mutableStateOf(setOf(face.id)) }
    // 菜单候选与字重行同一口径（光学尺寸过滤后按字重去重），每项即一个下载目标。
    val pool = family.faces.filter { face.optical.isBlank() || it.optical == face.optical }
    val menuFaces = pool.distinctBy { it.variable to it.weight }.sortedWith(compareBy({ it.variable }, { it.weight }))
    val menuPending = menuSelection.mapNotNull { id -> menuFaces.firstOrNull { it.id == id } }
        .filter { vm.status(it.id) !is FaceStatus.Ready }
    if (state is FaceStatus.Downloading && state.progress >= 0f) {
        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
    } else if (state is FaceStatus.Downloading || state is FaceStatus.Queued) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                when (state) {
                    is FaceStatus.Ready -> { menuSelection = setOf(face.id); menuOpen = true }
                    is FaceStatus.Downloading, FaceStatus.Queued -> vm.cancel(face)
                    else -> vm.download(face)
                }
            },
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 8.dp, bottomEnd = 8.dp),
        ) {
            Text(
                when (state) {
                    is FaceStatus.Downloading, FaceStatus.Queued -> "取消"
                    is FaceStatus.Failed -> "重试"
                    else -> "下载字体"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
        }
        Box {
            Button(
                onClick = { menuSelection = setOf(face.id); menuOpen = true },
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 28.dp, bottomEnd = 28.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Symbol("keyboard_arrow_down", size = 24.dp)
            }
            TokiDropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                hazeState = if (LocalBackdropBlur.current) LocalHazeState.current else null,
            ) {
                menuFaces.forEach { candidate ->
                    val ready = vm.status(candidate.id) is FaceStatus.Ready
                    TokiSelectableMenuItem(
                        selected = candidate.id in menuSelection,
                        label = (if (candidate.variable) "可变" else candidate.weightLabel) + if (ready) " · 已下载" else "",
                        onClick = {
                            menuSelection =
                                if (candidate.id in menuSelection) menuSelection - candidate.id
                                else menuSelection + candidate.id
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            "下载所选（${menuPending.size} 个）",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = {
                        menuPending.forEach { vm.download(it) }
                        menuOpen = false
                    },
                    enabled = menuPending.isNotEmpty(),
                    // 与可选菜单项同一套左右间距（8dp），文字左右对齐。
                    modifier = Modifier.padding(horizontal = TokiSpacing.sm),
                )
            }
        }
        if (state is FaceStatus.Ready) {
            TextButton(onClick = { vm.delete(face) }) { Text("删除") }
            TextButton(onClick = { vm.openWith(face) }) { Text("打开") }
        }
    }
}

/**
 * 单行横向内容的边缘淡出：只在对应方向还能继续滚动时淡出该边；
 * 内容放得下时两边都不淡出。
 *
 * 淡出直接画背景色的透明度渐变盖在内容上（TokiUI/MD3E 做法）：
 * 边缘是实色背景色、向内淡到透明，只调透明度，不用 DstIn 遮罩——
 * 遮罩会把同一画布上已画好的页面渐变也一起擦掉，边缘发黑。
 * [color] 取这行所在位置的页面底色：列表顶部是 surfaceContainer，
 * 详情页中部传 surface。
 */
@Composable
private fun Modifier.horizontalEdgeFade(
    showStart: Boolean,
    showEnd: Boolean,
    fadeWidth: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
): Modifier =
    drawWithContent {
        drawContent()
        if ((!showStart && !showEnd) || size.width <= 0f) return@drawWithContent
        val fade = fadeWidth.toPx().coerceAtMost(size.width / 2f)
        val fraction = fade / size.width
        val ltr = layoutDirection == LayoutDirection.Ltr
        val fadeStart = if (ltr) showStart else showEnd
        val fadeEnd = if (ltr) showEnd else showStart
        drawRect(
            brush = Brush.horizontalGradient(
                0f to color.copy(alpha = if (fadeStart) 1f else 0f),
                fraction to color.copy(alpha = 0f),
                (1f - fraction) to color.copy(alpha = 0f),
                1f to color.copy(alpha = if (fadeEnd) 1f else 0f),
            ),
        )
    }

@Composable
private fun Modifier.horizontalEdgeFade(
    scroll: ScrollState,
    fadeWidth: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
): Modifier =
    horizontalEdgeFade(showStart = scroll.value > 0, showEnd = scroll.value < scroll.maxValue, fadeWidth = fadeWidth, color = color)

@Composable
private fun ChipRow(fadeColor: Color = MaterialTheme.colorScheme.surfaceContainer, content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    Row(
        Modifier.horizontalScroll(scroll).horizontalEdgeFade(scroll, color = fadeColor),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * 字体页的左边栏内容：大分类（公司字库 / 品牌主题 / 城市主题 / 社区贡献）
 * 由顶栏的三条线打开；点一项就切分类并关抽屉。
 * 分组（苹果 / 谷歌 / …）仍留在页面里横向滚动，不进抽屉。
 * 容器不在这里：外层按 TokiNote 的悬浮模糊设计给一块浮板（见 TokiApp），
 * 这里只出内容，不贴边、不占满高。
 */
@Composable
fun LibraryDrawerContent(vm: FontsViewModel, onClose: () -> Unit) {
    val catalog = vm.catalog
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TokiSpacing.lg, vertical = TokiSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("分类", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TokiLongPressLabel("关闭") {
                IconButton(onClick = onClose) { Symbol("close", size = 24.dp) }
            }
        }
        NavigationDrawerItem(
            label = { Text("全部") },
            selected = vm.groupId == "all",
            onClick = { vm.setGroup("all"); onClose() },
            badge = { Text("${catalog?.families?.size ?: 0}", style = MaterialTheme.typography.labelLarge) },
        )
        catalog?.groups?.forEach { group ->
            NavigationDrawerItem(
                label = { Text(group.name) },
                selected = vm.groupId == group.id,
                onClick = { vm.setGroup(group.id); onClose() },
                badge = {
                    Text(
                        "${catalog.families.count { it.group == group.id }}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }
}
