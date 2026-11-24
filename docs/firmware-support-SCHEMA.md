# firmware-support.json — 合并固件支持 feed：schema 说明

本文件说明 `docs/firmware-support.json` 的字段含义、取值与 App 侧校验规则。
该 JSON 是 TokiFonts Pro 轨安装向导第 2 步（Shizuku → root 工具 → KernelSU →
模块）的“哪个工具支持哪台设备”的唯一数据来源：root-my-s24 与
Root-My-Galaxy 两路载荷合并在一份 feed 里，由
`.github/workflows/update-device-list.yml` 每天从上游重新生成。

> 位置：`docs/firmware-support.json`，随仓库发布，可通过
> `https://raw.githubusercontent.com/ichiyotsu/Tokifonts/Pro/docs/firmware-support.json`
> 拉取（分支名以实际 Pro 分支为准）。

## 顶层字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `schemaVersion` | 整数 | 是 | 本合并 feed 的 schema 版本。当前为 **1**。与 Root-My-Galaxy-Payloads 自身的 `schemaVersion`（当前 3）是两回事，不要混淆。 |
| `updatedAt` | 字符串 | 是 | 本次生成的 UTC 时间，ISO 8601，形如 `2026-11-24T07:07:52Z`。 |
| `generator` | 字符串 | 否 | 生成方式说明（种子转录 / Action 生成）。 |
| `sources` | 数组 | 是 | 上游来源，每项见下表。至少包含 root-my-s24 README 与 Root-My-Galaxy-Payloads 两项。 |
| `rules` | 对象 | 是 | 客户端匹配规则（已修复 43499 的固件、跨机型候选、推荐等级），见“rules”一节。 |
| `notes` | 字符串数组 | 否 | 跨条目的通用说明（DZF2 覆盖范围、EZIF 覆盖 EZJ1、美版不支持、98260 一次开机一次）。 |
| `entries` | 数组 | 是 | 支持条目，至少 1 条；为空的 feed 是无效的。 |

### sources[] 每一项

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | 字符串 | 是 | 来源名，如 `root-my-s24 README`、`Root-My-Galaxy-Payloads targets-v3.json`。 |
| `url` | 字符串 / null | 是 | 上游公开 raw URL；本地快照（如 App 内表）为 null。 |
| `version` / `schemaVersion` | 字符串 / 整数 | 否 | 上游版本标记：README 取 `v3.7.0-alpha.1` 这类 tag；RMG feed 取其自身的 `schemaVersion`（3）。 |
| `fetchedAt` | 字符串 | 是 | 该来源的抓取 / 转录时间，ISO 8601 UTC。 |
| `note` | 字符串 | 否 | 备注，如“种子由本地研究快照转录，非实时拉取”。 |

## entries[] 每一项

### 通用字段（所有条目都有）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | 字符串 | 是 | 全 feed 唯一稳定标识。`rms24-<固件构建号>`、`rmg-<payloadId>`、`rms24-ported-<系列>`。**Action 再生成时靠它把人工写的 `notes` 带回到新条目上**，不要改它的生成规则。 |
| `tool` | 字符串 | 是 | `root-my-s24` 或 `root-my-galaxy`。 |
| `kind` | 字符串 | 是 | `exact`（按机型精确匹配的行）或 `ported-series`（按系列 + 内核 + 固件后缀匹配的移植行）。 |
| `model` | 字符串 / null | 是 | 精确的 `Build.MODEL`（如 `SM-S9280`）。`kind=ported-series` 时为 null，改用 `modelPrefixes`；`tool=root-my-galaxy` 时为 null，改用 `models`（feed 里一条 payload 覆盖多台机型）。 |
| `firmwareBuild` | 字符串 / null | 是 | 固件构建号（如 `S9280ZCS6DZF2`）。共用载荷的行写 `共用 DZF2` 这类文字，匹配时用 `matchKeys`；移植系列行写后缀拼串（如 `DZE2 / DZF2 / DZG1`），无后缀时为 null；RMG 行恒为 null——**feed 里本来就没有固件构建号，绝不能编一个**。 |
| `kernel` | 字符串 / null | 是 | 内核版本展示串。精确行是单个版本（如 `6.1.145`）；移植系列行可能是 `5.15 / 6.1 / 6.6` 这类拼串。 |
| `engine` | 字符串 / null | 是 | exploit 引擎。root-my-s24 行：`GhostLock` 或 `98260 UAF`；root-my-galaxy 行：`GhostLock (CVE-2026-43499)`；移植系列行恒为 null（引擎以 RootMyS24 内实际载荷为准）。 |
| `driver` | 字符串 / null | 是 | KernelSU 驱动。98260 行：`KernelSU 3.2.5（默认）`；GhostLock 行：`KernelSU 3.3.0（实验）`；RMG 行：`随载荷提供（KernelSU 工件）`——**feed 里没有 KernelSU 版本号，绝不能编**；移植系列行恒为 null。 |
| `status` | 字符串 | 是 | 见下节“三档 status”。 |
| `source` | 字符串 | 是 | 行来源：`rms24-readme:tested` / `rms24-readme:calibrated` / `rms24-readme:ported` / `rmg-targets-v3`。 |
| `notes` | 字符串 | 是 | 备注，可为空串。人工备注靠 `id` 在 Action 再生成时带回。 |

