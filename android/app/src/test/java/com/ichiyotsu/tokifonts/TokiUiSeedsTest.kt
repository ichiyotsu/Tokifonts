package com.ichiyotsu.tokifonts

import com.tokiui.semantic.SemanticTokens
import com.tokiui.semantic.ShowcaseInventory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TokiUI 锁定测试：解析 vendored 的语义 token 原文件（与打包进 APK 的同一份
 * JSON，见 build.gradle.kts 的 test resources 配置），断言种子色与代码
 * 使用的种子一致。TokiUI 上游改种子时这里会失败，提醒同步使用方，
 * 而不是悄悄用上一份旧色值。同步步骤见 android/TOKIUI_SYNC.md。
 */
class TokiUiSeedsTest {
    private fun assetText(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(name)
        if (stream != null) return stream.bufferedReader().readText()
        // 类加载器找不到时退回模块相对路径（单元测试的工作目录即模块目录）。
        return java.io.File("src/main/assets", name).readText()
    }

    @Test fun vendoredSeedsMatchExpected() {
        val tokens = SemanticTokens.parse(assetText("tokiui.semantic.tokens.json"))
        assertEquals("0.2.0-b1", tokens.version)
        assertEquals(
            listOf(
                "purple" to "#6750A4",
                "blue" to "#0061A4",
                "teal" to "#006A6A",
                "green" to "#146C2E",
                "orange" to "#A85100",
            ),
            tokens.seeds.map { it.id to it.value },
        )
        assertEquals(listOf("紫", "蓝", "青", "绿", "橙"), tokens.seeds.map { it.name })
        // 主题用的种子色转换必须把同一份值转成不透明色。
        tokens.seeds.forEach { seed ->
            assertEquals(1f, seedColor(seed.value).alpha, 0f)
        }
    }

    @Test fun showcaseInventoryParses() {
        val inventory = ShowcaseInventory.parse(assetText("showcase.inventory.json"))
        assertEquals("0.2.0-b1", inventory.version)
        assertEquals(15, inventory.sections.size)
    }
}
