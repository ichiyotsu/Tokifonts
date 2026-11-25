# Shizuku API — vendored source (Pro track)

## Source

- Upstream: https://github.com/RikkaApps/Shizuku-API
- Pinned commit: `aaba9928f50f9df62c5e429318de14a0ed473480`
  ("Fix #54", 2023-09-21). There is no tag for 13.x; this commit is
  exactly Maven Central `dev.rikka.shizuku:{aidl,shared,api,provider}:13.1.5`
  (verified against `manifest.gradle` patch 4→5 and the Maven publish date
  2023-09-21). Do NOT pin HEAD: 13.1.6 was never published to Maven and
  HEAD requires AGP 9.5.0 / compileSdk 37 / Java 21-era tooling jumps.
- License: **MIT** (`LICENSE` in this directory, Copyright (c) 2021 RikkaW).
  This corrects `THIRD-PARTY-NOTICES.md`, which previously said Apache 2.0.
  The only hard MIT condition is keeping the copyright + permission notice.
  The Apache 2.0 + trademark rules in the main `RikkaApps/Shizuku` repo
  (don't use `Shizuku` as app name, don't use `moe.shizuku.privileged.api`
  as applicationId, don't use the manager launcher icons) still apply as
  background rules — this tree does none of those.

## What was vendored

```
aidl/       # moe.shizuku.server.* AIDL (IShizukuService etc.)
shared/     # ShizukuApiConstants (SERVER_VERSION=13, SERVER_PATCH_VERSION=5)
api/        # Shizuku / ShizukuBinderWrapper / Sui (pure Java)
provider/   # ShizukuProvider (ContentProvider binder channel)
LICENSE     # MIT, verbatim from upstream
```

Dependency chain: `provider → api → shared → aidl`
(+ `androidx.annotation:annotation:1.3.0`, the only external dependency).
Namespaces kept verbatim (`rikka.shizuku.{aidl,shared,api,provider}`).

## What was NOT vendored (deliberately)

- `server-shared/`, `rish/`, `demo/`, `demo-hidden-api-stub/` — server-side /
  interactive shell / demo, not client code.
- The whole main repo `RikkaApps/Shizuku` (`server/`, `manager/` incl. its
  JNI starter `libshizuku.so`, `starter/`, `shell/`, `common/`) — the
  privileged server belongs to the Shizuku manager app installed on the
  user's phone; building it into Pro would run as Pro's own UID with zero
  privilege.
- Upstream root `build.gradle` (maven-publish + signing, OSSRH deploy) and
  `manifest.gradle` version logic — release-publishing only, not needed here.
- Upstream root `settings.gradle` bits: `FAIL_ON_PROJECT_REPOS` and the
  version catalog (`hidden-api` / `refine`) — only used by `server-shared` /
  `rish` / `demo`, which are not vendored.

## Changes made to upstream files (build glue only)

Upstream sources under `src/` are **byte-identical** to commit `aaba9928`
(verified with `diff -r`); `consumer-rules.pro` / `proguard-rules.pro`
likewise. Only the four `build.gradle` files were adapted:

1. `ext { publishLibrary = true; POM_NAME / POM_DESCRIPTION }` removed
   (it only fed the maven-publish POM generation).
2. `manifest.gradle` variables inlined and aligned to this tree
   (AGP 9.5.0, Java 17): `compileSdk 33 → 37`, `minSdk 23 → 36`
   (api's debug `minSdk 24 → 36`; the app's minSdk is 36 anyway),
   `sourceCompatibility/targetCompatibility 1.7/1.8 → 17`.
   Upstream at `aaba992` was AGP 8.0.2 / compileSdk 33 / Java 1.7–1.8.
3. `project(':aidl')` / `project(':shared')` / `project(':api')` re-pointed
   to this tree's nested paths (`:third-party:shizuku:aidl` etc.).
4. `ShizukuApiConstants.SERVER_PATCH_VERSION` (= 5) was **not** touched —
   it is a protocol constant, not a build version.

## Note for the next round (execution chain) — DONE 2026-11-25

- Binder path decided: **provider, not Sui** (see `UserServiceGlue.kt`):
  Sui needs a pre-installed Magisk module, which an unrooted target phone
  does not have; the provider channel (`ShizukuProvider`, now registered in
  the manifest, `:app` depends on `:third-party:shizuku:provider`) is the
  Shizuku-manager channel. Before this round neither path was actually
  wired (no provider registered, `Sui.init()` never called), so
  `pingBinder()` could only return false.
- UserService glue written: Pro's own business AIDL
  (`app/src/main/aidl/com/ichiyotsu/tokifonts/ITokiFontsRootService.aidl`)
  + `TokiFontsRootService` (extends the AIDL Stub, `Context` constructor
  supported, destroy via transaction 16777115 + `System.exit()`),
  `UserServiceArgs.tag` explicitly set to `com.ichiyotsu.tokifontspro.root`
  (stable under R8), `daemon(false)`, version 1.
- Loader written: `PayloadLoader.kt` (precheck → staging → libtimer →
  verify → rollback on failure) + `UserServiceGlue.kt` (connector +
  `UserServiceExecutor`). Driver mapping kept at 3.2.5; `ksud-331-src`
  not wired (embeds no `.ko`); `KSU_PACKAGE_NAME` stays upstream default.
  See `docs/source-integration.md` §5 item 4 for the full decisions.
- To re-verify against upstream: `git clone https://github.com/RikkaApps/Shizuku-API.git`,
  `git checkout aaba9928f50f9df62c5e429318de14a0ed473480`, then
  `diff -r` each module's `src/` and `*-rules.pro` against this directory.
