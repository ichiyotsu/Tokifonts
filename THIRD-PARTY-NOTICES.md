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
- **KernelSU**（https://github.com/tiann/KernelSU）：GPL-2.0。
  `ksud` 驱动产物（3.2.5 / 3.3.0 / 3.3.1 KDP）按 GPL-2.0 计；
  内嵌时须随包带上 GPL-2.0 文本（上游 App 的 assets 里已有
  `gpl-2.0.txt`，内嵌时一并带走）。
- **Shizuku**（https://github.com/RikkaApps/Shizuku）：Apache 2.0。
  Shizuku 是用户另行安装的独立应用，不由本 App 打包分发；
  此处只记录授权依赖关系。
- **SamTimer**（https://github.com/RiosWesley/SamTimer）：
  98260 UAF 引擎的参考实现。**已核对（2026-11-24）：该仓库根目录没有
  LICENSE 文件**（列根目录只有 `.gitignore`、`README.md`、`app/`、
  `build.gradle.kts` 等），许可证仍未核对清——在核对清之前不内嵌它的
  任何代码（`timer_samtimer.c` 源码未取、未放进树）；本轮内嵌的
  `samtimer/*` 二进制全部来自 root-my-s24 公开仓库（GPL-3.0，
  许可证文本已随包放在 `android/app/src/main/assets/embed/licenses/`）。
  另见 `docs/embed-inventory.md` §D 缺口 2。
