# TokiFontsPro 源码融入 — 侦察结论与实施方案

- 日期：2026-11-25
- 范围：Shizuku + root-my-s24（只编国行 S24 系列 DZJ3）+ KernelSU，替代二进制内嵌
- 侦察报告：`~/workspace/work/tokifonts-source-recon/`（`shizuku.md`、`kernelsu.md`、`samtimer.md`；root-my-s24 侦察中止，无报告）
- 状态：第一阶段（侦察）完成；第二阶段（实施）完成（2026-11-25：工具链 + 搬运 + 构建接入 + 执行链接通）

## 0. 总结论

| 组件 | 结论 | Pin |
|---|---|---|
| Shizuku API（aidl / api / shared / provider） | 从源码编（Gradle 模块） | Shizuku-API @ `aaba9928`（= Maven 13.1.5） |
| Shizuku server / manager | 不搬、不编 | —（属于用户手机上的 Shizuku 应用） |
| KernelSU ksud + ksuinit | 从源码编（Rust/Cargo，Gradle 前加 cargo 步骤） | tiann/KernelSU @ `v3.3.1`（`0c0fdb96`） |
| KernelSU 内核 LKM（.ko） | 继续预编译 | —（需目标内核源码 + 三星补丁，CI 里没有） |
| SamTimer（98260 载荷 timer.c 等） | 继续预编译，源码一律不搬 | 无协议 |
| root-my-s24 DZJ3 路径 | exploit 部分继续预编译；App 胶水复用未核实 | — |
| GhostLock 按固件分载荷 | 继续预编译（DZJ3 不用，已打补丁） | — |

## 1. Shizuku

- 两个仓：`RikkaApps/Shizuku`（主仓，manager + server）与 `RikkaApps/Shizuku-API`（API 仓，AAR 源码出处）。`api/` 在主仓只是指向 API 仓的 git submodule。
- 协议：API 仓 **MIT**（`Shizuku-API/LICENSE`，Copyright (c) 2021 RikkaW；各模块 POM 同样写 MIT）——**纠正**：`THIRD-PARTY-NOTICES.md` 现写 Shizuku 是 Apache 2.0 是错的，第二阶段改成 MIT，条件只是保留 copyright + permission notice。主仓 **Apache 2.0** + 商标/图标禁令（禁 `Shizuku` 作应用名、禁 `moe.shizuku.privileged.api` 作 applicationId、禁相关权限、禁用 launcher 图标）。
- 能编：`aidl` / `shared` / `api` / `provider` 四模块，标准 `com.android.library`、纯 Java、无自定义构建逻辑，唯一外部依赖 `androidx.annotation:annotation:1.3.0`；依赖链 provider → api → shared → aidl。放进 `third-party/shizuku/`，剥离根 `build.gradle` 的 maven-publish + signing 发布逻辑与 `manifest.gradle` 版本逻辑；不搬 settings 级的 `FAIL_ON_PROJECT_REPOS`、version catalog（hidden-api/refine 是给 server-shared/rish 用的）；namespace 保留原样；不拷 `server-shared/`、`rish/`、`demo/`。
- 不该编进 Pro：`server/`（纯 Java/Kotlin 库，`ShizukuService.main()` 为入口）+ manager 的 jni（CMake 编 `libshizuku.so` 启动器、`libadb.so` 无线 ADB 配对）+ `shell/` 的 `rish_shizuku.dex`——特权来自进程身份（root uid 0 / shell uid 2000），不是代码；编进来以 Pro 自身 UID 跑是零特权，还拖 hidden-stub/refine/gson 等依赖。
- UserService 机制：App 侧 `Shizuku.bindUserService(UserServiceArgs, ServiceConnection)` → `IShizukuService.addUserService`（AIDL transaction 11）→ server 侧 `UserServiceManager.addUserService` → `app_process` 以 root/shell 拉起 Pro 自己的 service 类（须实现 `IBinder`，通常 extends 自家 AIDL Stub；v13 起可带 `Context` 构造器）；destroy 用 transaction `16777115`，须自调 `System.exit()`；`tag` 须显式设（R8 后类名不稳定）。全部协议面在 `Shizuku-API/aidl/src/main/aidl/moe/shizuku/server/` 的 4 个 AIDL（`IShizukuService.aidl` 核心，`getVersion=2`…`addUserService=11`…`attachUserService=101`）+ `shared/ShizukuApiConstants.java`（`SERVER_VERSION=13`、`SERVER_PATCH_VERSION=6`）。Pro 自己的业务 AIDL 仍需自己写，上游不提供。
- 13.1.5 没有 tag（13.x 从不打 tag，版本只在 `manifest.gradle`）：对应 commit `aaba9928f50f9df62c5e429318de14a0ed473480`（"Fix #54"，2023-09-21，patch 4→5 并修 `ShizukuProvider#requestBinderForNonProviderProcess` 在 Android 14 的 crash，与 README changelog 和 Maven Central 发布日期三方一致）。不要 pin HEAD（13.1.6 未发布到 Maven，工具链还跳变：API 仓 HEAD 要求 AGP 9.5.0 / compileSdk 37 / Java 17，主仓是 AGP 8.10.1 / compileSdk 36 / Java 21）。
- 实施轮待确认：Pro 是否已依赖 provider AAR；`aaba992` 树内当时的精确 AGP/compileSdk（须 checkout 后重读）；源码融入后的编译与 R8/tag 回归验证。

