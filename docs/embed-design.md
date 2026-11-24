# 内嵌 exploit 设计清单（Pro 轨调研结论）

> 性质：只写调研结论，不写代码。所有路径与版本号都以 2026-11-24 从上游
> 公开仓库读到的为准（root-my-s24 README v3.7.0-alpha.1；
> Root-My-Galaxy v0.2.65；Root-My-Galaxy-Payloads targets-v3.json schema 3）。
> 内嵌动手前，逐项按“待核实”一节复核；任何一项对不上就停下，不要猜。

## (a) 必须带走的上游文件 / 二进制清单

内嵌的目标是：不依赖另外安装 root-my-s24 也能跑完一次临时 root。
按上游仓库结构（`NanoTurtle1145/root-my-s24`），要带走的东西分四组。
只列“必须”，不确定的进“待核实”。

### A1. GhostLock（CVE-2026-43499）引擎——按固件分的载荷

上游位置：`app/src/main/assets/`（随包分发的预编译二进制，构建链在开发者侧）。

| 带走什么 | 说明 |
|---|---|
| `cve-2026-43499` | GhostLock exploit 主体（二进制，按目标编译）。 |
| `cve-2026-43499-root` | root helper（提权后执行的辅助程序）。 |
| `cve-2026-43499-<TAG>` | 按构建号分的载荷变体，如 `cve-2026-43499-S9280ZCS6DZF2`、`cve-2026-43499-dze2`、`cve-2026-43499-cza1`、`cve-2026-43499-cza1-chc`、`cve-2026-43499-czb2`、`cve-2026-43499-byh7`、`cve-2026-43499-czg1`、`cve-2026-43499-S928BXXS4CZA1`、`cve-2026-43499-S928BXXU4BYD9`、`cve-2026-43499-S928BXXU4AXK5`、`cve-2026-43499-W9023ZCSAIZF1`、`cve-2026-43499-dze2-xaa`（美版，未发布）。**载荷绝不能跨构建号混用**：同构建号不同地区都可能差 8 字节（如港版 CZA1 与欧版 CZA1 的 `.rodata` 差 8 字节，不可互换）；国行 CZA1 的数据段偏移 +0x10000。 |
| 载荷选择逻辑 | 载荷按“机型 + 固件构建号”选；DZF2 载荷自 v3.5.0 起覆盖 DZE2–DZH3（同一条 6.1.145 内核线），外版 DZE2 载荷覆盖 DZE2–DZG1——覆盖关系要进表，不要靠文件名猜。 |

源码侧（只在需要自己编译载荷时带走，不随包分发也可）：
`app/src/main/cpp/` 下的 exploit 源码（不含 GhostLock 的独立 `.c` 顶层文件，
见待核实项——GhostLock 载荷的源码位置须先确认）。

### A2. 98260 UAF（CVE-2026-98260）引擎——公共载荷 + per-target 叠加

上游位置：`app/src/main/assets/samtimer/` 与 `app/src/main/cpp/`。

| 带走什么 | 说明 |
|---|---|
| `samtimer/libtimer` | 98260 公共载荷（覆盖 EZIF/EZJ1，内核构建号 34343818）。 |
| `samtimer/uhexec` | `call_usermodehelper` 链路用的执行器。 |
| `samtimer/umh_action.sh`、`samtimer/umh_action-stock.sh` | UMH action 脚本（stock 版与普通版）。 |
| `samtimer/targets/<TAG>/libtimer` | **必需**，按构建号编译的 per-target 载荷。`<TAG>` = 完整构建号大写，如 `S9280ZCS6DZJ3`；运行时优先 targets 目录，缺失回退公共载荷。目前已合入：`S9280ZCS6DZJ3`（PR #44）、`S721BXXUEEZIF`。 |
| `samtimer/targets/<TAG>/uhexec`、`samtimer/targets/<TAG>/ksud` | 可选，与公共版本不同才放。 |
| `cpp/timer_samtimer.c` | 98260 UAF 公共载荷源码（含 safe unlink 安全拆除）。 |
| `cpp/targets/<TAG>/offsets.h`（+ `BUILD.md`） | 按构建号的偏移表，如 `S9280ZCS6DZJ3`、`S721BXXUEEZIF`。新构建号适配 = 加一个 target 目录，不改公共载荷。 |

