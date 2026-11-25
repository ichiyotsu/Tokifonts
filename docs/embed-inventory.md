# 内嵌二进制清单（Pro 轨，2026-11-24 拉取）

> 用途：后续推二进制时靠这份清单逐个核对 size + SHA-256。
> 全部文件均从上游公开地址用 curl 拉取；取不到的列在“缺口”一节，绝不编造。
> 拉取日期 2026-11-24；上游版本：root-my-s24 README v3.7.0-alpha.1、
> Root-My-Galaxy-Payloads targets-v3.json schema 3、Shizuku API 13.1.5。

## A. 已内嵌进 APK（`android/app/src/main/assets/embed/`，共 25M）

root-my-s24 公开仓库 `main` 分支 `app/src/main/assets/` 原样搬运：

| 树内路径 | 大小（字节） | SHA-256 |
|---|---|---|
| `embed/cve-2026-43499` | 104128 | `62d50ee443a994c55230da6ff5ee6793b9684b0d7502d2cd76c4cd7cf93c78d2` |
| `embed/cve-2026-43499-root` | 27200 | `ccde5adabc4dac1325069ccfe61f888e853ee56127cdcdce60ef92e6b9e9f5ab` |
| `embed/samtimer/libtimer` | 2215512 | `813e2875aa82e4954f93b6c4ec865b1096c6304643180f2a8427254e00f34bd0` |
| `embed/samtimer/uhexec` | 2219832 | `0a9db96c219b19f758dbb62b4f036a3a0a6a63eb69545a75555a7d4d285c5be0` |
| `embed/samtimer/umh_action.sh` | 1174 | `1963f61ae60524560379530a67a06c7372a144ac01f8addabe8a67b06fb843dd` |
| `embed/samtimer/umh_action-stock.sh` | 2002 | `c8f28c3bb7cf07344cfbe6f27a30ed203e9d308800795f365f39dd1e646d1021` |
| `embed/samtimer/ksud-32525` | 4879856 | `1b92611de62035263f129636270deb9002de42e1d2329460342856243975015b` |
| `embed/samtimer/ksud-32601` | 6028400 | `6f618248e919f0a1695812056eefe711c01957dfa942decb796e3fa904ddb5de` |
| `embed/samtimer/ksud-331-kdp` | 5164448 | `dba3c3625ef3935012d0375278763a9c58668fb263b7e8c8bb3daa3a531f511a` |
| `embed/samtimer/targets/S9280ZCS6DZJ3/libtimer` | 2228648 | `8989511991779fa0eefa68bcde0373ff36737b228a617e9e2c0851edf5386539` |
| `embed/samtimer/targets/S721BXXUEEZIF/libtimer` | 2337456 | `1f15388af1a98a7b8ae41b2228eee31133e228ee06693f2c03753fccf5195153` |
| `embed/licenses/gpl-2.0.txt` | 见文件 | `edaef632cbb643e4e7a221717a6c441a4c1a7c918e6e4d56debc3d8739b233f6` |
| `embed/licenses/gpl-3.0.txt` | 见文件 | `3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986` |
| `embed/licenses/SHIZUKU-APACHE-2.0.txt` | 见文件 | `58d1e17ffe5109a7ae296caafcadfdbe6a7d176f0bc4ab01e12a689b0499d8bd` |

交叉核对：`samtimer/ksud-32525` 与 RMG 的 `ksud-e3q-S9280ZCS6DZF2-kdp`
SHA-256 完全一致（`1b92611d…`），两者是同一份 KernelSU 3.2.5 KDP 产物。

## B. RMG 载荷暂存（`docs/embed-inventory/rmg/`，不进 APK，共 7.0M）

Root-My-Galaxy-Payloads 的 20 条 `.so` 全部拉到并逐个核对：实测大小与
feed 里的 `size` 逐条一致。**不进 APK**：RMG 的 `.so` 能否直接塞进
root-my-s24 的 GhostLock 加载链路尚未核实（见 `docs/embed-design.md`
待核实项 2），核实前只做“推荐去装 Root-My-Galaxy”，不由本 App 直接跑。
`ksud-*` 只下了用户本机那条（S9280 DZF2）；其余 19 份只记 feed 的
URL + size，不进树（19 × 约 5MB，全下约 100MB，APK 装不下也没有必要）。