## 2. KernelSU

- 协议分区：`kernel/` = **GPL-2.0-only**（`kernel/LICENSE` 为 GPLv2 全文）；其余全部（ksud、ksuinit、manager、uapi）= **GPL-3.0-or-later**（根 `LICENSE` 为 GPLv3 全文）。证据：`docs/README.md` §License 两条分区声明 + 两份 LICENSE 全文 + `kernel/core/init.c:258` 的 `MODULE_LICENSE("GPL")`。Pro 已是 GPL-3.0，兼容；分发时须随附对应源码与协议文本。两份 LICENSE 随 vendored 树一起拷进 `third-party/kernelsu/`。
- ksud 能从源码编：Rust edition 2024 + Cargo workspace（`userspace/ksud/` + `userspace/ksuinit/`，根 `Cargo.toml` members 就是这两者）。上游 CI（`.github/workflows/ksud.yml`，ubuntu-latest）：Rust stable + NDK **r29** + API **26**，`cargo build --target aarch64-linux-android --release`。另需 libclang（bindgen 0.73）与 NDK clang（AArch64 汇编器）；git 依赖多，须随附 `Cargo.lock` 或提前 `cargo vendor`。**Gradle 编不了 ksud**：上游 Manager 构建也是先把编好的 ksud 拷进 `jniLibs/arm64-v8a/libksud.so` 再跑 `./gradlew`；Pro 应在 Gradle 之前加 cargo 步骤，产物放进 assets。ksuinit 同样可源码编（RUSTFLAGS 需 NDK 的 `libclang_rt` builtins + 16K page-size）。
- ksud 能脱离 Manager 独立用：`cli.rs` 有完整 CLI（module / late-load / insmod / install / boot-patch / sepolicy / profile / su 等），经 `ksucalls.rs` 直接走 supercall ioctl 与内核对话。Manager 只是 GUI + 安装器：它自己的 `libkernelsu.so`（`ksu.cc`）直连内核，另把 ksud 二进制改名 `libksud.so` 嵌进 APK 做搬运。注意 v3.3.1 起有三方版本匹配检查，ksud/内核/UAPI 必须同代；内核另有 Manager 包名/签名绑定（默认 `me.weishu.kernelsu`）。
- 内核 LKM 不能在 App 的 CI 里编：`kernel/` 是 C + Kbuild/Kconfig，`Makefile` 要求传入 `KDIR`（已准备好的目标内核树）；通用 GKI 靠特权容器 `ghcr.io/ylarod/ddk-min:<kmi>-20260828` 编 8 个 KMI（`android12-5.10`…`android17-6.18`）；S24 须另用三星内核源码 + 三星 KDP/RKP/Defex 补丁（补丁在 Root-My-Galaxy 载荷仓库，不在 tiann/KernelSU 里）；`.ko` 与固件 vermagic/符号绑定，一固件一编，不可通用。
- 三份内嵌 ksud 的映射（本地实测 + 上游 tag 实测交叉验证）：
  - `ksud-32525`（4,879,856 B，`1b92611d…`）：VERSION_CODE 32525、VERSION_NAME `3.2.5-JamXisu`，单嵌 `android14-6.1`。对应 v3.2.5（tag commit `b0bc817b…`，2026-06-23，UAPI 2），但 **`-JamXisu` 后缀在上游不存在**，全网搜不到该 fork，**无法用纯上游源码复现**，须问供应方。
  - `ksud-32601`（6,028,400 B，`6f618248…`）：`783d3d87 (uapi: 2)`，内嵌全部 8 个 KMI。对应上游 main 快照 commit `783d3d87eb569233e1e1c7e027768ae5eb5a6989`（"Allow installation of official KernelSU Manager without version mismatch"，2026-09-29，v3.3.0 与 v3.3.1 之间），裸短 hash 说明构建树无 tag；原则上可复现。
  - `ksud-331-kdp`（5,164,448 B，`dba3c362…`）：`3.3.1 (uapi: 5)`、VERSION_CODE 32680，单嵌 `android14-6.1`。对应 v3.3.1（tag commit `0c0fdb96…`，2026-10-12，UAPI 5）。但 **`-kdp` 不是上游命名**，是 Root-My-Galaxy 载荷仓库对三星 KDP 补丁 late-load 构建的叫法；纯上游源码只能复现其通用 daemon 一半，三星补丁 + S24 内核源码另需解决。
