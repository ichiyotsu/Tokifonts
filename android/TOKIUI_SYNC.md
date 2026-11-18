# TokiUI 同步记录

Tokifonts 的设计层引用 TokiUI 语义标准。按 `docs/复用.md` 的精神，源头在
TokiUI 仓库；本仓库因工具限制无法跨仓库引用路径，改用**逐字 vendoring +
锁定测试**：下面四份文件是 TokiUI 仓库的逐字副本，本地不许手改；`TokiUiSeedsTest`
会解析 vendored 的 JSON，断言种子色与代码里用的种子一致，漂移会在单元测试里暴露。

## 来源

- 仓库：`ichiyotsu/TokiUI`（私有）
- Pinned commit：`e6021f6169874db56f6977d594e4a4e7b6e4c00c`（TokiStudio Android 0.1.10，tag `v0.1.10`，2026-11-18；四份 vendored 文件与该 commit 逐字核对一致）
- 语义标准版本：`0.2.0-b1`（用户口中的“TokiUI 0.1.10”是同期 TokiStudio Android
  应用的版本号；设计标准本身的版本以 token 文件里的 `version` 字段为准）

## Vendored 文件

| 本仓库路径 | TokiUI 仓库里的源路径 |
| --- | --- |
| `app/src/main/assets/tokiui.semantic.tokens.json` | `tokens/tokiui.semantic.tokens.json` |
| `app/src/main/assets/showcase.inventory.json` | `tokens/showcase.inventory.json` |
| `app/src/main/java/com/tokiui/semantic/SemanticTokens.kt` | `platforms/android/src/main/kotlin/com/tokiui/semantic/SemanticTokens.kt` |
| `app/src/main/java/com/tokiui/semantic/ShowcaseInventory.kt` | `platforms/android/src/main/kotlin/com/tokiui/semantic/ShowcaseInventory.kt` |

包名保留 `com.tokiui.semantic`，与上游一致。运行时种子色列表由
`TokiUiTokens` 解析 `assets/tokiui.semantic.tokens.json` 得到（见
`app/src/main/java/com/ichiyotsu/tokifonts/TokiUiTokens.kt`），不要在本仓库
另抄一份色值。

## 设计实现参照

除 token 外，本次重构的 Android 落地写法对照 TokiUI 仓库同一 commit 下的
TokiStudio Android 实现（`tokistudio/android/`）：主题（`ui/theme/Theme.kt`、
`Color.kt`、`AppTypeface.kt`）、玻璃（`ui/theme/Glass.kt`）、悬浮胶囊与独立钮
（`ui/components/Common.kt`）、设置页外观区（`ui/settings/SettingsScreen.kt`）。
规范文字见 TokiUI 的 `docs/语义.md`、`docs/platforms/android.md`、
`docs/platforms/材质.md`、`tokistudio/docs/DESIGN.md` 的“现行规则”一节。

## 重新同步步骤

1. 确认 TokiUI 的新 HEAD（`git ls-remote` 或仓库页面），记下完整 commit。
2. 把上表四份文件从新 HEAD 逐字拷过来，覆盖本地副本（不要合并、不要手改）。
3. 更新本文件里的 pinned commit。
4. 跑 `:app:testDebugUnitTest`：`TokiUiSeedsTest` 会在种子色变化时失败——
   此时按新 token 更新使用方（种子选择、默认种子），不要改测试去迁就旧值。
5. 跑 `:app:assembleRelease`，确认编译通过。
