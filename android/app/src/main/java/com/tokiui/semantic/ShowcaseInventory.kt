package com.tokiui.semantic

import org.json.JSONObject

/** 三端展示应用共用的场景。平台决定控件，清单决定要覆盖哪些交互。 */
enum class ShowcaseSectionKind {
    Color,
    Type,
    Shape,
    Motion,
    Materials,
    Buttons,
    TextFields,
    Pickers,
    Menus,
    Dialogs,
    Navigation,
    Lists,
    Feedback,
    Accessibility,
    Layout,
    ;

    val id: String
        get() = when (this) {
            Color -> "color"
            Type -> "type"
            Shape -> "shape"
            Motion -> "motion"
            Materials -> "materials"
            Buttons -> "buttons"
            TextFields -> "textFields"
            Pickers -> "pickers"
            Menus -> "menus"
            Dialogs -> "dialogs"
            Navigation -> "navigation"
            Lists -> "lists"
            Feedback -> "feedback"
            Accessibility -> "accessibility"
            Layout -> "layout"
        }

    companion object {
        fun fromId(id: String): ShowcaseSectionKind =
            entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("未知展示场景：$id")
    }
}

data class ShowcaseSection(val id: String, val title: String, val summary: String)

data class ShowcaseInventory(val version: String, val sections: List<ShowcaseSection>) {
    fun section(id: String): ShowcaseSection =
        sections.firstOrNull { it.id == id } ?: throw IllegalArgumentException("清单中没有场景：$id")

    companion object {
        fun parse(json: String): ShowcaseInventory {
            val root = JSONObject(json)
            val array = root.getJSONArray("sections")
            val sections = buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val id = item.getString("id")
                    val title = item.getString("title")
                    val summary = item.getString("summary")
                    require(id.isNotBlank() && title.isNotBlank() && summary.isNotBlank()) { "场景字段不能为空" }
                    add(ShowcaseSection(id, title, summary))
                }
            }
            val ids = sections.map { it.id }
            require(ids.distinct().size == ids.size) { "场景 id 重复" }
            val expected = ShowcaseSectionKind.entries.map { it.id }
            require(ids == expected) { "场景清单必须覆盖全部约定场景。期望 $expected，实际 $ids" }
            sections.forEach { ShowcaseSectionKind.fromId(it.id) }
            return ShowcaseInventory(root.getString("version"), sections)
        }
    }
}