- 建议 pin **v3.3.1**（commit `0c0fdb960178fa12d80d6cbcfdcb6ad8be6a865d`，UAPI 5）；不要 pin main（侦察日 UAPI 已是 6）。
- 拷进 `third-party/kernelsu/` 的清单见 `kernelsu.md` §6.2（含两份 LICENSE、`Cargo.toml`/`Cargo.lock`、`.cargo/config.example.toml`、`setup_cargo_config.py`、`setup-rust-build.sh`、`ksud.yml`/`ksuinit.yml`、`userspace/`、`uapi/`、`kernel/` 源码留存）。
- submodule 还是 plain copy：上游建议 git submodule 钉 tag，因为 `userspace/ksud/build.rs` 编译时跑 `git rev-list --count HEAD` / `git describe` 生成版本号，无 `.git` 会退化成 `0.0.0`，v3.3.1 的版本匹配检查会判不匹配。**但 Pro 树的推送管线是快照 + push-files + 网页上传，不支持 gitlink（submodule）。** 实施轮做法：plain copy + 把版本号写死（patch `build.rs` 或用环境变量注入 pin 的 tag/commit），并在 CI 里校验编出 ksud 的版本串与 pin 一致。
- 继续预编译：各固件 `.ko`（DZJ3 三型号 + 其他固件）、通用 GKI `.ko`、`userspace/ksud/bin/<arch>/` 里已提交的 `bootctl` / `busybox` / `waitsys` 三个 blob（拷源码时会原样带入，需另处理才算零 blob）。
- 待核实（`kernelsu.md` §7）：`JamXisu` 是谁的 tag/fork（须问 `ksud-32525` 供应方）；`ksud-32601` 的 commit 数 2601 未独立数过（需完整克隆）；`ksud-331-kdp` 是否确用 RMG 的 `samsung-kdp-rkp-defex` 补丁（二进制内未找到 samsung/defex 字样，仅能由命名与形态推断）；Rust stable 的具体版本下限（edition 2024 需 ≥1.85，以 CI 当日为准）。

## 3. root-my-s24（DZJ3 only）与 SamTimer

