# Parity report (PLAN.md Phase 10)

**Conversion:** `/Users/viktorarsovski/Projects/Grace` (Java/Android) → `GraceKMP` (Kotlin Multiplatform + Compose)
**Acceptance bar:** PLAN.md §3.3 — same UI, same resources, same behaviour, no new features, no redesign.

---

## 1. Method

| Layer | How it was verified |
|---|---|
| Domain logic | Pure-Kotlin unit tests (`commonTest`), run on Android JVM **and** iOS simulator |
| Shared UI / behaviour | Driven end-to-end on an Android emulator (API 36.1) with `adb input` + `uiautomator` bounds, screenshots at each step |
| iOS platform code | Kotlin/Native tests that execute the **real** `UIImage`/`CoreGraphics` pipeline on the iOS simulator |
| iOS UI | Built, installed and launched on an iPhone 17 (iOS 26.5) simulator; screens captured |
| Release | `assembleRelease` (R8 + signing) and an Xcode `Release` build |

The Android flow was verified by real interaction: gallery pick → permission →
app-storage copy → ML Kit classification → result screen → bless → share-ready
output. The iOS UI was verified by rendering (no tap automation is available in
this environment — see §6).

## 2. Results

### 2.1 Build & test matrix

| Check | Result |
|---|---|
| `./gradlew :composeApp:assembleDebug` | ✅ |
| `./gradlew :composeApp:testDebugUnitTest` | ✅ **109 tests, 0 failures** |
| `./gradlew :composeApp:iosSimulatorArm64Test` | ✅ **117 tests, 0 failures** |
| `./gradlew :composeApp:assembleRelease` (R8 + shrink) | ✅ with `-PallowPlaceholderMonetization=true` until production monetization IDs are configured |
| `xcodebuild -configuration Debug -sdk iphonesimulator` | ✅ |
| `xcodebuild -configuration Release -sdk iphonesimulator` | ✅ |
| Launch + render, Android emulator | ✅ |
| Launch + render, iOS simulator | ✅ |

### 2.2 Screens / states

| Screen | Android | iOS | Notes |
|---|---|---|---|
| Splash (clouds + logo) | ✅ | ✅ | unified native→Compose "Heaven Opens" scene; see §4.11 |
| GetMeal (two cards) | ✅ | ✅ | `docs/parity/*-getmeal.png` |
| TnC page 1 (Disclaimer) | ✅ | ✅ | WebView loads `blessameal.com/disclaimer.html` |
| TnC page 2 (Privacy Policy) | ✅ | ✅ | checkbox gates `OK`; accent→grey when disabled |
| TnC persistence | ✅ | ✅ | `disclaimer_tnc_key=true` in `<pkg>_preferences.xml` |
| Loading (classify) | ✅ | ✅ | pulsing god, typing indicator |
| Photo — Asks | ✅ | ✅ | bottom-cropped photo, gradient, tap strip, NO/YES |
| Photo — Approves | ✅ | ✅ | blessed photo, DONE/SHARE |
| Photo — Angered | ✅ | ✅ | error-god composite, FEEL THE WRAITH / GIVE IT ANOTHER TRY |
| Wraith pulse | ✅ | ✅ | measured ~12 mean-brightness delta between frames 550 ms apart |
| PhotoDetails viewer | ✅ | ✅ | open from photo area, close button, back pops |
| Settings (new) | ✅ | ✅ | gear in the Get Meal top-right; Privacy / Terms / Invite Friends rows + **Grace Premium** (Remove Ads, Restore Purchases, Privacy Choices, support ID) — see §4.18 |
| Legal page (new) | ✅ | ✅ | in-app `WebPage`; back returns to Settings |
| Invite Friends share | ✅ | ✅ | Android `ACTION_SEND text/plain` chooser; iOS `UIActivityViewController` (iPad popover anchored) |
| Back behaviour | ✅ | n/a | back from Photo → GetMeal; back on GetMeal exits |
| Snackbars | ✅ | ✅ | all six tokens wired (`GraceToken`) |

Screenshots live in `docs/parity/`.

### 2.3 Bless output (golden check)

