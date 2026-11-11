package com.ichiyotsu.tokifonts

import java.net.URLEncoder
import org.json.JSONObject

data class FontGroup(val id: String, val name: String, val code: String, val blurb: String)

data class FontSection(val id: String, val name: String, val group: String)

data class FontFace(
    val id: String,
    val fileName: String,
    val exportName: String,
    val weight: Int,
    val weightName: String,
    val weightLabel: String,
    val italic: Boolean,
    val optical: String,
    val opticalLabel: String,
    val variable: Boolean,
    val format: String,
    val innerName: String,
    val bytes: Long,
    val repo: String,
    val ref: String,
    val path: String,
    val mirrorRepo: String,
    val mirrorRef: String,
    val mirrorPath: String,
) {
    fun usableExt(): String = when {
        innerName.endsWith(".otf", true) -> "otf"
        format == "otf" -> "otf"
        else -> "ttf"
    }

    fun urls(): List<String> {
        val primary = FontUrls.raw(repo, ref, path)
        if (mirrorRepo.isBlank() || mirrorPath.isBlank()) return listOf(primary)
        return listOf(primary, FontUrls.raw(mirrorRepo, mirrorRef, mirrorPath))
    }
}

data class FontFamily(
    val id: String,
    val name: String,
    val nameEn: String,
    val group: String,
    val section: String,
    val summary: String,
    val license: String,
    val alias: String,
    val tags: List<String>,
    val status: String,
    val statusDetail: String,
    val credit: String,
    val sources: List<String>,
    val faces: List<FontFace>,
) {
    /** 预览选 face 的统一排序：非可变优先、非斜体优先、常规光学尺寸优先、字重离 400 最近优先。 */
    fun rankedFaces(): List<FontFace> = faces.sortedWith(
        compareBy<FontFace> { it.variable }
            .thenBy { it.italic }
            .thenBy { if (it.optical == "Medium" || it.optical.isEmpty()) 0 else 1 }
            .thenBy { kotlin.math.abs(it.weight - 400) },
    )

    fun initialFace(): FontFace? = rankedFaces().firstOrNull()

    /**
     * 列表卡和详情页预览实际渲染用的 face：已下载（id 在 [readyIds] 里）的按 [rankedFaces]
     * 顺序取第一个——下了哪个字重就用哪个的字形预览，不能只认默认 face；
     * 一个都没下载时退回 [initialFace]，此时它只作“下载预览”的目标，不渲染真实字形。
     */
    fun previewFace(readyIds: Set<String>): FontFace? =
        rankedFaces().firstOrNull { it.id in readyIds } ?: initialFace()
}

