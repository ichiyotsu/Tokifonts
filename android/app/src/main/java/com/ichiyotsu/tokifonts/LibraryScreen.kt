@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.ichiyotsu.tokifonts

import android.graphics.Typeface as AndroidTypeface
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import androidx.compose.ui.text.font.Typeface as ComposeTypeface
import androidx.compose.ui.text.style.TextOverflow
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
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = padding.asPagePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Tokifonts", style = MaterialTheme.typography.displaySmallEmphasized)
            Text(
                "${catalog.families.size} 套 · ${catalog.families.sumOf { it.faces.size }} 个文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = vm.groupId == "all", onClick = { vm.setGroup("all") }, label = { Text("全部") })
                }
                items(catalog.groups, key = { it.id }) { group ->
                    FilterChip(selected = vm.groupId == group.id, onClick = { vm.setGroup(group.id) }, label = { Text(group.name) })
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            item { Text("没有匹配的字体", style = MaterialTheme.typography.bodyLarge) }
        }
        items(families, key = { it.id }) { family ->
            FamilyCard(vm, family, catalog.sections.firstOrNull { it.id == family.section }?.name.orEmpty())
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
private fun FamilyCard(vm: FontsViewModel, family: FontFamily, section: String) {
    val preview = family.initialFace()
    val ready = family.faces.count { vm.status(it.id) is FaceStatus.Ready }
    var typeface by remember(preview?.id) { mutableStateOf(preview?.let { vm.cachedTypeface(it, null) }) }
    LaunchedEffect(preview?.id, ready) {
        typeface = preview?.takeIf { vm.status(it.id) is FaceStatus.Ready }?.let { vm.typefaceFor(it, null) }
    }
    Surface(
        onClick = { vm.openFamilyId = family.id },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                listOf(section, family.tags.firstOrNull().orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(family.name, style = MaterialTheme.typography.titleLarge)
            Text(
                listOf(family.nameEn, if (family.faces.isEmpty()) family.status.ifBlank { "暂无文件" } else "${family.faces.size} 个文件")
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (family.faces.isNotEmpty()) {
                Text("已下载 $ready/${family.faces.size}", style = MaterialTheme.typography.labelMedium)
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                val loaded = typeface
                Text(
                    text = vm.previewFor(family.id).lineSequence().firstOrNull().orEmpty().ifBlank { DEFAULT_PREVIEW },
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = loaded?.let { ComposeFontFamily(ComposeTypeface(it)) },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (preview != null && vm.status(preview.id) !is FaceStatus.Ready) {
                TextButton(onClick = { vm.download(preview) }) { Text("下载预览") }
            }
        }
    }
}

@Composable
fun FontDetail(vm: FontsViewModel, familyId: String, padding: PaddingValues) {
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
    LaunchedEffect(selected?.id, axis, state is FaceStatus.Ready) {
        val face = selected ?: return@LaunchedEffect
        if (vm.status(face.id) is FaceStatus.Ready) typeface = vm.typefaceFor(face, if (face.variable) axis.toInt() else null)
    }
    LaunchedEffect(selected?.id) {
        val face = selected ?: return@LaunchedEffect
        if (face.bytes in 1..AUTO_PREVIEW_BYTES && vm.status(face.id) is FaceStatus.Absent) vm.download(face)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding.asPagePadding(vertical = 8.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = { vm.openFamilyId = null }) { Text("返回字体") }
        Text(family.name, style = MaterialTheme.typography.displaySmallEmphasized)
        if (family.nameEn.isNotBlank() && family.nameEn != family.name) {
            Text(family.nameEn, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (family.alias.isNotBlank()) Text("也叫 ${family.alias}", style = MaterialTheme.typography.bodyMedium)
        if (family.credit.isNotBlank()) Text("提供：${family.credit}", style = MaterialTheme.typography.bodyMedium)
        Text(family.tags.joinToString(" · "), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        PreviewEditor(vm, family.id, typeface, previewSize)
        if (selected == null) {
            Text(family.statusDetail.ifBlank { "这个名字还没有字体文件。" }, style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("预览字号 $previewSize", style = MaterialTheme.typography.labelLarge)
            Slider(value = previewSize.toFloat(), onValueChange = { previewSize = it.toInt() }, valueRange = 18f..72f)
            if (selected.variable) {
                Text("可变字重 ${axis.toInt()}", style = MaterialTheme.typography.labelLarge)
                Slider(value = axis, onValueChange = { axis = it }, valueRange = 100f..900f)
            }
            val opticals = family.faces.map { it.optical to it.opticalLabel }.distinct().filter { it.first.isNotBlank() }
            if (opticals.size > 1) {
                Text("光学尺寸", style = MaterialTheme.typography.labelLarge)
                ChipRow {
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
            Text("字重", style = MaterialTheme.typography.labelLarge)
            ChipRow {
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
            Text(selected.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DownloadBlock(vm, selected, state)
            if (family.faces.size > 1) {
                OutlinedButton(onClick = { vm.downloadAll(family) }, modifier = Modifier.fillMaxWidth()) {
                    Text("下载全部 ${family.faces.size} 个文件")
                }
            }
        }
        InfoCard("介绍", family.summary)
        InfoCard("许可", family.license)
        InfoCard("来源", family.sources.joinToString("、").ifBlank { "—" })
    }
}

@Composable
private fun PreviewEditor(vm: FontsViewModel, familyId: String, typeface: AndroidTypeface?, size: Int) {
    OutlinedTextField(
        value = vm.previewFor(familyId),
        onValueChange = { vm.setFamilyPreview(familyId, it) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        textStyle = TextStyle(
            fontFamily = typeface?.let { ComposeFontFamily(ComposeTypeface(it)) },
            fontSize = size.sp,
            lineHeight = (size * 1.35f).sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        label = { Text("预览文字") },
        supportingText = {
            Text(if (vm.hasFamilyPreview(familyId)) "只改这一套字体。恢复后跟设置里的全局文字。" else "还没单独改过，现在用的是设置里的全局文字。")
        },
        shape = RoundedCornerShape(28.dp),
    )
    TextButton(onClick = { vm.clearFamilyPreview(familyId) }) { Text("恢复为全局预览文字") }
}

@Composable
private fun DownloadBlock(vm: FontsViewModel, face: FontFace, state: FaceStatus) {
    val label = when (state) {
        FaceStatus.Absent -> "下载并预览 · ${face.bytes.toSizeLabel()}"
        FaceStatus.Queued -> "等待下载"
        is FaceStatus.Downloading -> if (state.progress < 0f) "正在解压" else "下载中 ${(state.progress * 100).toInt()}%"
        is FaceStatus.Ready -> "已下载 · ${state.file.length().toSizeLabel()}"
        is FaceStatus.Failed -> "重试 · ${state.message}"
    }
    if (state is FaceStatus.Downloading && state.progress >= 0f) {
        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
    } else if (state is FaceStatus.Downloading || state is FaceStatus.Queued) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                when (state) {
                    is FaceStatus.Ready -> Unit
                    is FaceStatus.Downloading, FaceStatus.Queued -> vm.cancel(face)
                    else -> vm.download(face)
                }
            },
            modifier = Modifier.weight(1f),
            enabled = state !is FaceStatus.Ready,
        ) { Text(if (state is FaceStatus.Downloading || state is FaceStatus.Queued) "取消" else label, maxLines = 2) }
    }
    if (state is FaceStatus.Ready) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (face.id in vm.exported) Text("已保存到下载目录", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.export(face) }) { Text("保存到下载") }
            OutlinedButton(onClick = { vm.openWith(face) }) { Text("用其他应用打开") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.share(face) }) { Text("分享") }
            TextButton(onClick = { vm.delete(face) }) { Text("删除本地文件") }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