98260 的覆盖判断**不看构建串，只看 `uname -r` 里的内核构建号**
（如 `6.1.162-android14-11-34343818-abS928BXXU6EZJB` → `34343818`）：
同一内核构建号的固件共用同一份载荷（EZIF / EZJ1 / EZJB / EZJD 都是
34343818）；内核构建号变了才需要新 target。

### A3. KernelSU 驱动产物（ksud）

上游位置：`app/src/main/assets/samtimer/`（root-my-s24 侧）与
`BuSung-dev/Root-My-Galaxy-Payloads` 的 `kernelsu/` 目录（RMG 侧）。

| 带走什么 | 说明 |
|---|---|
| `samtimer/ksud-32525` | KernelSU 3.2.5（32525），98260 引擎默认。`--ephemeral` late-load：不写 `/data/adb`、不跑 module stage、不自动打开管理器、不软重启。 |
| `samtimer/ksud-32601` | KernelSU 3.3.0（32601），GhostLock 旧固件 / 实验。 |
| `samtimer/ksud-331-kdp` | KernelSU 3.3.1 KDP（含 One UI 9），实验，v3.7.0-alpha.1 起可用；目前没有哪一行默认用它。 |
| RMG 侧 `kernelsu/ksud-*-kdp` | 每条 RMG 载荷随附的 ksud（如 `ksud-s25u-kdp`、`ksud-e3q-S9280ZCS6DZF2-kdp`），直链与字节数在 feed 里；**feed 里没有 KernelSU 版本号，绝不能编**。 |
| KernelSU 管理器 | **不打包**：用户另行安装，且版本必须与驱动匹配、必须是官方签名（重签名 / fork 版一律认作“未安装”）。驱动 3.2.5 ↔ 管理器 v3.2.5（32525）；驱动 3.3.0 ↔ 管理器 v3.3.0（32601）。 |

驱动版本清单在上游是由服务端在线下发的（只对提供该驱动的固件生效，
不适用的固件自动回退默认驱动并提示）；内嵌后没有服务端，**回退规则
必须在本地实现**（默认驱动 + 提示），不能留一个“等服务端告诉我”的口子。

### A4. Shizuku 服务相关

| 带走什么 | 说明 |
|---|---|
| Shizuku UserService 侧代码 | 上游 `app/src/main/kotlin/cn/nanoturtle/rootmys9280/manager/rootmy/` 里的 `SamTimerUserService.kt`、`ShizukuController.kt`、`RootViewModel.kt`、`ShellExecutor.kt`、`RootShell.kt`、`PayloadStatus.kt`，以及 `app/src/main/aidl/` 下的 `IRootService` / `IRootCallback` 接口定义。exploit 跑在 **Shizuku UserService 常驻进程**里，App 切后台不会被回收——这是“熄屏跑、别切后台”仍能跑完的前提。 |
| Shizuku 本体 | **不打包**：用户另行安装的独立应用（包名 `moe.shizuku.privileged.api`）。内嵌只做“检测 Shizuku 是否运行中”，不内嵌 Shizuku。 |
| 无线调试直连 | 上游同目录下的 `AdbWirelessController.kt`、`AdbPairingClient.kt`、`AdbPairingFlow.kt`、`AdbKey.kt`、`AdbMdns.kt`、`PairingReplyService.kt`——Shizuku 不可用时的备用授权链路；是否内嵌与 Shizuku 主链路一起决策，不要只抄一半。 |
| 不带走的东西 | `server/`（公告 / 载荷清单 / 日志上报 / 论坛 / 统计自建接口）——内嵌后载荷清单改由本地 `docs/firmware-support.json` 提供，日志上报默认关；`manager-ui/` 与 `services/manager-service/`（前台服务模块）是否需要，等实现时按“没有它跑不跑得起来”逐项核对，不要整目录搬。 |

