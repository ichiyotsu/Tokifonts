# KernelSU — vendored source (Pro track)

## Source

- Upstream: https://github.com/tiann/KernelSU
- Pinned tag: **`v3.3.1`**, commit `0c0fdb960178fa12d80d6cbcfdcb6ad8be6a865d`
  (2026-10-12). UAPI version 5 (`uapi/supercall.h`: `KERNEL_SU_UAPI_VERSION = 5`).
  Do NOT pin main: at recon time main's UAPI was already 6.
- License (split — read both):
  - `kernel/` = **GPL-2.0-only** (`kernel/LICENSE`, GPLv2 full text).
  - Everything else (ksud, ksuinit, manager, uapi) = **GPL-3.0-or-later**
    (root `LICENSE`, GPLv3 full text).
  - Source: `docs/README.md` §License (vendored copy in `docs/`).
  - Pro itself is GPL-3.0, so this is compatible; distributing an APK
    containing ksud / a `.ko` must ship the matching source + license text.

## What was vendored (per `kernelsu.md` §6.2)

```
LICENSE                    # GPLv3 (all except kernel/)
kernel/LICENSE             # GPLv2 (kernel/ only)
Cargo.toml / Cargo.lock    # workspace (edition 2024, members ksud + ksuinit)
.cargo/config.example.toml # NDK cross config template
scripts/setup_cargo_config.py
.github/scripts/setup-rust-build.sh
.github/workflows/ksud.yml / ksuinit.yml   # upstream CI, for reference
userspace/ksud/            # ksud source (src/, Cargo.toml, build.rs)
userspace/ksuinit/         # ksuinit source (src/, Cargo.toml, build.rs)
uapi/                      # ksu.h, supercall.h, app_profile.h, ...
kernel/                    # LKM source + Kbuild/Kconfig/Makefile/setup.sh
                           # (SOURCE RETENTION ONLY — never built by the App CI)
docs/README.md             # license split statement (§License)
```

Plain copy, **not a git submodule**: Pro's push pipeline is snapshot +
push-files and does not support gitlinks. No `.git` is vendored.
All files above are byte-identical to tag `v3.3.1` except the one patch
described below (`userspace/ksud/build.rs`).

## Version patch (the one intentional change)

Upstream `userspace/ksud/build.rs` runs `git rev-list --count HEAD` and
`git describe --tags --always` at compile time to produce
`VERSION_CODE = 30000 + commit count` and `VERSION_NAME`. A plain copy has
no `.git`, so it would degrade to `(0, "0.0.0")` — and v3.3.1's version-match
check would treat the daemon as mismatched.

Patch (in `userspace/ksud/build.rs`, marked "TokiFontsPro vendor patch"):
if the environment provides **both** `KSU_VERSION_CODE` and
`KSU_VERSION_NAME`, they win over git; otherwise upstream git behaviour is
unchanged. `build-ksud.sh` sets them to the pinned tag:

- `KSU_VERSION_CODE=32680` (30000 + 2680 commits at v3.3.1)
- `KSU_VERSION_NAME=3.3.1`

Resulting binary reports `3.3.1 (uapi: 5)` — verified by `strings` in
`build-ksud.sh` (the aarch64 Android binary cannot be executed on the
x86_64 build host). `KSU_PACKAGE_NAME` is intentionally left at the
upstream default `me.weishu.kernelsu`; whether Pro should build with its
own package name is a decision for the execution-chain round (the kernel
side binds Manager identity; see "Still prebuilt" and "Next round").

## How ksud is built (Gradle does NOT build it)

Upstream's Manager build also builds ksud first with cargo and only then
runs Gradle. Same here:

```
./third-party/kernelsu/build-ksud.sh
```

- Rust stable (≥ 1.85, edition 2024) + NDK **r29**, API **26**, target
  `aarch64-linux-android`, `--locked` (git deps pinned by `Cargo.lock`).
- Output is staged as `third-party/kernelsu/out/ksud-331-src` — outside the
  app package (the binary is unreferenced by the run path and its wiring is
  deferred, so it stays out of the APK; rebuild any time with this script).
  It never overwrites the prebuilt `ksud-32525` / `ksud-32601` /
  `ksud-331-kdp`. Whether the app loads the source-built one, and the
  driver-version mapping, is decided by the execution-chain round.
- CI: `.github/workflows/android-release.yml` installs NDK r29 + Rust and
  runs this script before `./gradlew assembleRelease`.