| payloadId | .so 大小 | .so SHA-256 | 随附 ksud（feed 值，未下载） |
|---|---|---|---|
| `galaxy-s25-series-2026-06-07` | 104128 | `6311e5ae1a381fd96b8a989e82525521ed9b00cff4e0d3020d3904eb5716587c` | `ksud-s25u-kdp`，6407096 |
| `pa2q-S9360ZCSCCZG1` | 125904 | `758dee1f3c1381d0125fa3576f0fd372ddb7a848e14f5a7d68bf11463a643a58` | `ksud-s25u-kdp`，6407096 |
| `psq-S9370ZCS9CZG1` | 125264 | `209d7feb91fc46bfec6f5ebec1f0caca47ee033c9d529e6eaf9c74bc840dd210` | `ksud-s25u-kdp`，6407096 |
| `q7q-F966USQU9BZDN` | 111216 | `dcb20ca0186ab94e897440e478252361e6999fa20178b2c4e2fcefa6bea4d515` | `ksud-s25u-kdp`，6407096 |
| `q7q-F966USQSBBZG3` | 126264 | `d809061172eb825a43571eaeb10d8a898d32db6f28a1f2fb90bae6b318a8068f` | `ksud-s25u-kdp`，6407096 |
| `q7q-F966BXXSBBZG3` | 125872 | `a342474dd5a3b137e645215e4cefb28f2c3ad09601d469a602b1af91b7e7b9a5` | `ksud-s25u-kdp`，6407096 |
| `e3q-S928USQS6DZF2` | 104128 | `b2931d8980f969b5a0cb05bd67f6804f445ad4a4c867a7b4c4081c2ffac5b36a` | `ksud-e3q-S928USQS6DZF2-kdp`，4998352 |
| `e3q-S928BXXS6DZF2` | 104128 | `a49b378d654c7e637697a701c3c4c5fd02d22b9b30a7069c03e64ec5844af206` | `ksud-e3q-S928BXXS6DZF2-kdp`，4748232 |
| `e2s-S926BXXUEDZDR` | 104128 | `b1fe6dcc2e4d5cbfc13679f2086835c0a2ab2be5624f122b23b0c30954670552` | `ksud-e2s-S926BXXUEDZDR-kdp`，4780056 |
| `e1s-S921BXXSFDZE1` | 104128 | `cde4e9ca1f235f11940513c897de23c97f89f70c7ef0104d071ff1acbbcf1776` | `ksud-e1s-S921BXXSFDZE1-kdp`，4920520 |
| `essi-A566EXXSCCZG6` | 104128 | `f28050c5f291386ec539aac668b218d88ab8df43ec159978b2a7b71a329b0bd5` | `ksud-A566EXXSCCZG6-kdp`，4765336 |
| `a36xq-A366WVLS3AYG1` | 128688 | `481e3425d5038b7b51820d626783d5b1afb50c3358075582f14c94e5d7b52eec` | `ksud-A366WVLS3AYG1-kdp`，4889120 |
| `dm3q-S918BXXSAFZF5` | 133344 | `3bf7edcbab642c346ac0cb5564ef7406b835eb555b1163ebd498a9860dc6c83f` | `ksud-dm3q-S918BXXSAFZF5-kdp`，4879560 |
| `a53x-A536EXXSNGZG3` | 104128 | `fb9b2e08adb8046f3ae0566cc0b10ae55c5bb72b0d04396a935141e242060fdf` | `ksud-A536EXXSNGZG3-kdp`，4870752 |
| `dm3q-S9180ZHS8FZF5` | 137728 | `55586f8e13e2ef57b33a94a8d36f33d6f01f2881310b1fbe5e764bb06ba78bb2` | `ksud-dm3q-S9180ZHS8FZF5-kdp`，4556352 |
| `e1s-S921NKSSFDZF3` | 104128 | `1256ab0c500cb44ed46bf79dcf7331bf8e23fe1b047dd980e6048a3ae01a4245` | `ksud-e1s-S921NKSSFDZF3-kdp`，4758872 |
| `dm1q-S911U1UES6DYI3` | 130168 | `cc103692427c0ce7fb2a637ad6505ef1739c7036132b221cecf8d1b9a2328dca` | `ksud-dm1q-S911U1UES6DYI3-kdp`，4758792 |
| `r12s-S721BXXSCDZF3` | 104128 | `e493e64f9ebc8c64297a0cf1be3c6b2b82a3d39713d14049b5719b7a8d18111d` | `ksud-r12s-S721BXXSCDZF3-kdp`，6419768 |
| `gts9-X710XXS6EZF1` | 132688 | `caad8a8e65219c16d25af2091625c20fb96e721a39cda04f4f92c87f23bf9543` | `ksud-gts9-X710XXS6EZF1-kdp`，4783256 |
| `e3q-S9280ZCS6DZF2` | 104128 | `e9baa068843b372cf5d1a7131902f58af25931327957df7b39df3b655e89c9bc` | `ksud-e3q-S9280ZCS6DZF2-kdp`，4879856，已下载，SHA-256 `1b92611de62035263f129636270deb9002de42e1d2329460342856243975015b` |