### A5. 必须一并带走的许可证文本

- `app/src/main/assets/gpl-3.0.txt`、`gpl-2.0.txt`（KernelSU 是 GPL-2.0）、
  `app/src/main/assets/licenses/SHIZUKU-APACHE-2.0.txt`。
- 树根 `LICENSE`（GPL-3.0）与 `THIRD-PARTY-NOTICES.md` 已就位；内嵌产物
  分发时，上述文本要随包走，不能只在仓库里放一份。

### RMG 侧内嵌的特殊性

- RMG 的载荷在 Payloads 仓库里是**按 payload 分目录**的：
  `artifacts/<payloadId>/cve-2026-43499-app.so` + `kernelsu/ksud-<…>-kdp`。
  注意仓库里的 `artifacts/` 目录比 feed 的 20 条多（还有如
  `a15-A155NKSS6BYH1`、`dm2q-S916BXXSAFZG1` 等未进 feed 的目录），
  **内嵌只取 feed 里的 20 条**，多出来的先审计再决定，不要顺手全搬。
- RMG 的 `.so` 是为 RMG 自己的 harness 编译的 GhostLock 载荷；
  能否直接塞进 root-my-s24 的 GhostLock 加载链路、还是要按 RMG 的
  加载方式另写一套，**未核实**（见待核实项）。在核实前，RMG 行在
  App 里只做“推荐去装 Root-My-Galaxy”，不做“本 App 直接跑”。

## (b) 驱动版本选择规则

上游依据：README「驱动版本」表、「管理器版本必须与驱动匹配」表、
「98260 引擎的三条硬规则」。

| 情况 | 驱动 | 管理器 | 备注 |
|---|---|---|---|
| 98260 引擎（6.1.162 / One UI 9.0） | **3.2.5（32525），默认** | 官方 v3.2.5（32525） | 推荐组合。 |
| GhostLock 旧固件（6.1.145 及更早） | 3.3.0（32601），实验 | 官方 v3.3.0（32601） | 支持 SELinux 隐藏。 |
| 3.3.1 KDP（含 One UI 9） | 3.3.1 KDP，实验 | 待上游明确 | v3.7.0-alpha.1 起可用；目前没有哪一行默认用它，不要主动推。 |
| BakaSU 内核模式（实验） | 三星适配的 BakaSU LKM | 同上 | 仅 SM-S9280 / S9280ZCS6DZH3（6.1.145）实测通过；开关默认关。 |

### 硬规则（写进预检，违反就拦截，不要只写文案提醒）

1. **6.1.145 / 6.1.162 的 98260 引擎只能用 3.2.5**：3.3.0 驱动在
   6.1.162 上会让 system_server 崩溃循环（→ RescueParty → 整机重启）。
   README 在三处重复这条（快速开始、硬规则第 2 条、EZIF 覆盖说明），
   是本次调研里重复次数最多的单条规则。
2. **版本错配时 `su` 可能还能用，但管理器会显示“未安装”或装不了模块**：
   预检必须同时核对驱动与管理器版本，不要只核对驱动。
3. **不要点管理器里的「安装 / 刷入」**：那是永久安装，会写 boot，
   锁定 BL 上有变砖风险。装模块只用「模块」页。这条要写进跑完后的
   成功页，不要只放在帮助里。
4. **DZJ3 只能走 98260**：DZJ3 已修复 CVE-2026-43499，GhostLock 不可用；
   3.3.0 驱动与 DZJ3 无关，不要把“旧固件用 3.3.0”套到它头上。
5. 驱动选择不要读 feed 里的版本号——**RMG feed 里根本没有 KernelSU
   版本号**；RMG 行的驱动就是“随载荷提供的那份 ksud 工件”，
   用 feed 里的 `kernelsu.url` + `size` 校验完整性，不要编版本号。

## (c) 保守预检清单

跑 exploit 之前必须全部通过；任何一项不过就停在向导里，不要进“开始 Root”。