### kind=exact 且 tool=root-my-s24 的附加字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `modelLabel` | 字符串 | 展示用机型，如 `SM-S9280 国行`。 |
| `matchKeys` | 字符串数组 | 固件匹配用的子串（默认 = `firmwareBuild`）。三星的 `Build.DISPLAY` / `Build.VERSION.INCREMENTAL` 里嵌着完整固件号，所以用子串比对。共用载荷的行只用载荷标识，如 `["DZF2"]`。 |

### kind=exact 且 tool=root-my-galaxy 的附加字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `payloadId` | 字符串 | feed 里的 `payloadId`，如 `e3q-S9280ZCS6DZF2`。 |
| `displayName` | 字符串 | feed 里的 `displayName`，照抄。 |
| `models` | 字符串数组 | feed 里的精确 `Build.MODEL` 列表，照抄。 |
| `kernelVersions` | 字符串数组 | feed 里的三段式内核版本列表，照抄，如 `["6.1.145"]`。 |
| `exploit` | `{url, size}` | feed 里的 exploit 工件（`cve-2026-43499-app.so`）直链与字节数，照抄。 |
| `kernelsu` | `{url, size}` | feed 里随载荷提供的 KernelSU 工件直链与字节数，照抄。 |
| `requiresFreshP0Session` | 布尔 | feed 里的同名字段，缺省 `false`。为 `true` 时滑块发现与利用必须在同一个载荷进程里跑，App 侧要关掉按启动缓存的 P0 会话。 |

### kind=ported-series 的附加字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `series` | 字符串 | 系列名，如 `S23` / `S24` / `S25` / `S26` / `折叠屏` / `心系天下`。 |
| `modelPrefixes` | 字符串数组 | `Build.MODEL` 前缀（大写比对），如 `["SM-S9210", "SM-S9260", "SM-S9280"]`。折叠屏行用 `SM-F73` 这类短前缀。 |
| `firmwareSuffixes` | 字符串数组 | 固件后缀（大写子串比对），如 `["DZE2", "DZF2", "DZG1"]`；为空表示只按机型 + 内核匹配（折叠屏行）。 |
| `kernels` | 字符串数组 | 匹配用的内核 major.minor，如 `["5.15"]`、`["5.15", "6.1", "6.6"]`。 |
| `regions` | 字符串 | 展示用地区 / 固件说明，如 `国行 DZE2 / DZF2 / DZG1，港台 DZG1`。 |

## 三档 status

| status | 含义 | App 侧处理 |
|---|---|---|
| `已实测` | 上游 README「已实测成功」表里的行，真机 root 成功过。 | 可直接推荐（推荐等级 0）。 |
| `待验证` | 上游 README「已定标，待真机验证」表里的行：已定标、未实测。包括社区定标但 App 内标 `tested=false` 的行（如 SM-S9280 DZJ3，PR #44 手动 staging 成功过，但 App 内仍算待验证）。 | **绝不能自动放绿灯**：必须由用户额外明确确认才尝试，文案写“待验证（已定标）载荷”，绝不能写“已实测”。（推荐等级 1 / 跨机型候选同级处理。） |
| `移植` | 另一工具已发布的载荷（RMG feed 的 20 条，或 root-my-s24 README 里的移植载荷表），未经本 App / root-my-s24 真机实测。 | 与待验证同级处理：需用户额外确认；RMG 行还要满足“机型 + 三段式内核精确匹配”且固件未修复 CVE-2026-43499。（推荐等级 2 / 3。） |

注意：`status` 只描述“验证程度”，不描述“匹配形状”。一条 RMG 行的 `kind` 是 `exact`
（机型 + 内核精确匹配），但 `status` 是 `移植`——两者不要混为一谈。