ksuinit is built by this script since 2026-11-25 (execution-chain round):
built first with the upstream `ksuinit.yml` RUSTFLAGS (NDK `libclang_rt`
builtins + 16K page size), copied into `userspace/ksud/bin/aarch64/`, and
embedded into ksud by `rust-embed` (the script verifies the embedded
marker string). Current build output (`third-party/kernelsu/out/ksud-331-src`): 5,370,600 B, sha256
`e8fbdea50ee54c3a13a9b7d6afac7c378862c8d4c1974356dde2ce7db3868a63`
(2026-11-25 rebuild, with ksuinit embedded). ksuinit only matters for
boot-patch flows, never for the 98260 late-load path.

## Execution-chain decision (2026-11-25)

`ksud-331-src` is **not wired into the run path**: the driver mapping stays
at 3.2.5 (`ksud-32525`) for the 98260 chain. Verified: the source-built
binary embeds **zero** `.ko` files (no `*_kernelsu.ko` was placed in
`bin/aarch64/` before building — only bootctl/busybox/waitsys/ksuinit),
so `late-load` would fail with "No kernel module found"; its UAPI 5 daemon
would also mismatch DZJ3's UAPI 2-era `.ko`. `KSU_PACKAGE_NAME` stays at
the upstream default `me.weishu.kernelsu` (the prebuilt `umh_action.sh`
hardcodes `--package-name me.weishu.kernelsu`). Full reasoning:
`docs/source-integration.md` §5 item 4, `PayloadLoader.kt` header.

## Prebuilt blobs that came along (do NOT pretend zero-blob)

`userspace/ksud/bin/<arch>/` already contains prebuilt binaries committed
upstream, copied verbatim into this tree:

- `bin/aarch64/`: `bootctl`, `busybox`, `waitsys`
- `bin/x86_64/`: `busybox`, `waitsys`
- `bin/riscv64/`: `busybox`, `waitsys`

They are embedded into the built ksud by `rust-embed` (`src/assets.rs`)
alongside any `.ko` / ksuinit placed in `bin/<arch>/` before building.
Building ksud from source does **not** rebuild or replace them. Replacing
them (self-build or substitution) is a separate task, not claimed here.
(`bin/aarch64/ksuinit` is the exception: it is built from source by
`build-ksud.sh` on every run, not an upstream-committed blob.)

## Still prebuilt — and why

- **Kernel LKM `.ko` for every firmware** (DZJ3 S9210/S9260/S9280 and all
  others): building a `.ko` needs the target kernel source tree (KDIR) +
  Samsung KDP/RKP/Defex patches; neither is in this repo or in the App CI.
  One firmware = one build; not interchangeable.
- **SamTimer / DZJ3 exploit binaries** (`libtimer`, `uhexec`,
  `umh_action.sh`): no license — SamTimer source is never vendored
  (see `THIRD-PARTY-NOTICES.md`). DZJ3's prebuilt `libtimer` is untouched.
- **`ksud-32525` (`3.2.5-JamXisu`)**: `-JamXisu` exists nowhere upstream;
  pure v3.2.5 source cannot reproduce it. Ask its supplier before treating
  it as v3.2.5.
- **`ksud-331-kdp`**: `-kdp` is Root-My-Galaxy's name for the Samsung-KDP
  late-load build; pure upstream v3.3.1 source reproduces only the generic
  daemon half (the Samsung-patched `.ko` it embeds is not in this repo).

## Driver-version mapping — decided by the execution-chain round (2026-11-25)

Current runtime rule maps kernel 6.1.145 / 6.1.162 to ksud 3.2.5; the
source-built daemon is v3.3.1 (UAPI 5). v3.3.1 added a version-match check,
so ksud / `.ko` / UAPI must be the same generation. **Decision: keep 3.2.5,
do NOT wire in `ksud-331-src`** — it embeds zero `.ko` files (verified:
no `*_kernelsu.ko` string in the staged binary), so `late-load` would fail
with "No kernel module found", and its UAPI 5 daemon would mismatch DZJ3's
UAPI 2-era `.ko`. `KSU_PACKAGE_NAME` stays at the upstream default
`me.weishu.kernelsu`. Full reasoning: `docs/source-integration.md` §5
item 4, `PayloadLoader.kt` header.

## Re-verify against upstream

`git clone --depth 1 --branch v3.3.1 https://github.com/tiann/KernelSU.git`,
then `diff -r` this directory against the clone: the only expected diff is
`userspace/ksud/build.rs` (the version patch above).
