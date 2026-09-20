# Environment check (PLAN.md Phase 0)

Captured on the build machine at the time the conversion was executed.

| Tool | Version / value |
|---|---|
| OS | macOS (darwin, Apple silicon) |
| JDK | Temurin/OpenJDK **17.0.20.1** |
| Gradle | **9.5.0** (wrapper, pinned in `gradle/wrapper`) |
| Kotlin | **2.4.10** |
| Android Gradle Plugin | **9.3.2** |
| Compose Multiplatform | **1.12.0** |
| Android SDK | `/Users/viktorarsovski/Library/Android/sdk` (via `local.properties`) |
| Android build-tools | 36.1.0 |
| Xcode | **26.6** |
| Kotlin/Native prebuilts | `~/.konan/kotlin-native-prebuilt-macos-aarch64-*` (2.2.20 – 2.3.0) |

## Android

- `ANDROID_HOME`/`local.properties` resolves (`sdk.dir` above).
- Emulator used for verification: `Medium_Phone_API_36.1` (1080×2400 @ 420dpi, API 36).

## iOS

`xcode-select` still points at the Command Line Tools on this machine, so every
iOS command needs `DEVELOPER_DIR`:

```bash
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
```

The one-time clean fix (needs sudo, not executed here):

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

Simulators used for verification:

| Simulator | Runtime | Device id |
|---|---|---|
| iPhone 17 | iOS 26.5 | `12847EB1-41A6-474E-8779-BEEDDA9B518B` |

## One-time commands executed

```bash
# Android build + install + launch
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk

# iOS build + install + launch (DEVELOPER_DIR exported first)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -derivedDataPath /tmp/grace-dd build
xcrun simctl install <device> .../Grace.app
xcrun simctl launch <device> com.grace.app
```

CocoaPods is **not** used: the Kotlin framework is built by the
`Compile Kotlin Framework` Xcode build phase and embedded directly.
