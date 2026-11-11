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
}