data class Catalog(
    /** 目录数据版本（不是应用版本；生成脚本里写死在 catalog.json 里）。 */
    val dataVersion: String,
    val mergeNote: String,
    val groups: List<FontGroup>,
    val sections: List<FontSection>,
    val families: List<FontFamily>,
    /** 解析时跳过的损坏条目数（坏 family / face 各计 1）。 */
    val skipped: Int = 0,
) {
    fun family(id: String) = families.firstOrNull { it.id == id }
    fun face(id: String) = families.asSequence().flatMap { it.faces }.firstOrNull { it.id == id }

    companion object {
        /**
         * 容错解析：坏掉的 group / section / family / face 只跳过该条并计入 [Catalog.skipped]，
         * 不再因为一条坏记录让整个字体库打不开。只有根 JSON 本身非法时才抛异常。
         */
        fun parse(text: String): Catalog {
            val root = JSONObject(text)
            var skipped = 0
            val onSkip = { skipped++ }
            val groups = root.optJSONArray("groups")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = runCatching {
                            val o = arr.getJSONObject(i)
                            FontGroup(o.getString("id"), o.getString("name"), o.getString("code"), o.getString("blurb"))
                        }.getOrNull()
                        if (item == null) onSkip() else add(item)
                    }
                }
            } ?: emptyList()
            val sections = root.optJSONArray("sections")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = runCatching {
                            val o = arr.getJSONObject(i)
                            FontSection(o.getString("id"), o.getString("name"), o.getString("group"))
                        }.getOrNull()
                        if (item == null) onSkip() else add(item)
                    }
                }
            } ?: emptyList()
            val families = root.optJSONArray("families")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = runCatching { parseFamily(arr.getJSONObject(i), onSkip) }.getOrNull()
                        if (item == null) onSkip() else add(item)
                    }
                }
            } ?: emptyList()
            // 逐条容错不等于根级也吞：families 缺失或全坏时返回空库会让 UI 只剩一片空白、
            // 且 catalog != null 把重试挡死。根级损坏必须抛错，走 loadError + 重试路径。
            if (families.isEmpty()) throw org.json.JSONException("catalog 缺少 families 或全部损坏")
            return Catalog(
                dataVersion = root.optString("version"),
                mergeNote = root.optString("mergeNote"),
                groups = groups,
                sections = sections,
                families = families,
                skipped = skipped,
            )
        }

        private fun parseFamily(o: JSONObject, onSkip: () -> Unit): FontFamily {
            val facesJson = o.optJSONArray("faces")
            val faces = facesJson?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val face = runCatching { parseFace(arr.getJSONObject(i)) }.getOrNull()
                        if (face == null) onSkip() else add(face)
                    }
                }
            } ?: emptyList()
            val tagsJson = o.optJSONArray("tags")
            val sourcesJson = o.optJSONArray("sources")
            return FontFamily(
                id = o.getString("id"),
                name = o.getString("name"),
                nameEn = o.optString("nameEn"),
                group = o.getString("group"),
                section = o.getString("section"),
                summary = o.optString("summary"),
                license = o.optString("license"),
                alias = o.optString("alias"),
                tags = tagsJson?.let { arr -> List(arr.length()) { arr.getString(it) } } ?: emptyList(),
                status = o.optString("status"),
                statusDetail = o.optString("statusDetail"),
                credit = o.optString("credit"),
                sources = sourcesJson?.let { arr -> List(arr.length()) { arr.getString(it) } } ?: emptyList(),
                faces = faces,
            )
        }

        private fun parseFace(o: JSONObject) = FontFace(
            id = o.getString("id"),
            fileName = o.getString("fileName"),
            exportName = o.text("exportName").ifBlank { o.getString("fileName") },
            weight = o.optInt("weight", 400),
            weightName = o.text("weightName"),
            weightLabel = o.text("weightLabel"),
            italic = o.optBoolean("italic"),
            optical = o.text("optical"),
            opticalLabel = o.text("opticalLabel"),
            variable = o.optBoolean("variable"),
            format = o.text("format"),
            innerName = o.text("innerName"),
            bytes = o.optLong("bytes"),
            repo = o.getString("repo"),
            ref = o.getString("ref"),
            path = o.getString("path"),
            mirrorRepo = o.text("mirrorRepo"),
            mirrorRef = o.text("mirrorRef"),
            mirrorPath = o.text("mirrorPath"),
        )

        private fun JSONObject.text(key: String): String {
            if (!has(key) || isNull(key)) return ""
            return optString(key)
        }
    }
}

object FontUrls {
    fun raw(repo: String, ref: String, path: String): String {
        val encoded = path.split("/").joinToString("/") { segment ->
            URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
        }
        return "https://raw.githubusercontent.com/$repo/$ref/$encoded"
    }
}

/**
 * 详情页预览的预设参考文本：（短标签，全文）。点一下就把该字体的预览文字换成这段，
 * 不用自己现想句子；它和自定义输入走同一条通道——选预设就是把全文设为该字体的预览文字，
 * 之后随时能在输入框里改；输入框清空时，列表卡会回退到默认文本展示。
 */
val PREVIEW_PRESETS: List<Pair<String, String>> = listOf(
    "兰亭序" to "永和九年，岁在癸丑",
    "简繁对照" to "字体之美，源于细节；字體之美，源於細節",
    "英文字母" to "AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz",
    "数字标点" to "0123456789 ，。、；：？！（）【】％＠＃＆＊",
    "日文假名" to "いろはにほへと ちりぬるを アイウエオカキクケコ",
)

fun Long.toSizeLabel(): String {
    if (this < 1024) return "$this B"
    val kb = this / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    return String.format("%.1f MB", kb / 1024.0)
}