### 机型 / 固件 / 内核匹配

- [ ] 机型（`Build.MODEL`）精确命中，且固件构建号（`Build.DISPLAY` /
      `Build.VERSION.INCREMENTAL` 子串）命中同一行。
- [ ] 只认 `status=已实测` 的行直接放行；`待验证` 与 `移植` 的行必须
      用户额外明确确认（文案写“待验证（已定标）载荷”，绝不能写“已实测”）。
- [ ] 先查已修复表：SM-S9280 DZJ3 这类已修复 CVE-2026-43499 的固件，
      GhostLock / RMG 载荷一律排除，只剩 98260 行。
- [ ] 内核版本要对得上：RMS24 行按 major.minor（如 6.1.145 行认 6.1.x 线，
      但载荷本身按构建号选）；RMG 行按 `uname -r` 前三段精确比对
      （如 `6.1.145`），固件构建号不参与 RMG 匹配。
- [ ] 跨机型候选（S9260 用 S9280 同构建号载荷）只在 root-my-s24 内生效，
      绝不串到 RMG；候选是“建议”，不是“支持”。
- [ ] 美版 S24 系列（SM-S921U / S926U1 / S928U / S928U1）直接判不支持，
      不要给“试试”的入口。
- [ ] 升级过的固件先核对构建号再跑：升级可能修掉漏洞；停在已适配固件。

### 环境

- [ ] Shizuku 正在运行（或无线调试已配对连接，二选一）；Shizuku 没起来
      就不要跑，不要边跑边等它起来。
- [ ] 驱动与管理器版本已按 (b) 节核对过，且管理器是官方签名。
- [ ] 建议熄屏、系统负载低、一轮跑完前别切后台、别开省电。
      （exploit 跑在 Shizuku UserService 常驻进程里，App 切后台不会被回收，
      但高负载会让成功率骤降——这是建议，不是拦截项。）

### 一次启动只跑一次（98260 的硬规则，v3.6.2 起上游 App 直接拦截）

- [ ] 本次开机内没有跑过 98260（成功、失败、软重启后都算跑过）。
- [ ] **失败绝不重试**：失败 / 软重启后先重启手机再跑；脏状态继续跑
      会在 plant 中途 panic。上游 App 在 v3.6.2 起直接拦截二次尝试，
      内嵌必须照做——失败页上不要放“再试一次”按钮。
- [ ] 失败要完整重启才能再试：GhostLock 失败可多试（这是 GhostLock 的
      规则，不要套到 98260 头上）；98260 失败 → 重启 → 再试。
- [ ] 看到 `parking without reap (zombie held)` 就重启：这是保护动作
      （parked 进程拿住页），不能强杀。v3.6.2 起有 safe unlink：
      parked 前把 held victim 的 3 个 CPU-clock timerqueue 树根置空，
      软重启不再演变成硬重启。
- [ ] 跑完别久留：exploit 会留 parked 进程占住内核页，事情做完就重启；
      避免长时间高负载（游戏 / 大量后台 / 反复锁屏）。
- [ ] 每次重启后需要重新跑一次「开始 Root」（临时 root，重启即干净）。

## (d) 待核实项

### 已核实（2026-11-24 从上游构建文件读到，改写了任务原定的“待核实”状态）

- **root-my-s24 的包名已确认**：上游 `app/build.gradle.kts` 第 157 行
  `applicationId = "cn.nanoturtle.rootmys9280"`，与
  `InstallWizard.kt` 里 `ROOT_MY_S24_PACKAGE` 按源码包路径推断的值一致；
  同文件注释（第 85 行附近）写明 versionCode 与 applicationId 全系列一致、
  签名相同——各 flavor 只加 `versionNameSuffix`（`-s24` / `-s25` /
  `-s23` / `-s26` / `-fold` / `-w` / `-exp`），不改 applicationId。
  命名空间是 `cn.nanoturtle.rootmys9280.manager`，不要把它当包名用。
  剩下没核实的只有**真机启动**：已安装的上游 release APK 是否响应
  这个包名的启动 intent——发版前真机点一次启动按钮验证。