- 范围（用户 2026-11-25）：只编国行 S24 系列 DZJ3（SM-S9210 / SM-S9260 / SM-S9280）；其它固件、其它机型一律不从源码编，已内嵌二进制保持原样。
- DZJ3 已打 CVE-2026-43499 补丁，只有 98260（samtimer）那条路可用，GhostLock 不用管。
- SamTimer 协议结论（`samtimer.md`）：`RiosWesley/SamTimer`（HEAD `4c90361`，tag `v1.3`，2026-11-04）全仓库**没有任何有效开源协议**——根目录、各源码目录均无 `LICENSE` / `COPYING` / `NOTICE`，README 无协议章节，全部源码文件头无协议头、无 SPDX、无版权声明，GitHub 协议检测 404。`timer.c:17` 的 "Adapted from woshigenm/CVE-2026-98260 (MIT)" 不能授权 SamTimer：MIT 只覆盖 woshigenm 自己的文件，而 `timer.c` 是 4,028 行三星专属移植，形态与上游完全不同，无法判定多少行源自 MIT 代码；SamTimer 也未履行 MIT 条件（仓内既无 woshigenm 版权声明也无 MIT 文本）；"other 98260 ports" 未点名，另一血缘 `CVE-2026-98260-aurora` 在公开搜索中找不到对应仓库、协议无法验证。Kotlin 应用层、`umh_action.sh`、`uhexec` 与 woshigenm 毫无关系。`uhexec` 在 SamTimer 仓库内只有预编译二进制，无源码、无构建脚本、无来源说明。
- 结论：**DZJ3 的 exploit 部分（98260 libtimer、uhexec、umh_action.sh）继续预编译，源码一律不搬，不扩大无协议源码的使用范围。** 合法后续路径：取得 RiosWesley（及 yoro1836）的书面授权并补协议后 pin 到授权后 commit；或彻底绕开 SamTimer、从已核实的 MIT 上游 `woshigenm` 自行重做三星移植（新移植工作，非“搬源码”，仍需单独评审）。
- root-my-s24 App 胶水复用：侦察中止，未核实。已确认的事实：仓库 `NanoTurtle1145/root-my-s24`，根 LICENSE 为 GPL-3.0；Gradle 模块 `:app`、`:manager-ui`、`:services:manager-service`；`app/build.gradle.kts` namespace `cn.nanoturtle.rootmys9280.manager`，按机型系列 flavor（all / s24 / s25 / s23 / s26 / fold / w / exp / gamma），资产由 `tools/split_assets.py` 分发；`app/src/main/assets/` 下有 `cve-2026-43499`、`cve-2026-43499-root`、`samtimer/` 等；`app/src/main/cpp/CMakeLists.txt` 只编译 DirtyFrag 的 `dfr` 库。App 层 Shizuku 服务调用与载荷 staging 逻辑的核查被安全分类器拦下，未完成；实施轮不再重读这部分，现有 Kotlin 预检/胶水（`EmbeddedRoot.kt`、SystemFontShell 等）保持原样，如需复用改为人工核查。
- GhostLock 按固件分载荷（`cve-2026-43499-<TAG>`）：源码位置未确认，继续预编译；DZJ3 不用 GhostLock。
- RMG .so（`docs/embed-inventory/rmg/`）：只做清单，不进 APK，不变。

## 4. 工具链与 CI

- 本机现状：NDK 未装（android-sdk 下无 ndk/cmake 目录）；Rust/cargo 是否装了待实施轮核查。
- ksud 源码编需要：Rust stable（edition 2024 需 ≥1.85）+ NDK r29（API 26）+ libclang + `Cargo.lock`（或 `cargo vendor`）。本地用 sdkmanager 装 NDK（注意代理/证书老规矩）；CI 里 `android-release.yml` 要加 NDK 安装步骤（+ Rust 工具链安装）。
- Shizuku API 源码编不需要 NDK（纯 Java，Gradle 模块）。
- SamTimer 若要编需要 NDK r27c，但协议拦住了，不编。

## 5. 第二阶段实施大纲

