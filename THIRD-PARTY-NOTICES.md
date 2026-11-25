# Third-Party Notices

本 App（TokiFonts Pro 轨）整体按 **GNU General Public License v3.0**
发布，见树根目录的 `LICENSE`。

本文件列出本 App 直接使用 / 内嵌的上游第三方组件及其许可证。
许可证原文：GPL-3.0 见 `LICENSE`；Apache 2.0 全文见
https://www.apache.org/licenses/LICENSE-2.0 。

## Apache License 2.0 的部分

### Root-My-Galaxy

- 仓库：https://github.com/BuSung-dev/Root-My-Galaxy
- 许可证：Apache License 2.0
- 用途：免解锁 root 参考工程；其载荷 feed（见
  https://github.com/BuSung-dev/Root-My-Galaxy-Payloads）的 20 条
  GhostLock（CVE-2026-43499）载荷经 `docs/firmware-support.json`
  合并进本 App 的设备支持表（`tool=root-my-galaxy` 的条目），
  状态一律记为“移植”，未经本 App 真机实测。
- 该项目按 Apache 2.0 发布；其代码与载荷在本 App 里的使用遵守
  Apache 2.0 的条款（保留版权声明与许可证声明）。

### Root-My-Galaxy-Payloads

- 仓库：https://github.com/BuSung-dev/Root-My-Galaxy-Payloads
- 许可证：Apache License 2.0
- 用途：按机型 + 内核版本分发的 exploit 载荷（`cve-2026-43499-app.so`）
  与随载荷提供的 KernelSU 工件（`ksud-*-kdp`）；feed 为
  `support/targets-v3.json`（schema 3），由
  `.github/workflows/update-device-list.yml` 每天拉取、
  重新生成 `docs/firmware-support.json`。
- 该仓库按 Apache 2.0 发布；载荷直链与字节数在 feed 条目里照抄，
  不改写、不重新分发二进制本身（内嵌方案见 `docs/embed-design.md`）。

## 与 GPL-3.0 的关系

- 本 App（Pro 轨）整体按 GPL-3.0 发布：任何人分发本 App（含内嵌的
  exploit 载荷与驱动产物）时，必须同时遵守 GPL-3.0（提供源码、
  同许可证发布修改版）与上述 Apache 2.0 组件的条款。
- Apache 2.0 的组件可以与 GPL-3.0 的作品组合分发（Apache 2.0 与
  GPL-3.0 兼容），但 Apache 2.0 的部分本身仍按 Apache 2.0 计，
  不能把它的许可证声明删掉或改写成 GPL-3.0。

## 其他组件（供内嵌时核对，不随本次 feed 发布）

- **root-my-s24**（https://github.com/NanoTurtle1145/root-my-s24）：
  GPL-3.0。Pro 轨内嵌 exploit 方案参考 / 复用其引擎与载荷
  （见 `docs/embed-design.md`）；其 `LICENSE` 即本树根 `LICENSE`
  的来源。
- **KernelSU**（https://github.com/tiann/KernelSU）：许可证按目录分区——
  `kernel/`（内核 LKM 源码）为 **GPL-2.0-only**，其余部分（ksud、ksuinit、
  manager、uapi）为 **GPL-3.0-or-later**（见 `third-party/kernelsu/` 里
  随附的根 `LICENSE`（GPLv3 全文）与 `kernel/LICENSE`（GPLv2 全文），
  以及 `docs/README.md` §License 的分区声明；之前这里只写 GPL-2.0 是
  不完整的，已纠正）。ksud 守护进程自本轮起从源码编（pin v3.3.1，
  commit `0c0fdb96`，UAPI 5；构建见 `third-party/kernelsu/build-ksud.sh`，
  产物不进包（`build-ksud.sh` 编出后放在 `third-party/kernelsu/out/`，可随时重编），
  未替换任何预编译驱动）；
  执行链轮（2026-11-25）定夺：**`ksud-331-src` 不接入运行链**，映射保持
  3.2.5（见 `docs/source-integration.md` §5 第 4 项）——实测它内嵌的
  `.ko` 是零个（编前 `bin/aarch64/` 里没放任何 `*_kernelsu.ko`），
  late-load 会直接报 "No kernel module found"；且 UAPI 5 的 daemon 对
  DZJ3 的 UAPI 2 时代 `.ko` 会触发版本不匹配拒绝。`KSU_PACKAGE_NAME`
  保持上游默认 `me.weishu.kernelsu`（预编译 `umh_action.sh` 写死
  `--package-name me.weishu.kernelsu`）。ksuinit 自执行链轮起由
  `build-ksud.sh` 同一步编出并嵌进 ksud（只影响 boot-patch，不影响
  late-load）；现 `ksud-331-src`（`third-party/kernelsu/out/` 里的构建产物）为 5,370,600 B，
  sha256 `e8fbdea5…3868a63`（2026-11-25 重编，含 ksuinit）；
  各固件的 `.ko` 仍为预编译（编 `.ko` 需目标内核源码 + 三星补丁，App
  构建里没有）。内嵌的 ksud 驱动产物随包带上对应协议文本（上游 App
  的 assets 里已有 `gpl-2.0.txt`，内嵌时一并带走；GPLv3 文本见树根
  `LICENSE`）。
- **Shizuku**（https://github.com/RikkaApps/Shizuku-API）：**MIT**
  （Copyright (c) 2021 RikkaW；之前这里写 Apache 2.0 是错的，已纠正——
  Apache 2.0 的是主仓 `RikkaApps/Shizuku` 的 manager/server，本 App
  不打包它们）。API 源码已 vendored 进 `third-party/shizuku/`
  （pin commit `aaba9928`，即 Maven 13.1.5；MIT 全文见该目录 `LICENSE`）。
  执行链轮（2026-11-25）起 `:app` 也依赖 `:third-party:shizuku:provider`
  源码模块，manifest 注册了 `ShizukuProvider`，binder 只走 provider
  通道，不走 Sui（Sui 要预装 Magisk 模块，未 root 的目标机没有）。
  Shizuku manager 本身仍是用户另行安装的独立应用，不由本 App 打包分发；
  此处只记录授权依赖关系。
- **SamTimer**（https://github.com/RiosWesley/SamTimer）：
  98260 UAF 引擎的参考实现。**已核对（2026-11-25，见侦察报告）：
  该仓库全树没有任何有效开源协议**（根目录、各源码目录均无 LICENSE /
  COPYING / NOTICE；README 无协议章节；全部源码文件头无协议头、无 SPDX；
  `timer.c:17` 的一句 "Adapted from woshigenm/CVE-2026-98260 (MIT)" 只
  覆盖 woshigenm 自己的文件，不能授权 SamTimer 的 `timer.c`，且 SamTimer
  未履行 MIT 的署名条件）。因此 SamTimer 的任何源码（`timer.c`、Kotlin
  应用代码、`umh_action.sh`）一律不搬、不编；本轮内嵌的 `samtimer/*`
  二进制全部来自 root-my-s24 公开仓库（GPL-3.0，许可证文本已随包放在
  `android/app/src/main/assets/embed/licenses/`），保持预编译原样，
  不扩大使用范围。另见 `docs/embed-inventory.md` §D 缺口 2。
