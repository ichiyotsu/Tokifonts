package com.ichiyotsu.tokifonts

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    @Test fun mergedLibraryKeepsBothRepos() {
        val catalog = Catalog.parse(File("src/main/assets/catalog.json").readText())
        assertEquals("0.1.0", catalog.dataVersion)
        assertEquals(listOf("co", "brd", "city", "community"), catalog.groups.map { it.id })
        assertEquals(26, catalog.families.size)
        val faces = catalog.families.flatMap { it.faces }
        assertEquals(129, faces.size)
        assertEquals(faces.map { it.id }.size, faces.map { it.id }.toSet().size)
        assertEquals(0, catalog.family("breeze-sans")!!.faces.size)
        assertEquals(0, catalog.family("canva-sans")!!.faces.size)
        assertEquals(6, catalog.family("pingfang-sc")!!.faces.size)
        assertEquals(18, catalog.family("sf-pro-display")!!.faces.size)
        assertEquals(24, catalog.family("new-york")!!.faces.size)
        assertEquals(10, catalog.family("misans")!!.faces.size)
        assertTrue(catalog.family("new-york")!!.faces.any { it.optical == "Extra Large" && it.weight == 900 })
        assertEquals("", catalog.family("google-sans")!!.faces.first { it.id == "3009" }.optical)
        val kangxi = catalog.family("kangxi")!!.faces.single()
        assertEquals("7z", kangxi.format)
        assertEquals("康熙字典体.ttf", kangxi.innerName)
        assertTrue(kangxi.urls().single().contains("%E5%BA%B7%E7%86%99%E5%AD%97%E5%85%B8%E4%BD%93.7z"))
        assertEquals("zip", catalog.family("source-han-sans")!!.faces.single().format)
        assertEquals("SourceHanSansSC-VF.ttf", catalog.family("source-han-sans")!!.faces.single().innerName)
        assertEquals(121, faces.count { it.mirrorPath.isNotBlank() })
        assertEquals(8, faces.count { it.repo.endsWith("TokiFontsResource") })
        assertTrue(catalog.family("one-ui-sans")!!.alias.contains("Samsung One"))
        assertTrue(catalog.family("cotton-pinyin")!!.sources == listOf("TokiFontsResource"))
        assertTrue(catalog.mergeNote.contains("社区"))
        val ping = catalog.family("pingfang-sc")!!.faces.first { it.id == "2006" }
        assertTrue(ping.urls().first().contains("ichiyotsu/Tokifonts/Pro/"))
        assertTrue(ping.urls()[1].contains("TokiFontsResource/Apple/"))
    }

    @Test fun previewFacePrefersDownloadedFace() {
        val catalog = Catalog.parse(File("src/main/assets/catalog.json").readText())
        val pingfang = catalog.family("pingfang-sc")!!
        val initial = pingfang.initialFace()!!
        // 一个都没下载：退回默认 face，此时它只作“下载预览”的目标，不渲染真实字形
        assertEquals(initial.id, pingfang.previewFace(emptySet())!!.id)
        // 只下了另一个字重：预览必须用已下载的那个文件的字形，而不是默认 face
        val other = pingfang.faces.first { it.id != initial.id }
        assertEquals(other.id, pingfang.previewFace(setOf(other.id))!!.id)
        // 全下了：按 ranked 顺序取第一个已下载的，就是默认 face 本身
        val all = pingfang.faces.map { it.id }.toSet()
        assertEquals(initial.id, pingfang.previewFace(all)!!.id)
        // 没有任何 face 的家族：预览 face 为空，卡片只显示名称和状态
        assertEquals(null, catalog.family("breeze-sans")!!.previewFace(all))
    }

    @Test fun previewPresetsCoverScripts() {
        assertTrue(PREVIEW_PRESETS.size >= 5)
        assertTrue(PREVIEW_PRESETS.all { it.first.isNotBlank() && it.second.isNotBlank() })
        val texts = PREVIEW_PRESETS.map { it.second }
        assertTrue(texts.any { it.contains('永') && it.contains('，') })
        assertTrue(texts.any { it.contains('體') && it.contains('体') })
        assertTrue(texts.any { it.contains('A') && it.contains('a') && it.contains('Z') && it.contains('z') })
        assertTrue(texts.any { it.contains('0') && it.contains('9') && it.contains('？') })
        assertTrue(texts.any { it.contains('あ') || it.contains('ア') })
    }
}
