package com.ichiyotsu.tokifonts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallerToolsTest {
    @Test fun everyToolHasUsableEntryLinks() {
        // 教程页顶部和安装页共用同一份 InstallerTools 数据渲染下载/官方按钮：
        // id 不能重复，两个入口的文案和链接都必须齐全且是 https，缺一个教程页就会出现点了没反应的按钮。
        assertEquals(InstallerTools.size, InstallerTools.map { it.id }.distinct().size)
        InstallerTools.forEach { tool ->
            assertTrue("${tool.id} 下载文案为空", tool.downloadLabel.isNotBlank())
            assertTrue("${tool.id} 官方文案为空", tool.officialLabel.isNotBlank())
            assertTrue("${tool.id} 下载链接不是 https", tool.downloadUrl.startsWith("https://"))
            assertTrue("${tool.id} 官方链接不是 https", tool.officialUrl.startsWith("https://"))
            assertTrue("${tool.id} 没有步骤", tool.steps.isNotEmpty())
        }
    }

    @Test fun stepsPointingUpwardsNameAnExistingButton() {
        // 步骤里写“点上面的「…」”的工具，那个被点名的按钮必须真实存在于本页顶部
        // （文案与 InstallerTool 的 downloadLabel / officialLabel 逐字一致），
        // 否则又会回到“教程让用户去点一个不存在的入口”的老问题。
        val referencing = InstallerTools.filter { tool -> tool.steps.any { it.contains("上面的") } }
        assertTrue("预期至少有工具的步骤引用顶部入口", referencing.isNotEmpty())
        referencing.forEach { tool ->
            val named = tool.steps.any { step ->
                step.contains("「${tool.downloadLabel}」") || step.contains("「${tool.officialLabel}」")
            }
            assertTrue("${tool.id} 的步骤引用了顶部入口，但没点名任何一个真实按钮", named)
        }
    }
}
