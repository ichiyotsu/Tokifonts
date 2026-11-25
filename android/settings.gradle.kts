pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "tokifonts"
include(":app")
// Vendored Shizuku API source (third-party/shizuku, MIT, Shizuku-API @ aaba9928
// = Maven 13.1.5). Dependency chain: provider -> api -> shared -> aidl.
// The modules live at the repo root (../third-party), not under android/,
// so each project's directory is remapped below.
// provider is a dependency of :app (the Shizuku binder channel) and is
// registered as ShizukuProvider in the manifest; the execution chain uses
// the provider path, not Sui — see UserServiceGlue.kt.
include(":third-party:shizuku:aidl")
include(":third-party:shizuku:shared")
include(":third-party:shizuku:api")
include(":third-party:shizuku:provider")
project(":third-party").projectDir = File(rootDir, "../third-party")
project(":third-party:shizuku").projectDir = File(rootDir, "../third-party/shizuku")
project(":third-party:shizuku:aidl").projectDir = File(rootDir, "../third-party/shizuku/aidl")
project(":third-party:shizuku:shared").projectDir = File(rootDir, "../third-party/shizuku/shared")
project(":third-party:shizuku:api").projectDir = File(rootDir, "../third-party/shizuku/api")
project(":third-party:shizuku:provider").projectDir = File(rootDir, "../third-party/shizuku/provider")