## C. Shizuku API（`android/app/libs/`，本地 AAR，不走 Maven）

构建是 `--offline`，Maven 新依赖拉不到，故把 AAR 下到本地再接：

| 文件 | 大小（字节） | SHA-256 |
|---|---|---|
| `shizuku-api-13.1.5.aar` | 24652 | `4def9bde498ef8626614c2fc5db9af4749c86f16f6c33e3f5658d35e70bab59b` |
| `shizuku-aidl-13.1.5.aar` | 23673 | `33fe7191cdd69fcb66d649264f3b0c47acb2f3d6343afc05b98dbbff6f221963` |
| `shizuku-shared-13.1.5.aar` | 2614 | `4659642c9339be0a26e9c65bb8648f7ad6d8f4a465f557993ccbc78802381635` |

来源：Maven Central `dev.rikka.shizuku:api/aidl/shared:13.1.5`。
api 的唯一传递依赖是 `androidx.annotation:annotation:1.3.0`，本 App 已有，
无需另下。Shizuku 本体仍是用户另行安装的独立应用，不打包。

> 2026-11-25 源码融入轮：上述 3 个 AAR 已删除，改为 vendored 源码模块
> `third-party/shizuku/`（Shizuku-API @ `aaba9928`，即 Maven 13.1.5，
> MIT；另含 `provider/` 模块，暂未被 `:app` 依赖）。上表保留为历史记录
> （AAR 的 SHA-256 是删除前实测值）。`app/libs/` 现为空。

## D. 缺口（取不到 / 没法核实，不编造）

1. **按固件分的 GhostLock 载荷 `cve-2026-43499-<TAG>`（12 个）全部取不到**：
   README 列出的 `cve-2026-43499-S9280ZCS6DZF2`、`…-dze2`、`…-cza1`、
   `…-cza1-chc`、`…-czb2`、`…-byh7`、`…-czg1`、`…-S928BXXS4CZA1`、
   `…-S928BXXU4BYD9`、`…-S928BXXU4AXK5`、`…-W9023ZCSAIZF1`、
   `…-dze2-xaa`（美版，未发布）在公开仓库 `app/src/main/assets/` 里
   都不存在（逐个 raw HEAD 均为 404；该目录实测只有 `cve-2026-43499`
   与 `cve-2026-43499-root` 两个文件）。上游仓库另有
   `tools/` 下的 `split_assets` / `release_flavors` 脚本，推测按固件
   分的载荷是在发版构建时按 flavor 注入、不进公开仓库。**后果**：
   内嵌的 GhostLock 引擎目前只有通用 `cve-2026-43499` 这一份；
   按固件精确选载荷做不到——内嵌一键 root 的预检因此把 GhostLock
   精确行判为“载荷缺失”，只放行 98260 行（targets 载荷已取到）。
   要补：等上游 release 附件或私有构建链给出按 TAG 的文件。
2. **SamTimer 许可证未核对**：`RiosWesley/SamTimer` 仓库根目录实测
   没有 LICENSE 文件（2026-11-24 列根目录：只有 `.gitignore`、
   `README.md`、`app/`、`build.gradle.kts` 等）。在核对清之前，
   **不内嵌 SamTimer 的任何代码**（`timer_samtimer.c` 源码未取、
   未放进树）；内嵌的 `samtimer/*` 二进制全部来自 root-my-s24
   公开仓库（GPL-3.0，许可证文本已随包放在 `embed/licenses/`）。
3. **RMG `.so` 能否塞进 root-my-s24 的 GhostLock 加载链路未核实**
   （`docs/embed-design.md` 待核实项 2）：20 份 `.so` 只暂存、不进 APK。
4. **`samtimer/targets/S721BXXUEEZIF/` 只有 `libtimer`**：已确认该目录
   无 `uhexec` / `ksud` 覆盖（与 DZJ3 行一致），运行时用公共版。
5. **W9023N 与 SM-S721B EZIF 两行**：README 有、App 内表没有，进 App
   表前需人工核对（见 `docs/firmware-support.json` 的 notes）。
6. **RMG 19 份 ksud 只记了 feed 值**：未下载核对 size/SHA-256；
   需要时按上表 URL 逐个拉取校验后再用。
7. **GhostLock 载荷源码位置未确认**（`docs/embed-design.md` 待核实项 1）：
   本轮只内嵌预编译产物，不自己编 C++，与任务要求一致。
