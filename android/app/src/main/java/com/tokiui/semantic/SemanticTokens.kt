package com.tokiui.semantic

import org.json.JSONObject

/** 一个品牌种子色。运行时色板由各平台自己生成或引用参考色。 */
data class SeedSpec(val id: String, val name: String, val value: String)

/**
 * 文字角色。
 *
 * [referenceSizeSp] 只是默认缩放下的对照值。排版必须使用 [androidStyle] /
 * [appleStyle] / [windowsStyle] 指向的平台文字样式，让系统缩放生效。
 */
data class TypeRole(
    val id: String,
    val weight: String,
    val referenceSizeSp: Int,
    val androidStyle: String,
    val appleStyle: String,
    val windowsStyle: String,
)

/**
 * `tokens/tokiui.semantic.tokens.json` 的内存模型。
 * 其他 Toki 应用把本目录加进源码集后，直接调用 [parse]。
 */
data class SemanticTokens(
    val name: String,
    val version: String,
    val seed: String,
    val seeds: List<SeedSpec>,
    val light: Map<String, String>,
    val dark: Map<String, String>,
    val space: Map<String, Int>,
    val shape: Map<String, Int>,
    val typeScalesWithSystem: Boolean,
    val typeRoles: List<TypeRole>,
    val motionShortMs: Int,
    val motionStandardMs: Int,
    val motionEmphasizedMs: Int,
    val motionReduced: String,
    val materialPersistent: String,
    val materialTransient: String,
    val materialFallback: String,
    val materialLightGlass: String,
    val materialSolidBar: String,
    val materialScrollTitle: String,
    val typeLatin: String,
    val typeCjk: String,
    val typeCjkBundledAs: String,
) {
    fun palette(darkTheme: Boolean): Map<String, String> = if (darkTheme) dark else light

    companion object {
        val colorRoles: List<String> = listOf(
            "canvas", "surface", "surfaceElevated", "textPrimary", "textSecondary",
            "accent", "onAccent", "accentContainer", "onAccentContainer",
            "danger", "onDanger", "border",
        )
        val spaceKeys: List<String> = listOf("xs", "sm", "md", "lg", "xl", "xxl")
        val shapeKeys: List<String> = listOf("compact", "subtle", "standard", "emphasized", "large", "full")
        private val hex = Regex("^#[0-9A-Fa-f]{6}$")

        fun parse(json: String): SemanticTokens {
            val root = JSONObject(json)
            val color = root.getJSONObject("color")
            val seedsJson = color.getJSONArray("seeds")
            val seeds = buildList {
                for (i in 0 until seedsJson.length()) {
                    val item = seedsJson.getJSONObject(i)
                    val value = item.getString("value")
                    require(hex.matches(value)) { "种子色不是 #RRGGBB：${item.getString("id")}" }
                    add(SeedSpec(item.getString("id"), item.getString("name"), value.uppercase()))
                }
            }
            require(seeds.isNotEmpty()) { "至少要有一个种子色" }
            val seed = color.getString("seed").uppercase()
            require(hex.matches(seed)) { "color.seed 不是 #RRGGBB" }
            require(seeds.any { it.value == seed }) { "color.seed 必须出现在 seeds 里" }

            val type = root.getJSONObject("type")
            val scales = type.getBoolean("scalesWithSystem")
            require(scales) { "type.scalesWithSystem 必须为 true：字号跟随系统缩放" }
            val typeLatin = type.getString("latin").trim()
            val typeCjk = type.getString("cjk").trim()
            val typeCjkBundledAs = type.getString("cjkBundledAs").trim()
            require(typeLatin.isNotEmpty() && typeCjk.isNotEmpty() && typeCjkBundledAs.isNotEmpty()) {
                "type.latin / type.cjk / type.cjkBundledAs 不能为空"
            }
            val rolesJson = type.getJSONArray("roles")
            val roles = buildList {
                for (i in 0 until rolesJson.length()) {
                    val item = rolesJson.getJSONObject(i)
                    require(!item.has("line") && !item.has("lineHeight")) { "文字角色不能写死行高" }
                    val size = item.getInt("referenceSizeSp")
                    require(size > 0) { "referenceSizeSp 必须为正" }
                    val weight = item.getString("weight")
                    require(weight == "bold" || weight == "regular") { "文字字重只能是 bold 或 regular" }
                    add(
                        TypeRole(
                            id = item.getString("id"),
                            weight = weight,
                            referenceSizeSp = size,
                            androidStyle = item.getString("android"),
                            appleStyle = item.getString("apple"),
                            windowsStyle = item.getString("windows"),
                        ),
                    )
                }
            }
            val expectedRoles = listOf("display", "headline", "title", "body", "bodyEmphasized", "label", "caption")
            require(roles.map { it.id } == expectedRoles) { "文字角色缺失或顺序不对" }

            val motion = root.getJSONObject("motion")
            val shortMs = motion.getInt("shortMs")
            val standardMs = motion.getInt("standardMs")
            val emphasizedMs = motion.getInt("emphasizedMs")
            require(shortMs in 1..standardMs && standardMs <= emphasizedMs) { "动效时长必须递增" }
            val material = root.getJSONObject("material")
            fun materialText(key: String): String {
                val value = material.getString(key).trim()
                require(value.isNotEmpty()) { "material.$key 不能为空" }
                return value
            }

            return SemanticTokens(
                name = root.getString("name"),
                version = root.getString("version"),
                seed = seed,
                seeds = seeds,
                light = colorMap(color.getJSONObject("light")),
                dark = colorMap(color.getJSONObject("dark")),
                space = intMap(root.getJSONObject("space"), spaceKeys),
                shape = intMap(root.getJSONObject("shape"), shapeKeys),
                typeScalesWithSystem = scales,
                typeRoles = roles,
                motionShortMs = shortMs,
                motionStandardMs = standardMs,
                motionEmphasizedMs = emphasizedMs,
                motionReduced = motion.getString("reduced"),
                materialPersistent = materialText("persistent"),
                materialTransient = materialText("transient"),
                materialFallback = materialText("fallback"),
                materialLightGlass = materialText("lightGlass"),
                materialSolidBar = materialText("solidBar"),
                materialScrollTitle = materialText("scrollTitle"),
                typeLatin = typeLatin,
                typeCjk = typeCjk,
                typeCjkBundledAs = typeCjkBundledAs,
            )
        }

        private fun colorMap(obj: JSONObject): Map<String, String> {
            val map = linkedMapOf<String, String>()
            for (role in colorRoles) {
                val value = obj.getString(role).uppercase()
                require(hex.matches(value)) { "$role 不是 #RRGGBB" }
                map[role] = value
            }
            return map
        }

        private fun intMap(obj: JSONObject, keys: List<String>): Map<String, Int> {
            val map = linkedMapOf<String, Int>()
            for (key in keys) {
                val value = obj.getInt(key)
                require(value >= 0) { "$key 不能为负" }
                map[key] = value
            }
            return map
        }
    }
}
