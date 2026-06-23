# Sirat (صراط) — Agent Guide

## Tech stack
- Kotlin 2.1.0 + Jetpack Compose + Material 3 — no XML layouts, no Views
- Gradle Kotlin DSL (`build.gradle.kts`), version catalog at `gradle/libs.versions.toml`
- AGP 9.0.0, Gradle 9.5.1, JDK 17, compileSdk/targetSdk 35, minSdk 26
- MultiDex enabled

## Modules
| Module | Purpose |
|--------|---------|
| `:app` | Main app, all features |
| `:appintro` | Onboarding screens (library module) |
| `:hidden-api` | Hidden API stubs — 30 Java files (AIDL-like), compileOnly dep in `:app`. Intentional — required by `dev.rikka.tools.refine`. |
| `:patternlock` | Disabled — commented out in `settings.gradle.kts` |

## Entrypoints
- **Application**: `AppLockApplication.kt` (initializes repos, HiddenApiBypass, Sui)
- **Activity**: `MainActivity.kt` — `NavigationManager` decides start destination
- **Nav host**: `AppNavHost` in `core/navigation/`

## Build & run
```bash
./gradlew assemble                    # CI: debug + release APKs
./gradlew :app:assembleDebug          # debug APK only
./gradlew :app:assembleRelease        # minified + shrunk release APK
```
No lint, typecheck, or test tasks are configured beyond defaults. Open project in Android Studio.

## Architecture
- **Feature-based packaging** under `com.atyafcode.sirat/features/`
- Shared code in `core/`, shared UI in `ui/components/` and `ui/theme/`
- Data layer in `data/repository/` — `AppLockRepository`, `PreferencesRepository`, `LockedAppsRepository`, `BehaviorRepository`, `PlanRepository`
- Navigation: Jetpack Navigation Compose via `Screen` sealed class + `NavigationManager`
- "Supervised" mode throughout (`SupervisedLockManager`, `SupervisedLockOverlay`, `SupervisedSetupScreen`, etc.)

## App-locking services (heart of the app)
Three detection mechanisms in `services/`:
1. `AppLockAccessibilityService` — primary, uses AccessibilityService
2. `ShizukuAppLockService` — faster/lower power via Shizuku IPC
3. `UsageLockService` — fallback via Usage Stats permission
- All coordinated by `AppLockManager.kt`
- **Any change to these services must not degrade battery or performance**

## Key permissions & features
- `SYSTEM_ALERT_WINDOW` — lock screen overlay (`features/lockscreen/`)
- `Device Admin` — anti-uninstall (`features/admin/`, `features/antiuninstall/`)
- `BIND_ACCESSIBILITY_SERVICE` — app detection
- `PACKAGE_USAGE_STATS` — fallback detection
- Shizuku provider registered in manifest (`rikka.shizuku.ShizukuProvider`)
- Camera + ML Kit (barcode scanning, face detection)
- MediaPipe GenAI + Retrofit (cloud AI) for AI features (`features/aisettings/`, `features/planbuilder/`, `features/chat/`)
- Hidden API bypass via `hiddenapibypass` + `dev.rikka.tools.refine` at app startup
- Sui (root) initialized at app startup

## Lock methods
- Biometric (fingerprint), Pattern, PIN — configured via `features/setpassword/`
- Lock screen shown as overlay via `PasswordOverlayActivity` (singleInstance, noHistory, translucent theme)

## Localization
- Bilingual (Arabic / English), locale applied in `attachBaseContext` via `LocaleUtils`
- `supportsRtl="true"` in manifest

## Release build
- `isMinifyEnabled = true`, `isShrinkResources = true`
- ProGuard rules: `proguard-rules.pro`
- Managed signing via `signingConfigs.getByName("debug")` for release (customize for production)

## Notable quirks
- `:patternlock` module exists on disk but is excluded from the build
- `includeInApk = false`, `includeInBundle = false` — dependency info stripped
- No test suite beyond the default instrumentation runner
- `BootReceiver` handles `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `USER_UNLOCKED`, `USER_PRESENT`
- `AppLockConstants.kt` holds shared constant values for service coordination
- Custom vector icons live in `ui/icons/` as Kotlin composables
