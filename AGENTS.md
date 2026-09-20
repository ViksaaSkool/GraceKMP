# GraceKMP — OpenCode Agent Instructions

## Project identity
Kotlin Multiplatform rewrite of the original Grace Android app (a meal-blessing joke app). Single **Compose Multiplatform** UI shared across Android + iOS, with all business logic in `commonMain`.

## Repo layout
- `composeApp/` — the only Gradle module (KMP + `com.android.application` + Compose). Source sets: `commonMain`, `androidMain`, `iosMain`, `commonTest`, `iosTest`.
- `iosApp/` — Xcode host project (`iosApp.xcodeproj`) wrapping `MainViewControllerKt.MainViewController()` via `UIViewControllerRepresentable`.
- `gradle/libs.versions.toml` — single version catalog (the parity contract for dependency versions).
- `docs/parity/REPORT.md` — every accepted UI/behaviour deviation, numbered #1–#17. **Read this before any UI change.**
- `PLAN.md` — the original migration plan (phases 0–11).

## Build & test
```bash
./gradlew :composeApp:assembleDebug          # Android debug APK
./gradlew :composeApp:testDebugUnitTest       # Android JVM unit tests (expect ~70, 0 failures)
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  ./gradlew :composeApp:iosSimulatorArm64Test # iOS simulator tests (expect ~63)
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```
- iOS requires `DEVELOPER_DIR` pointed at the full Xcode install (not CommandLineTools).
- Release signing reads `local.properties` (gitignored) or env vars — never committed.

## Architecture
- **UDF (State/Action/Effect) + ViewModels** (ADR-2) replaces the original MVP + `Loader` + EventBus.
- **Koin 4.2.2** (ADR-3) replaces Dagger; graph in `di/Modules.kt` (`appModule` + `expect platformModule`).
- **~60-line `Navigator`** reproduces fragment `replace()` + translucent overlay (ADR-5).
- `core/GraceConstants` holds business values as named constants with a unit test each.

## Key conventions
- **No new features / no redesign** — pixel parity is the acceptance bar. Every deviation is documented in `docs/parity/REPORT.md`.
- `compose.resources` public res class (`packageOfResClass = "com.grace.app.resources"`, `generateResClass = always`).
- `android.nonTransitiveRClass=true`, `android.enableAppCompileTimeRClass=false` in `gradle.properties`.
- Formatting: `kotlin.code.style=official`.

## Known open items (from REPORT.md)
1. **iOS food detection** — Vision's scene classifier misses food on-device; fallback is a bundled Core ML model behind the `FoodClassifier` seam (`composeApp/src/{androidMain,iosMain}/.../FoodClassifier.kt`). Highest priority.
2. **iOS `Exit` (TnC)** — Apple forbids programmatic termination; no-op on iOS.
3. **AGP 9 migration debt** — `composeApp` still mixes `kotlinMultiplatform` + `com.android.application` with `android.builtInKotlin=false`. AGP 10 drops the legacy flag; see skill `kotlin-tooling-agp9-migration`.

## Do not
- Commit `local.properties`, `*.jks`, `*.keystore`, `google-services.json`, `GoogleService-Info.plist`, `*.env`, `secrets.properties`.
- Commit build artefacts (`**/build/`, `.gradle/`, `.kotlin/`, `.gradle/gradle-daemon-jvm.properties`, `DerivedData/`).
- Re-introduce `!src/**/build/` — it un-ignores a risky path.