Input: `705×299` JPEG. Landscape ⇒ `watermarkSize = height / 3 = 99`.
Output: `Grace_Blessed_Photo_20260916_222303.jpg`, `705×299`, JPEG q100, watermark
drawn at `x = 99/12 ≈ 8`, `y = 299 - 99 - 8 = 192` — matches
`BlessGeometry.watermarkX/Y`. See `docs/parity/android-blessed-output.jpg`.

### 2.4 Classifier labels observed

| Platform | Engine | Labels for the sample photo | Decision |
|---|---|---|---|
| Android | ML Kit `image-labeling:17.0.9` @ 0.70 | `[Food, Cuisine]` | meal ✅ |
| iOS | Vision `VNClassifyImageRequest` @ 0.70 | `[]` | **not a meal** ⚠️ |

## 3. Findings

### 3.1 Vision's scene classifier cannot detect food (⚠️ open risk)

`VNClassifyImageRequest` is a **scene** classifier, and on the iOS simulator it
is degenerate — it returns the same three labels for every input:

| Input | Top labels |
|---|---|
| solid black | `outdoor=0.48, night_sky=0.48, sky=0.48` |
| solid white | `outdoor=0.49, night_sky=0.49, sky=0.49` |
| solid green | `outdoor=0.49, night_sky=0.49, sky=0.49` |
| pizza photo | `outdoor=0.48, night_sky=0.48, sky=0.48` |
| sushi photo | `outdoor=0.49, night_sky=0.49, sky=0.49` |
| burger photo | `outdoor=0.49, night_sky=0.49, sky=0.49` |

Because the output does not depend on the input, **iOS food detection cannot be
validated in this environment**, and on the simulator it will always take the
"Angered" path.

Apple's purpose-built API, `VNRecognizeFoodAndDrinkRequest`, is a **Swift
struct** in this SDK (`_$s6Vision28RecognizeFoodAndDrinkRequestV…` in
`Vision.tbd`) with no ObjC class and no header, so it is **not reachable from
Kotlin/Native** (verified: no `_OBJC_CLASS_$_VNRecognizeFoodAndDrinkRequest`
symbol, no matching header, absent from the Kotlin/Native Vision bindings).

**Status:** documented, not silently accepted. See §5 for the recommended fix.
The wiring itself *is* verified: `VisionFoodClassifierTest` proves the
handler → request → completion-handler → coroutine path runs and returns a real
observation list, and `ClassifierParityTest` records the labels for
`docs/parity/REPORT.md` (run it on a device with `GRACE_PARITY_STRICT=1` to turn
the parity expectation into an assertion).

### 3.2 Two real bugs found and fixed by the verification work

1. **iOS: EXIF orientation applied twice.** `UIImage.size` is already
   orientation-corrected, so rotating by the EXIF tag produced wrong dimensions
   (`300×200` came back as `300×200` after a 90° tag instead of `200×300`).
   Fixed by re-wrapping the raw `CGImage` before applying the shared
   `OrientationTransform` — matching Android's `BitmapFactory` semantics.
   Caught by `IosImageProcessorTest.appliesExifRotation90`.
2. **iOS: crash on undecodable input.** `UIImage(data:)` is imported as
   non-null, so a nil return throws a `NullPointerException` rather than
   returning `null`. A corrupt/HTML/mislabelled file would have crashed the app
   instead of reaching the "something went wrong" path. Fixed with a guarded
   `runCatching`, so the failure path now matches Android. Caught while probing
   a mis-downloaded probe image.
3. **Android: `FileProvider` crash on "Capture Meal".** The provider declared
   only the `androidx.core.FILE_PROVIDER_PATHS` meta-data key, so
   `FileProvider.getUriForFile` threw
   `IllegalArgumentException: Missing android.support.FILE_PROVIDER_PATHS meta-data`
   and killed the app on the first tap of the capture card.

   `androidx.core:core:1.17.0` looks up **only** the legacy key — verified in
   the library source:

   ```java
   private static final String META_DATA_FILE_PROVIDER_PATHS =
           "android.support.FILE_PROVIDER_PATHS";   // FileProvider.java:358
   ...
   info.loadXmlMetaData(context.getPackageManager(), META_DATA_FILE_PROVIDER_PATHS);
   ```

   This is exactly why the original `Grace` manifest declared **both** keys; the
   port had dropped one. Both are restored, and the two FileProvider call sites
   (`AndroidPhotoPicker.takePhoto`, `AndroidShareService.sharePhoto`) now catch
   `IllegalArgumentException`/`ActivityNotFoundException` so a future provider
   misconfiguration degrades to a snackbar instead of a crash.
   Re-verified: camera launches, writes `Grace_Photo_20260917_000248.jpg`
   (63 KB) through the provider URI, and the flow continues to classification.