- **Root-My-Galaxy 的包名已确认**：上游 `app/build.gradle.kts` 里
  `namespace = "dev.busung.s25uroot"` 且
  `applicationId = "dev.busung.s25uroot"`（v0.2.65，versionCode 13）。
  这补上了 `InstallWizard.kt` 里“上游 README 与本仓库都没有可核对的来源”
  的缺口。同样，写进向导前要真机验证一次启动；**启动失败一律回落到
  “去下载”，绝不假装已装**——这条对两个工具都适用。

### 仍待核实（动手前必须逐项确认）

1. GhostLock 载荷的源码位置：`app/src/main/cpp/` 顶层只有
   `timer_samtimer.c`（98260）、`dfr_*`（DirtyFrag 相关）与 `aes256.*`，
   没看到 GhostLock exploit 的独立源码文件；GhostLock 的 `.so` 是预编译
   进 assets 的，还是源码在别处（如 `cpp/targets/` 或子模块）？
   决定“内嵌是搬二进制还是自己编译”。
2. RMG 的 `.so` 能否直接塞进 root-my-s24 的 GhostLock 加载链路：
   两个项目的 GhostLock 载荷是不是同一套 ABI / 入口约定？没核实前，
   RMG 行只做“推荐去装 Root-My-Galaxy”。
3. `manager-ui/` 与 `services/manager-service/`（前台服务模块）是不是
   内嵌的必需部分：没有它们，提权流程跑不跑得起来？
4. SamTimer 上游的许可证：98260 实现参考 SamTimer v1.3，安全拆除补丁已回提
   SamTimer PR #2；内嵌 `timer_samtimer.c` 前核对 SamTimer 仓库的许可证，
   再决定引用方式，并在 THIRD-PARTY-NOTICES 里补一条。
5. `samtimer/targets/S721BXXUEEZIF/` 里的内容：按约定应有 `libtimer`，
   是否还有 `uhexec` / `ksud` 覆盖？（`S9280ZCS6DZJ3/` 里只有 `libtimer`，
   已确认；S721B 的那份只确认了目录存在。）
6. W9023N（5.10.236，beta）与 SM-S721B EZIF 两行：README 有、App 内表没有
   （见 `docs/firmware-support.json` 里这两条的 notes）；进 App 表前
   要不要先有人核对一遍，不要由 Action 静默带进 App 的匹配逻辑。
7. Root-My-Galaxy-Payloads feed 的 `requiresFreshP0Session=true` 的行
   （共 7 条）：滑块发现与利用必须在同一个载荷进程里跑，App 侧要关掉
   按启动缓存的 P0 会话并给单次组合尝试配目标专用的长超时——
   内嵌的加载器支不支持这个模式，要按 RMG 的实现核对，不要只抄字段。

## (e) 如实写明：没有中途暂停

- **exploit 跑起来后没有中途暂停**：从点“开始 Root”到出结果，中间没有
  暂停点、没有进度条能代表的“做到百分之几”、没有“取消后安全停在原地”
  的状态。界面只能有三种诚实状态：准备中（预检）、运行中（等结果，
  只能看、不能干预）、已出结果（成功 / 失败 + 下一步）。
- 界面文案绝不能承诺做不到的：
  - 不能写“暂停”“取消后可恢复”“还剩 X 秒”；
  - 不能把概率写成保证：GhostLock 失败可多试、98260 成功率不是 100%，
    文案要写“可能失败，失败请重启后重试”，不要写“保证成功”；
  - 不能把“待验证 / 移植”写成“已实测”，不能把“推荐”写成“支持”；
  - 失败页只能给一条路：重启手机再试（98260）或按规则多试一次
    （GhostLock），不要给“再试一次”按钮绕过一次启动一次的拦截。
- 运行日志是唯一的排障依据：上游做法是运行日志页 →「复制编号」
  （v3.6.3 起），把日志编号贴进 issue；内嵌也要保留“复制日志编号”
  的出口，不要只留一句“失败了”。