1. Vendor Shizuku-API @ `aaba9928`：`aidl` / `shared` / `api` / `provider` + MIT LICENSE 全文 → `third-party/shizuku/`（附 README：来源 URL + commit + 协议）；剥离发布/版本逻辑；`settings.gradle` 接入四模块；替换 `app/libs/` 的 3 个 AAR 依赖；回归验证（编译 + R8 + UserService tag）。
2. Vendor KernelSU @ `v3.3.1`：按 `kernelsu.md` §6.2 清单 plain copy → `third-party/kernelsu/`（附 README）；patch `build.rs` 写死版本号（pin v3.3.1，替代 git describe）；Gradle 前加 cargo 构建步骤（产物进 assets）；本地装 NDK r29 + Rust；CI 加 NDK/Rust 安装步骤。
3. 修正 `THIRD-PARTY-NOTICES.md`：Shizuku API 改 MIT；新增 KernelSU 分区说明（kernel GPL-2.0-only / userspace GPL-3.0-or-later）；SamTimer 保持“预编译二进制、无协议、未搬源码”的说明。
4. 接通执行链（READY_NOT_WIRED 缺口）——**已完成（2026-11-25，执行链轮）**：
   Shizuku UserService 胶水（Pro 自己写业务 AIDL `ITokiFontsRootService.aidl` +
   service 类 `TokiFontsRootService`，`UserServiceArgs.tag` 显式设为
   `com.ichiyotsu.tokifontspro.root`，destroy 走 transaction 16777115 自调
   `System.exit()`）+ 载荷加载器（`PayloadLoader`：预检 → staging → libtimer
   → 校验 → 失败回滚；状态机 READY / LOADING / RUNNING / VERIFYING /
   SUCCEEDED / FAILED，`READY_NOT_WIRED` 已删）。binder **只走 provider**
   （`ShizukuProvider` 已注册进 manifest，`:app` 已依赖
   `:third-party:shizuku:provider`）：Sui 要预装 Magisk 模块，未 root 的
   目标机根本没有；之前“现走 Sui”的说法是不准确的——代码里既没注册
   provider 也没调过 `Sui.init()`，`pingBinder()` 只能为 false。
   驱动版本映射**定夺：保持 3.2.5，不接入 `ksud-331-src`**（理由三条，缺一不可）：
   ① 实测 `ksud-331-src` 内嵌的 `.ko` 是零个（编前 `bin/aarch64/` 里只放了
   bootctl/busybox/waitsys，没放任何 `*_kernelsu.ko`），late-load 会直接报
   "No kernel module found"；② UAPI 5 的 daemon 对 DZJ3 的 UAPI 2 时代 `.ko`
   会触发版本不匹配拒绝；③ 3.3.0 在 6.1.162 上让 system_server 崩溃循环
   的前车之鉴在前，没有真机证据不换代。`KSU_PACKAGE_NAME` **不改**，
   保持上游默认 `me.weishu.kernelsu`：预编译 `umh_action.sh` 写死
   `late-load --package-name me.weishu.kernelsu`，预编译 `.ko` 也是按官方
   Manager 身份绑定的，改包名要重编 `.ko`，需三星内核源码，不在本轮范围。
   ksuinit 已接进同一 cargo 步骤（`build-ksud.sh` 先编 ksuinit 再编 ksud，
   编进 `bin/aarch64/` 嵌进 ksud；只影响 boot-patch，不影响 late-load）。
   保守规则全部保留：预检、一次开机只跑一次、失败后不重启不重试、任一步失败
   自动取消并清理 staging、装后校验失败自动回滚、模块绝不写 /system、fonts.xml 双备份。
   校验不碰 `/data/adb`（`--ephemeral` 明确跳过 ksud 安装与 `/data/adb`），
   只验 `su -c id` 出 uid=0 + /proc/modules 里有 kernelsu。仍需真机验证，
   见 §6。
5. 验证：`testDebugUnitTest --offline` 全绿（除已知红的 ScreenshotTest D9）+ `assembleDebug` 成功。不推、不打 tag、不发版。
   —— 执行链轮验证结果（2026-11-25）：`testDebugUnitTest` 188 项，187 通过，
   唯一失败是已知红的 ScreenshotTest D9（`UnsupportedOperationException`，
   与本轮无关）；新测试 `PayloadLoaderTest` 14 项 + `UserServiceGlueTest`
   4 项全部通过。`assembleDebug` 成功，APK 69M；已拆包核对：
   `ShizukuProvider` 已注册进 manifest（authorities
   `com.ichiyotsu.tokifontspro.shizuku`，exported，`INTERACT_ACROSS_USERS_FULL`），
   `TokiFontsRootService` / `ITokiFontsRootService` 在 dex 里，
   `ksud-32525` 在 assets 里（`ksud-331-src` 5,370,600 B、含 ksuinit，0.1.1 起不再随包：
   `build-ksud.sh` 改 stage 到 `third-party/kernelsu/out/`，可随时重编——它不被运行链引用，
   接线又被三星内核源码堵住，包里留着只是死重）；
   三份预编译 ksud + libtimer / uhexec / 两个 umh 脚本的 sha256 与本轮开始
   时一致，未动。真机全流程仍待 Ichiyotsu 的 S24+ 实测。

## 6. 待核实清单（实施轮或人工）

- `JamXisu` 是谁（`ksud-32525` 供应方）；`ksud-331-kdp` 是否确用 RMG `samsung-kdp-rkp-defex` 补丁；`ksud-32601` commit 数 2601；`aaba992` 树内精确 AGP/compileSdk。
- 已解决（2026-11-25）：Pro 是否已依赖 provider——**已依赖**：`:app` 依赖 `:third-party:shizuku:provider` 源码模块，manifest 已注册 `ShizukuProvider`，binder 只走 provider；Rust stable 下限——本机实装 1.100.0，edition 2024（需 ≥1.85）编 ksud + ksuinit 均已通过。