## rules

| 字段 | 说明 |
|---|---|
| `rules.patched43499` | 已修复 CVE-2026-43499 的固件：`{model, firmwareSuffixes[]}`。目前只有 `SM-S9280 → ["DZJ3"]`。这类固件上 GhostLock/43499 引擎不可用，RMG 的载荷也一律不可用（RMG 只有 43499 这一条引擎），只能走 98260。匹配时先查这张表，再查 entries。 |
| `rules.crossModelCandidate` | 跨机型候选规则：国行 S24+（`SM-S9260`）可用 S24 Ultra（`SM-S9280`）同构建号后缀（DZF2/DZG1/DZJ3）的载荷，限 6.1 内核线。已实测行优先；待验证行作候选时按待验证对待。只在 root-my-s24 工具内生效，绝不串到 RMG 去。 |
| `rules.recommendationLevels` | 双工具推荐等级（数字越小越优先，与 App 内 `FirmwareSupport.recommendTool` 一致）：0 root-my-s24 精确行·已实测；1 root-my-s24 精确行·待验证；2 root-my-galaxy 载荷（移植）；3 root-my-s24 移植载荷表；4 root-my-s24 跨机型候选。 |

## App 校验规则（读 feed 时必须执行）

App 读到 `docs/firmware-support.json`（内置快照或从 raw 拉取的新版）时，按以下
规则校验，**任何一条不通过就拒绝整份 feed、沿用上一次好的版本，绝不能用
部分数据凑合**：

1. **顶层结构**：JSON 必须能解析；`schemaVersion` 必须是整数且等于 App 支持的版本
   （当前为 1）；**`schemaVersion` 不符就拒绝**（不要试图按旧 schema 猜字段）。
2. **`updatedAt` 必须存在**且是合法的 ISO 8601 UTC 时间；**缺失就拒绝**。
   另：`updatedAt` 不得晚于当前时间超过 24 小时（时钟漂移容限），否则视为可疑数据，拒绝。
3. **`entries` 必须是非空数组**；每条 `id` 必须非空且全 feed 唯一；`tool` 只能是
   `root-my-s24` / `root-my-galaxy`；`status` 只能是 `已实测` / `待验证` / `移植`；
   `kind` 只能是 `exact` / `ported-series`。
4. **字段完整性按 kind 检查**：
   - `kind=exact` 且 `tool=root-my-s24`：`model`、`firmwareBuild`、`kernel`、
     `engine`、`driver` 均非空；`matchKeys` 非空数组。
   - `kind=exact` 且 `tool=root-my-galaxy`：`models`、`kernelVersions` 非空数组；
     `exploit.url` / `kernelsu.url` 必须以 `https://raw.githubusercontent.com/` 开头，
     `size` 为正整数；`firmwareBuild` 必须为 null（feed 里没有固件号，有值即为脏数据）。
   - `kind=ported-series`：`modelPrefixes`、`kernels` 非空数组；`engine`、`driver`
     必须为 null；`series` 非空。
5. **`sources` 必须包含 `root-my-s24 README` 与 `Root-My-Galaxy-Payloads` 两项**，
   且每项的 `fetchedAt` 存在。
6. **规模 sanity**：`entries` 总数不得低于上一次好版本的 50%。上游一次性删掉一半
   以上的行，几乎一定是解析事故而非真实下线——拒绝，等人工看。
7. **规则表**：`rules.patched43499` 必须是数组（可为空）；`rules.recommendationLevels`
   存在时必须是 5 条等级说明（0–4）。

校验失败时 App 的行为：记一条日志（哪条规则、哪个 `id`），继续用内置的上一份
好 feed，并在安装页顶栏提示“设备列表不是最新”。**永远不要因为 feed 坏了就把
设备判为支持或不支持**——支持判定只用校验通过的 feed 做。

## 版本演进

- `schemaVersion` 只有在字段增删改、校验规则变化时才 +1；只加条目、只改 `notes`
  不动版本。
- 升版时：先改本文件，写清新旧字段对照与 App 的兼容策略（拒绝旧版还是向前兼容），
  再让 Action 生成新版 JSON；App 发版时把支持的 `schemaVersion` 一起带上去。
- `sources[].schemaVersion`（RMG feed 自身的版本，当前 3）变化时，Action 必须先
  人工确认新 feed 结构再合入——不要自动把未知结构写进合并 feed。