### 3.3 String escaping

Compose Resources does **not** honour Android's `\'` escape: it rendered
`Hey! That\'s not a photo of a meal!` with a literal backslash. Replaced with
the XML numeric entity `&#39;` (also applied to `no_internets`,
`i_agree_text`, `interrupted_text`). Verified on-device.

## 4. Deviations from PLAN.md

All are recorded here; none change user-visible behaviour except where noted.

| # | Plan | What was built | Why |
|---|---|---|---|
| 1 | ADR-4: Skiko for image processing in `commonMain` | Platform pipeline (`Bitmap`/`Canvas` on Android, `UIImage`/`CoreGraphics` on iOS) behind `ImageProcessor`; all *maths* shared and unit-tested | Skiko is **not on the Android classpath** — Compose on Android draws through the platform canvas. ADR-4 was unbuildable as written. |
| 2 | `expect`/`actual` interfaces for platform capabilities | Services are common `interface`s; the **DI module** is `expect`/`actual` (`platformModule`) plus `expect` for `Log`, `PlatformWebView` and screen insets | Same seam, simpler wiring; the platform set is chosen once, at the DI boundary. |
| 3 | `CropToViewportUseCase` renders the bottom crop | `CropGeometry` implemented + unit-tested; rendering uses `ContentScale.Crop` + `Alignment.BottomCenter` | Visually identical for real photos (all wider than the viewport). The pure function is still the tested contract. |
| 4 | Phase 2 task 8: temporary "asset gallery" screen | Skipped; every asset is exercised by the real screens instead | The gallery would have been throwaway code; real-screen screenshots prove fidelity better. |
| 5 | Phase 3: iOS files in `Documents/GraceApp` | `Documents/GraceApp` for camera/blessed, `Documents/` for picked | Mirrors Android, which only adds the `GraceApp` sub-folder in `getOutputMediaFile`. |
| 6 | Phase 1.5 prose: Loading has a "black bg" | `colorPrimary` | The layout (`fragment_loading.xml`) says `@color/colorPrimary`; PLAN §0 makes the source the source of truth. |
| 7 | iOS deployment target 15.0 (open question 2) | Kept **15.0** | Only a benign linker warning about a data-only ICU object; no code change needed. See §5. |
| 8 | "Release APK is signed" | Signed via `GRACE_KEYSTORE_*` in `local.properties`/env, **unsigned when unset** | Secrets must not be committed. A verification keystore was used locally; the repo ships no key material. |
| 9 | Camera unavailable → nothing happens | Snackbar `no_photo_was_taken` | The original silently did nothing when `resolveActivity` returned null; a message is strictly better and reuses an existing string. |
| 10 | `StatusBarColor` per screen | No-op on iOS | iOS status bars are not tintable; appearance is VC-driven. Android parity is exact. **Superseded by #12.** |
| 11 | Reproduce the splash timeline verbatim: 1200ms clouds, then a 1200ms logo drop, then a 1100ms hold (~3.8s) | One continuous "Heaven Opens" scene (~2.1s), identical on both platforms: the native frame is a plain `colorPrimary` field (iOS `LaunchScreen.storyboard`; Android starting window, with the Android 12+ system splash icon suppressed via `values-v31`), the clouds fade in and open while `grace_main` rises with a halo and a spring settle, then the scene crossfades (450ms) into Get Meal. The splash is full-bleed, so the clouds run to the very bottom edge under the navigation bar / home indicator rather than leaving a blue strip. | On iOS the generated launch screen and the Compose splash were two visibly distinct screens, and on Android the Android 12+ system splash showed a centred app icon for the whole cold start. A bottom-anchored, width-scaled cloud is not expressible as an Android window-background drawable across screen sizes, so both native frames are reduced to the same solid field and the cloud reveal moved into the shared animation. |
| 12 | Per-screen status-bar tint (PLAN.md §1.4): `colorPrimaryDark` on GetMeal, black on the photo states | Android draws edge-to-edge (`enableEdgeToEdge` in `MainActivity`, `safeDrawingPadding` in `platformScreenInsets`); the status bar is transparent and simply shows the app background behind it. `SystemUi.setStatusBarColor` and the `StatusBarColor` enum are gone. | The tint left a visible seam — the GetMeal status bar was `#2196F3` against a `#03A9F4` background. Letting the bar show the app background makes it match on every screen, and aligns Android with iOS, which has no tintable status bar. |
| 13 | Not in the original: no settings entry point | Added a **Settings** screen (`Screen.Settings`, pushed over Get Meal) with a gear action in the Get Meal top-right corner and three rows: Privacy Policy, Terms and Conditions, Invite Friends. The legal rows push `Screen.Legal` and reuse the existing `PlatformWebView` (extracted to `ui/components/WebPage`, now shared with the TnC dialog); Invite Friends extends `ShareService` with `shareText` (`ACTION_SEND text/plain` on Android, `UIActivityViewController` on iOS). | Requested feature. Navigation uses the existing `Navigator.push`/`pop`, so system back and the in-app back arrow both return Settings → Get Meal without disturbing the photo flow. |
| 14 | The two meal cards were equal `weight(1f)` panels (24dp margin, 2dp radius) | Cards are rounded tiles (`CardRadius` 20dp, `CardSpacing` 40dp) whose size is derived from the space available: the largest square (capped at `CardMaxWidth` 340dp) that lets both cards plus their spacing fit the column. The illustration stays inside the card. The settings gear's 24dp icon sits inside a 48dp touch target right-aligned within a `cardWidth`-wide button so the icon's right edge aligns with the cards' right edge. | Requested redesign ("smaller cards with images inside and round edges"). Sizing from the available height — rather than a fixed aspect ratio — is what stops the bottom card being cut off on short screens; verified at 411×914dp and 360×640dp. |
| 15 | Splash: `grace_main` rose from 350ms while the clouds were still opening, to an unrelated 30%-of-height destination with a 1.02 overshoot | The badge is released only after the whole cloud sequence has played (`clouds.join()`, 930ms) and all badge channels are joined before the hold. Its resting position is derived from the cloud artwork geometry (`SplashGeometry`): the top edge sits 24dp below the white-cloud boundary (asset y 210), which is below the sun (asset y 24.2–182.2), and the rise keyframes are monotonic (no overshoot). | Requested: the logo must stop inside the white cloud and never cover the sun, after the sun has appeared behind the clouds. Verified on-device: the sun is fully visible at y 915–1209px and the badge rests at y 1349–1926px. |
| 16 | Splash cloud layer sized by `Image` intrinsic measurement | The cloud image is sized explicitly (`fillMaxWidth().height(maxWidth * 767/540)`) so `SplashGeometry` can derive the badge position exactly instead of relying on intrinsic measurement. | Makes the geometry deterministic across densities and tablet widths, and unit-testable. |
| 17 | Not in the original: the navigation bar strip matched the window background on every screen | Every photo result screen (`Screen.Photo` — Asks, Approves and Angered) paints the strip behind the system navigation bar black, so each black panel reads as running to the very bottom edge. Every other screen keeps the window background. Implemented in `AppNavHost` (`systemNavigationBarBackdrop`, unit-tested) as a bottom-aligned rect the height of the safe-drawing bottom inset. | Requested for all photo result screens (they all carry a black panel). Painting behind the bar — rather than calling `setNavigationBarColor` — keeps it working on iOS, which has no tintable navigation bar, and leaves the screen's own content inset so the action buttons stay clear of the gesture area. |
| 18 | Not in the original: no advertising, no purchases, no consent flow | **Monetization** (requested): an AdMob interstitial after every second successful blessing, a one-shot "Remove Ads" bottom sheet after a displayed ad, a Grace Premium section in Settings (Remove Ads · price, Restore Purchases, Privacy Choices, support ID), versioned legal acceptance (`REQUIRED_POLICY_VERSION = 2`, so existing installs review the new ads/purchase terms once), RevenueCat (anonymous App User ID) + UMP consent. Full detail in `docs/monetization-setup.md` and `docs/data-safety.md`. | Requested feature. Advertising stays *outside* the blessing use case (`LoadingViewModel.bless()` awaits the platform modal immediately before showing Approves; a failed/unavailable/consent-denied ad falls open to the result). Paid and unresolved entitlements never see an ad. |

