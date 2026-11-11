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
    fun initialFace(): FontFace? {
        val ranked = faces.sortedWith(
            compareBy<FontFace> { it.variable }
                .thenBy { it.italic }
                .thenBy { if (it.optical == "Medium" || it.optical.isEmpty()) 0 else 1 }
                .thenBy { kotlin.math.abs(it.weight - 400) },
        )
        return ranked.firstOrNull()
    }
}

data class Catalog(
    val version: String,
    val mergeNote: String,
    val groups: List<FontGroup>,
    val sections: List<FontSection>,
    val families: List<FontFamily>,
) {
    fun family(id: String) = families.firstOrNull { it.id == id }
    fun face(id: String) = families.asSequence().flatMap { it.faces }.firstOrNull { it.id == id }

    companion object {
        fun parse(text: String): Catalog {
            val root = JSONObject(text)
            val groups = root.getJSONArray("groups").let { arr ->
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    FontGroup(o.getString("id"), o.getString("name"), o.getString("code"), o.getString("blurb"))
                }
            }
            val sections = root.getJSONArray("sections").let { arr ->
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    FontSection(o.getString("id"), o.getString("name"), o.getString("group"))
                }
            }
            val families = root.getJSONArray("families").let { arr ->
                List(arr.length()) { i -> parseFamily(arr.getJSONObject(i)) }
            }
            return Catalog(
                version = root.optString("version"),
                mergeNote = root.optString("mergeNote"),
                groups = groups,
                sections = sections,
                families = families,
            )
        }

        private fun parseFamily(o: JSONObject): FontFamily {
            val facesJson = o.getJSONArray("faces")
            val faces = List(facesJson.length()) { i -> parseFace(facesJson.getJSONObject(i)) }
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

fun Long.toSizeLabel(): String {
    if (this < 1024) return "$this B"
    val kb = this / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    return String.format("%.1f MB", kb / 1024.0)
}