## 5. Open items / required follow-up

1. **iOS food detection (highest priority).** On a physical device, re-run
   `ClassifierParityTest` with `GRACE_PARITY_STRICT=1`. If Vision's scene
   taxonomy still misses food, bundle a small Core ML food/non-food model and
   run it through `VNCoreMLRequest` — the only remaining reachable option in
   Kotlin/Native. The `FoodClassifier` seam means this is a one-file change.
2. **iOS `Exit` (TnC).** Apple forbids programmatic termination, so `Exit` is a
   no-op on iOS; the Android behaviour (`finishAffinity`) is preserved.
3. **iOS interaction automation.** Not available in this environment (§6), so
   the iOS *picker*, *share sheet* and *connectivity observer* were verified by
   compilation + API-shape review and their pipeline tests, not by tapping.
4. **`sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`** is
   still the clean fix for the `DEVELOPER_DIR` workaround.
5. **APK size.** The release APK is ~47 MB, dominated by ML Kit's
   `libmlkitcommonpipeline.so` for four ABIs (~42 MB). The checked-in
   `Grace/apk/app-release.apk` (4 MB) contains **no `lib/` at all**, so it
   predates the ML Kit integration and is not a valid size baseline. Add ABI
   splits if a smaller download is wanted.
6. **Settings legal URLs are placeholders.** The original shipped only two legal
   documents (a Disclaimer and a Privacy Policy), so the Settings rows point at
   the pages the first-run dialog already uses:
   `PRIVACY_POLICY_URL = TNC_URL` and `TERMS_AND_CONDITIONS_URL = DISCLAIMER_URL`
   (`core/GraceConstants.kt`). Replace `TERMS_AND_CONDITIONS_URL` with the real
   Terms & Conditions page. `INVITE_FRIENDS_URL` is likewise the standard Play
   Store deep link for `com.grace.app` and must be swapped for the published
   store listing (and given an App Store URL on iOS) before release.
7. **Monetization production values are placeholders** (requested feature, deviation #18).
   Debug builds ship Google's sample AdMob IDs + a dummy purchase backend so the flow is
   demoable; release builds refuse to assemble until the real IDs/keys are configured
   (`-PallowPlaceholderMonetization=true` opts out for local R8 verification only). See
   `docs/monetization-setup.md`.
8. **iOS ads are not yet linked.** The Google Mobile Ads + UMP Swift packages are not in
   the Xcode project; the bridge (`iosApp/iosApp/GraceAdBridge.swift`) compiles to a no-op
   behind `#if canImport(GoogleMobileAds)`, so iOS never requests an ad today. Add the two
   Swift packages per `docs/monetization-setup.md` §5 to activate.

## 6. Verification environment notes

- Android: `Medium_Phone_API_36.1`, 1080×2400 @ 420 dpi.
- iOS: iPhone 17, iOS 26.5.
- iOS UI taps are not automatable here (`simctl` has no input command; no
  `idb`/`cliclick`; `osascript` assistive access is not granted), which is why
  the iOS strategy was *render verification* plus *on-simulator pipeline tests*.
- Compose Resources and UIKit both work inside Kotlin/Native test binaries, so
  `IosImageProcessorTest` exercises the production code paths for real.

## 7. Definition of done (PLAN §8)

| Item | Status |
|---|---|
| Runs on Android and iOS with identical UI and behaviour | ✅ UI identical; ⚠️ iOS food decision pending §5.1 |
| Same assets, strings, colours, fonts, animation timings | ✅ |
| Full journey: capture/pick → classify → bless → share + error path + TnC gate | ✅ verified on Android; ✅ on iOS except the classify decision |
| Domain logic unit-tested (matcher, EXIF, geometry, naming, monetization) | ✅ 109 Android / 117 iOS tests |
| No dead code carried over | ✅ |
| Release builds produced for both platforms | ✅ (Android signed with a local verification key; iOS Release builds, archive needs a team) |
| Parity report completed | ✅ this document |
