# Grace → Kotlin Multiplatform (KMP) Conversion Plan

**Target project:** `/Users/username/Projects/GraceKMP`
**Source project:** `/Users/username/Projects/Grace` (Java Android app, package `com.grace.app`)
**Scope:** Android + iOS, **shared Compose Multiplatform UI**
**Goal:** Reproduce the existing app 1:1 — same UI, same resources, same functionality — on a modern KMP architecture.

> This document is the deliverable of the planning phase. It contains no code changes to the source app.
> Execution is driven **phase by phase**: each phase below is self-contained, has explicit tasks, deliverables, and a verification step. Phases 1→11 are executed in order on request.

---

## 0. How to use this plan

- Each phase ends with **Verification** commands/checks. A phase is only "done" when those pass.
- Phases are ordered so that the app **builds and runs** after every phase (never leave the tree broken).
- The original Grace project is the **source of truth** for behavior. When in doubt, re-read the referenced Java file/line and match it exactly.
- Do **not** port dead code. See §5.4.
- No new features. No redesign. Pixel parity is the acceptance bar.

---

## 1. Current-state analysis (verified against source)

### 1.1 What the app does

A joke app: the user takes or picks a photo of a meal, an on-device image classifier decides whether it is food, and if it is, the app composites a "blessed" watermark onto the photo and offers to share it.

### 1.2 Tech stack of the original (as found)

| Area | Original |
|---|---|
| Language | Java 8 |
| Build | Gradle 9.0-milestone-1 wrapper, AGP 8.13.2, single `:app` module |
| UI | Android Views + XML layouts + ViewBinding |
| Architecture | MVP (View interface / Presenter / Interactor) with `Loader`-backed presenter retention (`BaseActivity`, `PresenterLoader`, `PresenterFactory`) |
| DI | Dagger 2.50 (`AppComponent`, per-screen sub-components + modules) |
| Images | Glide 4.16 (`RequestManager` injected), custom `BitmapCropTransformation` |
| Classifier | ML Kit `image-labeling:17.0.9` (on-device, no network) |
| Events | GreenRobot EventBus 3.3.1 (only for the "interrupted his Grace" snackbar) |
| Custom UI | `PhotoView` 2.3.0 (pinch-zoom), `widgetlab` `TypingIndicatorView`, `AutoFitTextView`, `material-ripple` |
| Dead deps | Retrofit 2.9 / OkHttp 4.12 / Gson 2.10.1 (no runtime usage — legacy Clarifai), Calligraphy 2.3 (explicitly disabled in `GraceApplication`), multidex, `BlurDrawable` (defined, never used) |
| SDK | minSdk 21, targetSdk 34, compileSdk 34, versionCode 1, versionName `1.0.4`, portrait-only |
| Other | `FileProvider` (`${applicationId}.provider`), network-state `BroadcastReceiver` → snackbar, `GraceAppTheme.NoActionBar` + translucent variant |

### 1.3 Screens & flow

```
GraceSplashScreenActivity (launcher, portrait)
   │  clouds+sun frame animation → logo fade+drop → 1100ms hold → fade transition
   ▼
Main2Activity  (fragment container @R.id.main_frame_layout; GetMealFromFragment by default)
   │
   ├─ GetMealFromFragment ── "Capture Meal" ──▶ Main2Activity.startExternalCamera()
   │                      └─ "Meal From Gallery" ─▶ Main2Activity.getPhotosFromGallery()
   │        (first run: DisclaimerTermsAndConditionsDialogFragment over the top)
   │
   ├─ LoadingFragment (message = "Let me see that photo for a moment")
   │        decode + EXIF-orient → FoodDetector (ML Kit) → isMeal?
   │
   ├─ PhotoFragment  ── "His Grace Asks / Would you like to bless this meal? / No | Yes"
   │     │  (Yes → LoadingFragment message = "Please wait, blessing meal" → BlessPhotoWorker)
   │     │  (No  → back to GetMealFromFragment)
   │     └─ "His Grace Approves / You can eat now! / Done | Share"
   │            Share → ACTION_SEND chooser
   │            tap photo strip → PhotoDetailsActivity (zoomable full-screen)
   │
   └─ PhotoFragment (error state) ── "His Grace is Angered / Hey! That's not a photo of a meal!"
              "Feel the wraith" (pulsing error god) | "Give it another try"
```

### 1.4 Business logic (exact values — must be preserved)

**Food detection** — `custom/FoodDetector.java`
- ML Kit `ImageLabeling` with `ImageLabelerOptions.setConfidenceThreshold(0.70f)`.
- Whitelist: `food, dish, cuisine, meal, dessert, fruit, vegetable, burger, pizza, sandwich, salad, pasta, sushi`.
- Match rule: exact match **or** substring containment (`text.contains(key)`), case-insensitive (`Locale.US`).
- Any match ⇒ `isMeal = true`.

**Bless (watermark compositing)** — `custom/BlessPhotoWorker.java` + `util/GracePhotoUtil.java:129`
- Decode meal photo, EXIF-orient it (`getHandledBitmap`).
- Decode `watermark_blessed_transparent.png`.
- Watermark square size = `min(photoW, photoH) / 3` (`WATERMARK_DIMENSIONS_FACTOR = 3`); orientation decides whether width or height drives it (`getResizeType`: portrait if `w <= h`).
- Draw at `x = watermarkW / 12`, `y = photoH - watermarkH - (watermarkH / 12)` (`MARGIN_FACTOR = 12`).
- Output: JPEG quality **100** → `Grace_Blessed_Photo_yyyyMMdd_HHmmss.jpg`.

**EXIF orientation** — `util/GracePhotoUtil.java:145` handles UNDEFINED, FLIP_HORIZONTAL, ROTATE_180, FLIP_VERTICAL, TRANSPOSE (no-op), ROTATE_90, TRANSVERSE (no-op), ROTATE_270.

**Photo list crop (result screen)** — `custom/BitmapCropTransformation.java`
- `width = min(layoutWidth, bmpW)`; if `layoutHeight < bmpH` then `height = layoutHeight`, `y = bmpH - layoutHeight` (bottom-anchored crop), else `height = bmpH, y = 0`.
- Called with `layoutHeight = screenHeight * 0.6`, `layoutWidth = screenWidth`.

**Output files** — `util/GracePhotoUtil.java:39`
- Dir: `getExternalFilesDir(DIRECTORY_PICTURES)/GraceApp` (fallback `cacheDir`).
- Camera file: `Grace_Photo_yyyyMMdd_HHmmss.jpg`.
- Gallery pick: copied to `picked_<millis>.<ext>` (extension from content-resolver MIME).
- Blessed file: `Grace_Blessed_Photo_yyyyMMdd_HHmmss.jpg`.

**Persistence** — `AppModule.provideSharedPreferences` uses `PreferenceManager.getDefaultSharedPreferences`.
- Single key: `disclaimer_tnc_key` (Boolean) — TnC accepted. (`Constants.java:51`)

**TnC dialog** — `view/dialog/DisclaimerTermsAndConditionsDialogFragment.java`
- 2 pages via `ViewPager` + `TabLayout` dots: page 0 = `https://goo.gl/iCBeBP` (Disclaimer), page 1 = `https://goo.gl/vFgXgD` (Privacy Policy).
- Page 0: title "Disclaimer", action "Next" + next-arrow, checkbox hidden, action enabled.
- Page 1: title "Privacy Policy", action "OK" (disabled until checkbox checked), checkbox visible.
- "Exit" → `finish()` the activity.
- On OK → persist `disclaimer_tnc_key = true`, dismiss.
- WebView: JS enabled, load-with-overview, `WebChromeClient`, on page finished show webview / hide typing indicator; on error load `file:///android_res/raw/empty_page.html`.

**Animation constants** — `constants/Constants.java:42`
| Constant | Value | Used by |
|---|---|---|
| `ANIMATION_DURATION_GMF` | 3000 | (unused) |
| `ANIMATION_LOGO_DURATION` | 1000 | (unused) |
| `CROSS_FADE_DURATION` | 1000 | Glide crossfade (photo result + details) |
| `LOADING_ANIMATION_DURATION` | 1100 | loading pulse; wraith pulse |
| `ANIMATION_LOGO_DURATION_L` | 1200 | splash logo alpha + Y |
| `SCALE_DURATION` | 500 | card / button scale-in |
| `TAP_DURATION` | 700 | "tap for full photo" strip scaleY |

**Splash timing** — `view/activity/GraceSplashScreenActivity.java`
- Frame animation `splash_clouds_sun_animation` (frames `clouds_0..clouds_8`), first frame `300ms` (`frame_duration_could_sun_animation_0`), remaining 9 frames `100ms` each (`frame_duration_could_sun_animation`) ⇒ ~1200ms total, one-shot.
- Then logo: alpha `0.1 → 1.0` and `Y` from `containerHeight` to `containerHeight/2 - containerHeight/5`, 1200ms.
- Hold `1100ms`, then `Main2Activity` with `fade_in`/`fade_out` (`overridePendingTransition`).

**Other behaviors to preserve**
- `Main2Activity.onBackPressed`: if `GetMealFromFragment` is not present, replace back to it instead of exiting.
- Camera result → `LoadingFragment(let_me_see_text)`; gallery result → same.
- Snackbars: no internet (`no_internets`), permission not granted, no photo taken, no photo selected, interrupted (`interrupted_text`), `something_went_wrong_text` (release) / raw error (debug).
- `onIsMealResponseFailure` → snackbar + return to GetMealFromFragment.
- Status bar color changes: GetMealFrom → `colorPrimaryDark`; PhotoFragment (no photo) → `colorPrimaryDark`, (with photo) → black.
- `PhotoDetailsActivity`: translucent theme, crossfade 1000ms, `PhotoViewAttacher` zoom disabled until load completes, then zoomable; close button → back; back → fade transition.
- App is portrait-only on all screens.

### 1.5 UI inventory (must be reproduced)

| Screen | Layout file | Key structure |
|---|---|---|
| Splash | `activity_splash_screen.xml` | RelativeLayout, `colorPrimary` bg; `background_image_view` (bottom-aligned, `splash_clouds_sun_animation` AnimationDrawable); `logo_image_view` (`grace_main`, centered, bottom, initially GONE) |
| Main | `activity_main.xml` | FrameLayout container `main_frame_layout` |
| GetMeal | `fragment_get_meal_from.xml` | Vertical LinearLayout weightSum 1; two `CardView` (weight .5, corner radius/elevation from dimens) each wrapping `MaterialRippleLayout` → RelativeLayout (`capture_meal_linear_layout` / `from_gallery_linear_layout`) with centered vector + bottom all-caps 18sp white label |
| Loading | `fragment_loading.xml` | Black bg, `grace_loading_image_view` (`loading_god`), pulsing; message text |
| Photo result | `fragment_photo.xml` | 60% photo area (black bg, `meal_image_view`, `black_gradient` overlay, error god layer, tap-full strip `colorAccent` alpha .3) + 40% black panel (title `colorAccent` caps, `AutoResizeTextView` subtitle, two rounded ripple buttons white/pink) |
| Photo details | `activity_photo_details.xml` | Translucent black, zoomable `PhotoView`, close icon |
| TnC dialog | `dialog_disclaimer_tnc.xml` | Title, `ViewPager`, dots `TabLayout`, checkbox, action text (with arrow), exit text |
| TnC page | `fragment_disclaimer_tnc.xml` | `WebView` + `TypingIndicatorView` |

**Colors** (`values/colors.xml`): `colorPrimary #03a9f4`, `colorPrimaryDark #2196f3`, `colorAccent #FF4081`, `transparent_black #B3000000`.
**Fonts**: `assets/fonts/cabin_bold.ttf`, `cabin_regular.ttf` (Calligraphy was disabled, so they were effectively unused — we will wire them properly as the app's typography).
**Strings**: full inventory in `values/strings.xml` (28 entries + app name). See §5.3.

**Assets** (must all be migrated):
- Vector XMLs: `clouds_0..clouds_8`, `error_god`, `error_god_top`, `grace_main`, `loading_god`, `loading_god_no_circles`, `take_photo_of_meal`, `upload_meal`, `ic_close`, `ic_forward`, `ic_next_wrapper`, `ic_dummy_next_wrapper`, `ic_dummy_forward`, `black_gradient`, `rounded_button_pink`, `rounded_button_white`, `tab_indicator_default`, `tab_indicator_selected`, `tab_selector`, `tnc_dialog_selector`, `splash_clouds_sun_animation`.
- PNGs: `watermark_blessed_transparent.png`, `watermark_circle.png`, `watermark_portrait.png`, `god_cloud_splash.png`.
- Launcher icons: `mipmap-*/ic_launcher.png`.
- Fonts: `cabin_bold.ttf`, `cabin_regular.ttf`.
- Raw: `empty_page.html` (WebView error fallback).

---

## 2. Target architecture

### 2.1 Principles

- **Shared Compose Multiplatform UI** in `commonMain`; platform source sets contain only true platform glue.
- **Unidirectional data flow (MVVM + MVI-style)**: immutable `UiState` + `UiAction` (intents) + one-shot `UiEffect` (events). Replaces the MVP/presenter/interactor/EventBus stack.
- **Pure Kotlin domain** with no Android/Skia dependencies where possible (fully unit-testable).
- **Platform capabilities behind `expect`/`actual`** interfaces, injected via DI.
- **Parity-first**: every behavior/value in §1.4 is encoded as a named constant or use case, not scattered.

### 2.2 Module & source-set layout

```
GraceKMP/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── gradle.properties
├── gradlew / gradlew.bat / gradle/wrapper/*
├── composeApp/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/grace/app/
│       │   │   ├── App.kt                      # root composable + theme + nav host
│       │   │   ├── di/                         # Koin modules
│       │   │   ├── core/                       # constants, logging, result types, dispatchers
│       │   │   ├── domain/                     # pure Kotlin: models, matcher, use cases, exif
│       │   │   ├── data/                       # file store, image codec (Skiko), settings
│       │   │   ├── platform/                   # expect declarations
│       │   │   └── ui/                         # screens, components, theme, navigation
│       │   └── composeResources/               # drawables, fonts, strings, files
│       ├── androidMain/kotlin/...              # actuals: ML Kit, picker, share, webview, file paths
│       ├── androidMain/AndroidManifest.xml
│       ├── iosMain/kotlin/...                  # actuals: Vision, picker, share, webview, file paths
│       └── commonTest/kotlin/...               # unit tests for domain + view models
├── iosApp/                                     # Xcode project (SwiftUI host, framework linking)
└── README.md
```

- Single KMP application module (`composeApp`) + `iosApp` host. Rationale: app is small (6 screens); a single module keeps phased builds reliable while still allowing a clean package layering. If it grows, `domain`/`data` can be split into a `:shared` module later without touching UI code.

### 2.3 Libraries

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin 2.4.x | Pin exact version in Phase 1 (2.4.20 stable, Sept 7 2026) |
| UI | Compose Multiplatform 1.12.0 | Stable (Aug 25 2026); Material3 for components, restyled to match legacy look |
| ViewModel | `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` | Multiplatform ViewModel |
| DI | **Koin** | Multiplatform, simple; replaces Dagger. (Alternative: kotlin-inject, compile-time — noted, not chosen) |
| Async | `kotlinx-coroutines-core` + `Flow`/`StateFlow`/`SharedFlow` | Replaces ExecutorService/Handler/EventBus |
| Settings | `com.russhwolf:multiplatform-settings` | For `disclaimer_tnc_key` |
| Image loading | **Coil 3** (`io.coil-kt.coil3`) | Multiplatform; result/detail photo display |
| Bitmap pipeline | **Skiko** (`org.jetbrains.skia`) | Decode/encode/rotate/draw watermark in `commonMain` |
| Date/time | `kotlinx-datetime` | For `yyyyMMdd_HHmmss` file names |
| Classifier | ML Kit (Android) / Vision `VNClassifyImageRequest` (iOS) | Behind `expect`/`actual` |
| Testing | `kotlin-test`, `kotlinx-coroutines-test` | Domain + ViewModel tests |

### 2.4 Architecture decision records (ADRs)

- **ADR-1 — Shared Compose UI over native UIs.** Confirmed with user. One UI codebase; guarantees identical look/behavior on both platforms and minimizes duplication.
- **ADR-2 — UDF (State/Action/Effect) instead of MVP.** MVP + `Loader` + EventBus is Android-specific and untestable across platforms. UDF with `StateFlow` is idiomatic KMP and keeps ViewModels in `commonMain`.
- **ADR-3 — Koin instead of Dagger.** Dagger is Java/annotation-processor based and not multiplatform. Koin is the mainstream KMP DI choice; the graph here is tiny.
- **ADR-4 — Skiko for image processing in shared code.** Compose Multiplatform already ships Skiko, so decoding, EXIF-driven rotation, watermark compositing, and JPEG encoding can live in `commonMain` (one implementation, unit-testable) instead of two platform implementations.
- **ADR-5 — Custom screen-stack navigator.** The app is a linear flow with a fragment `replace()` (no real back stack). A ~50-line navigator that holds an ordered screen stack reproduces this exactly, avoids alpha-stage navigation libraries, and keeps behavior under our control. `PhotoDetails` is a full-screen destination (was an Activity).
- **ADR-6 — Classifier: ML Kit on Android, Vision on iOS, matching shared.** No shared on-device image-labeling API exists. Both platforms return labels; the whitelist/threshold matching lives in shared `FoodLabelMatcher` so decision logic is identical (label *vocabulary* differs — see risks).
- **ADR-7 — WebView via `expect`/`actual` composable.** CMP has no first-class WebView. A small `PlatformWebView(url)` (Android `WebView`, iOS `WKWebView`) avoids a third-party dependency and is fully controllable for parity.
- **ADR-8 — Drop all dead code.** Retrofit/OkHttp/Gson, Calligraphy, EventBus, Dagger, multidex, `BlurDrawable`, `ToastUtil`, `LoadingDialogFragment`, `dialog_loading.xml`, `dialog_photo_result.xml`, `activity_splash_screen_1.xml`, `PresenterLoader` infra are not ported.

### 2.5 Old → new mapping

| Original (Java) | New (Kotlin, commonMain unless noted) |
|---|---|
| `GraceApplication` + `AppComponent`/modules | `GraceApp` + Koin `appModule`/`platformModule` |
| `BaseActivity`/`BaseFragment` + `PresenterLoader`/`PresenterFactory` | `GraceViewModel` base + `lifecycle-viewmodel-compose` |
| `view/*View` interfaces | `UiState`/`UiAction`/`UiEffect` per feature |
| `presenter/impl/*PresenterImpl` | `ui/<feature>/*ViewModel.kt` |
| `interactor/impl/*InteractorImpl` (empty ones) | deleted |
| `custom/FoodDetector` | `domain/food/FoodLabelMatcher` (shared) + `platform/FoodClassifier` (actuals) |
| `custom/BlessPhotoWorker` | `domain/photo/BlessPhotoUseCase` |
| `custom/BitmapCropTransformation` | `domain/photo/CropToViewportUseCase` |
| `util/GracePhotoUtil` (EXIF/orient/resize) | `domain/photo/JpegExifReader`, `ImageTransforms`, `domain/photo/GraceFileNames` |
| `util/GracePhotoUtil.getOutputMediaFile` | `data/GraceFileStore` (actual per platform) |
| `util/ShareUtil` | `platform/ShareService` (actual) |
| `util/UiUtil` | Compose modifiers / `LocalWindowInfo` (screen size) |
| `util/LogUtil` | `core/Log` (expect/actual → Logcat / NSLog) |
| `util/ToastUtil` (unused) | deleted |
| `view/helper/ChangeActivityHelper` | `ui/navigation/Navigator` |
| `view/helper/ChangeFragmentHelper` | `ui/navigation/Navigator` + `AppNavHost` |
| `Main2Activity` camera/gallery + permissions | `platform/PhotoPicker` (actuals) |
| `BaseActivity.NetworkStateReceiver` | `platform/ConnectivityObserver` (actuals) → effect |
| EventBus `post(true)` interruption | `BlessViewModel`/`LoadingViewModel` `UiEffect.Interrupted` |
| `SharedPreferences` | `multiplatform-settings` via `data/SettingsStore` |
| Glide `RequestManager` | Coil 3 `AsyncImage` / `ImageLoader` |
| `PhotoView` zoom | Compose `transformable` pinch/pan in `PhotoDetailsScreen` |
| `AutoFitTextView` | `AutoSizeText` composable (custom `Text` with auto-scale) |
| `MaterialRippleLayout` | Material3 `ripple` (indication) |
| `widgetlab TypingIndicatorView` | `TypingIndicator` composable (3 animated dots) |
| `WebView` TnC pages | `PlatformWebView` (expect/actual) |

### 2.6 Dropped dead code (do not port)

`Retrofit`, `OkHttp`, `logging-interceptor`, `Gson`, `converter-gson`, `Calligraphy`, `EventBus`, `Dagger`/`javax.inject`, `multidex`, `BlurDrawable`, `ToastUtil`, `LoadingDialogFragment`, `dialog_loading.xml`, `dialog_photo_result.xml`, `activity_splash_screen_1.xml`, `ExampleInstrumentedTest`, `ExampleUnitTest`, `BuildConfig` `BASE_URL`/`USERNAME`/`PASSWORD`, `Constants.IS_THIS_MEAL`/`SOURCE`/`TYPE`/`SHOW_RAW`/`DATA_VALUE` and other Clarifai leftovers, `getPhotoSizeInMB`/`convertPhotoToString` (base64 upload path, unused), `Constants.SCALE_FACTOR`.

---

## 3. Parity specification (the acceptance contract)

### 3.1 Constants to encode (shared `GraceConstants.kt`)

```
FOOD_CONFIDENCE_THRESHOLD = 0.70f
FOOD_LABELS = [food, dish, cuisine, meal, dessert, fruit, vegetable,
               burger, pizza, sandwich, salad, pasta, sushi]
CROSS_FADE_MS = 1000
LOADING_PULSE_MS = 1100
SPLASH_LOGO_MS = 1200
SPLASH_FIRST_FRAME_MS = 300
SPLASH_FRAME_MS = 100
SPLASH_HOLD_MS = 1100
SCALE_IN_MS = 500
TAP_STRIP_MS = 700
WATERMARK_DIMENSIONS_FACTOR = 3
MARGIN_FACTOR = 12
JPEG_QUALITY = 100
APP_FOLDER = "GraceApp"
CAMERA_FILE_PREFIX = "Grace_Photo_"
BLESSED_FILE_PREFIX = "Grace_Blessed_Photo_"
DATE_PATTERN = "yyyyMMdd_HHmmss"
PICKED_FILE_PREFIX = "picked_"
DISCLAIMER_URL = "https://goo.gl/iCBeBP"
TNC_URL = "https://goo.gl/vFgXgD"
SETTING_DISCLAIMER_TNC = "disclaimer_tnc_key"
```

### 3.2 Colors & typography

- `Primary #03A9F4`, `PrimaryDark #2196F3`, `Accent #FF4081`, `TransparentBlack #B3000000`.
- Typography: Cabin Regular (body/subtitle) + Cabin Bold (titles/buttons), from bundled TTFs.
- Text casing, sizes, and weights must match the XML (`textAllCaps`, 18sp card labels, etc.). Exact sp/dp values come from `values/dimens.xml` during Phase 2.

### 3.3 Screen parity checklist

- [ ] **Splash** — `colorPrimary` bg, bottom cloud/sun frame animation with exact per-frame timings, logo fade+drop to the same vertical position, same hold, fade into Main.
- [ ] **GetMeal** — two equal cards with same margins/radius/elevation, ripple, same icons, same labels, 500ms scale-in on entry, status bar `PrimaryDark`.
- [ ] **TnC dialog** — pager with 2 pages, dot indicator, checkbox gating, Next→OK, Exit, arrow swap, persisted acceptance, error fallback page.
- [ ] **Loading** — black bg, pulsing `loading_god`, exact message per flow, infinite reverse pulse 1100ms.
- [ ] **Photo result (ask)** — photo bottom-cropped at 60% viewport height, gradient overlay, title/subtitle/buttons text and colors, ripple + scale-in.
- [ ] **Photo result (angered)** — error god composite (`error_god` + `error_god_top`), wraith pulse 1100ms, correct buttons.
- [ ] **Photo result (approves)** — blessed image, "Tap for full photo" strip (accent, alpha .3, scaleY 700ms).
- [ ] **Photo details** — full-screen, crossfade 1000ms, pinch-zoom + pan (zoom disabled until loaded), close button, fade transitions.
- [ ] **Snackbars** — all six messages, correct triggers.
- [ ] **Back behavior** — Main returns to GetMeal before exiting; details screen pops.

---

## 4. Risks & mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| ML Kit vs Vision label vocabularies differ → different `isMeal` outcomes on iOS | Medium | Keep matching logic shared; during Phase 10 collect label outputs from a sample image set on both platforms and, if needed, extend the whitelist **additively** (documented decision, no behavior regression on Android). |
| WebView on iOS (no shared API) | Medium | `PlatformWebView` actuals; verify both pages render + fallback HTML works; keep JS disabled unless required (originals enabled JS). |
| Pinch-zoom parity with `PhotoView` | Medium | Implement bounds-aware pan/zoom with `transformable`; validate min/max scale and double-tap if present. |
| EXIF edge cases (transpose/transverse are no-ops today) | Low | Replicate current behavior exactly (including no-op cases); unit-test a matrix of orientations. |
| Pixel-perfect watermark placement | Medium | Pure-math use case + a golden-image unit test asserting pixel coordinates of the watermark corner. |
| Material3 vs legacy Material look | Medium | Restyle components (buttons, checkbox, snackbar, tabs, cards) explicitly; compare screenshots side-by-side in Phase 10. |
| CMP vector XML support / asset fidelity | Low | CMP 1.12 reads XML vector drawables from `composeResources/drawable`; verify each asset renders; fall back to PNG export if any asset fails. |
| `xcode-select` points at CommandLineTools | Blocks iOS builds | Phase 0 one-time `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`. |
| File-name timestamps locale | Low | Use `kotlinx-datetime` with fixed pattern, locale-independent. |

---

## 5. Reference tables

### 5.1 Constants reference (source: `constants/Constants.java`)

Tags: `UI_TAG`, `API_TAG`, `APP_TAG`, `CAMERA_TAG`, `BLESS_TAG` → become logger tags in `core/Log`.
Photo resize enums: `LANDSCAPE_RESIZE=0`, `PORTRAIT_RESIZE=1`.
Keys: `PHOTO_URI_KEY`, `LOADING_MESSAGE_KEY`, `MEAL_PHOTO_OBJECT_KEY`, `DISCLAIMER_TNC_KEY`.

### 5.2 Models

- `MealPhoto { photoUri: String?, title, subTitle, leftButtonText, rightButtonText }` — originally held **string resource ids**. New design: hold semantic tokens (e.g., `MealContent` sealed type: `Asks`, `Approves`, `Angered`) so ViewModels stay platform-agnostic and Compose resolves `Res.string.*`.
- `MealResponse { status, isMeal }` → simplify to `isMeal: Boolean` (status was a Clarifai leftover, but keep a `FoodClassificationResult(labels, isMeal)` if useful for logging).
- `Status { code, description }` → dropped (unused).

### 5.3 String inventory (port verbatim)

`app_name, capture_meal_text, from_file_meal_text, not_a_meal_text, do_you_want_to_text, let_me_see_text, blessing_photo_text, loading_text, yes_text, no_text, share_text, done_text, your_meal_is_blessed_text, share_meal_text, his_grace_approves_text, his_grace_angered_text, his_grace_asks_text, feel_wraith_text, another_try_text, no_photo_was_selected, no_photo_was_taken, permission_not_granted_text, no_internets, disclaimer_title, terms_and_conditions_title, next_step_text, exit_app_text, enter_app_text, i_agree_text, interrupted_text, something_went_wrong_text, transition_name, tap_for_full_text`.

### 5.4 Behavior quirks to preserve

- `onIsMealResponseFailure` message differs by build type (raw error in debug, generic in release).
- Interruption effect only fires when a task was actually in-flight.
- Camera file is created **before** launching the camera and its path is reused on result.
- Gallery selection is **copied** into app storage (not referenced by content URI).
- Blessed output is always JPEG q100 regardless of input format.
- Portrait-only; no RTL (`supportsRtl=false`).
- `allowBackup=false`.
- `installLocation="auto"` (Android manifest) — carry over to the new Android manifest.

---

## 6. Phases

> Each phase: **Goal → Tasks → Deliverable → Verification → Acceptance**.
> Later phases must not regress earlier verification.

### Phase 0 — Environment & prerequisites

**Goal:** Guarantee the toolchain can build both targets before scaffolding.

**Tasks**
1. Verify JDK 17+ (`java -version`) — Temurin 17.0.20.1 present.
2. Verify Android SDK + `ANDROID_HOME`/`local.properties`; confirm an emulator/AVD or device.
3. Switch Xcode: `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`, then `xcodebuild -version` and `xcrun simctl list devices`.
4. Install/verify CocoaPods if needed (only if the chosen setup uses it; prefer direct framework embedding to avoid it).
5. Confirm Kotlin/Native prebuilts download works (network) — `~/.konan` already has 2.2–2.4.x.

**Deliverable:** A short `docs/env-check.md` (or README section) capturing versions and any one-time commands executed.

**Verification:** `xcodebuild -version` prints a version; `xcrun simctl list devices available` lists at least one iOS simulator; Android SDK path resolves.

**Acceptance:** Both platforms have a working build path from the CLI.

---

### Phase 1 — Project creation (scaffold + hello world on both platforms)

**Goal:** A building KMP + Compose app that runs on Android and iOS.

**Tasks**
1. Create Gradle scaffold: `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties` (AndroidX, Kotlin/Native flags), wrapper (pin Gradle version required by CMP 1.12 / AGP), `gradle/libs.versions.toml`.
2. Create `composeApp` module: `kotlin("multiplatform")` + `org.jetbrains.compose` + `org.jetbrains.kotlin.plugin.compose` + `com.android.application`; targets `androidTarget()`, `iosArm64()`, `iosSimulatorArm64()`; framework `baseName = "ComposeApp"`, `isStatic = true`.
3. Source sets: `commonMain`, `androidMain`, `iosMain`, `commonTest`; add core deps (compose runtime/foundation/material3/ui, lifecycle-viewmodel-compose, coroutines, koin, coil3, multiplatform-settings, kotlinx-datetime).
4. Android manifest: `applicationId = "com.grace.app"`, minSdk 21, targetSdk 34, `MainActivity` launcher, portrait, `allowBackup=false`, `installLocation=auto`, permissions (CAMERA, READ_EXTERNAL_STORAGE maxSdk 32, READ_MEDIA_IMAGES, INTERNET/network state), `<queries>` for camera/pick, FileProvider with `${applicationId}.provider` + `file_paths.xml`.
5. iOS: `iosApp` Xcode project (SwiftUI `App` hosting the Compose `UIViewController`), framework search/build settings, Info.plist (`NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription`, portrait only, `UILaunchScreen`).
6. `App.kt`: minimal root composable; `MainActivity` (Android) and `MainViewController` (iOS) entry points.
7. `.gitignore`, `README.md` (build/run instructions), `git init` (do **not** commit unless asked).

**Deliverable:** Scaffold that compiles.

**Verification**
- `./gradlew :composeApp:assembleDebug`
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` (or `embedAndSignAppleFrameworkForSimulator`)
- Run on Android emulator + iOS simulator; a placeholder screen renders.

**Acceptance:** Blank app launches on both platforms.

---

### Phase 2 — Resources & theme migration

**Goal:** All original assets, strings, colors, fonts, and dimensions available to shared Compose code; a theme that matches the original look.

**Tasks**
1. Copy fonts to `composeApp/src/commonMain/composeResources/font/` (`cabin_regular.ttf`, `cabin_bold.ttf`).
2. Copy PNGs to `composeResources/drawable/` (`watermark_blessed_transparent.png`, `watermark_circle.png`, `watermark_portrait.png`, `god_cloud_splash.png`).
3. Copy vector XML drawables to `composeResources/drawable/` (all listed in §1.5). Verify CMP renders each; if any fails, export a PNG fallback.
4. Copy `empty_page.html` to `composeResources/files/`.
5. Migrate launcher icons: Android `mipmap-*/ic_launcher.png`; iOS `AppIcon` asset catalog (all required sizes).
6. Port strings verbatim to `composeResources/values/strings.xml`; generate `Res` accessors (`compose.resources` codegen).
7. Create `ui/theme/`: `Color.kt` (Primary/PrimaryDark/Accent/TransparentBlack), `Type.kt` (Cabin Regular/Bold via `Font(Res.font.*)`), `Dimens.kt` (values from `values/dimens.xml`), `GraceTheme.kt`.
8. Build a temporary "asset gallery" screen listing every migrated drawable + string to eyeball fidelity.

**Deliverable:** Resources + theme module compiling and rendering.

**Verification:** `./gradlew :composeApp:assembleDebug`; run asset gallery on Android; spot-check on iOS simulator.

**Acceptance:** Every original asset renders; no missing string/font warnings.

---

### Phase 3 — Core shared infrastructure

**Goal:** App shell, DI, settings, navigation, file store, logging, connectivity — no feature UI yet.

**Tasks**
1. Koin setup: `appModule`, `platformModule` (expect/actual registration), start Koin in Android `Application` and iOS entry point.
2. `data/SettingsStore` over `multiplatform-settings` exposing `disclaimerTncAccepted` (get/set) and `MutableStateFlow`.
3. `data/GraceFileStore` (expect/actual):
   - Android: `getExternalFilesDir(DIRECTORY_PICTURES)/GraceApp` with `cacheDir` fallback.
   - iOS: `Documents/GraceApp`.
   - API: `newCameraFile(): String`, `newBlessedFile(): String`, `newPickedFile(ext): String`.
4. `core/GraceFileNames` (pure): prefixes + `yyyyMMdd_HHmmss` via `kotlinx-datetime`; unit tests.
5. `core/Log` expect/actual (Logcat / NSLog) with the 5 tags.
6. `platform/ConnectivityObserver` expect/actual → `Flow<Boolean>`.
7. `ui/navigation/`: `Screen` sealed interface (`GetMeal`, `Loading(uri, message)`, `Photo(content)`, `PhotoDetails(uri)`), `Navigator` (ordered stack, `push`/`replace`/`pop`/`popToGetMeal`, back handling), `AppNavHost` composable.
8. `App.kt`: theme + `AppNavHost` + snackbar host scaffold; `ui/components/GraceSnackbarHost`.
9. `GraceViewModel` base (dispatchers, effect channel helper).

**Deliverable:** Navigable shell with placeholder screens for each `Screen`.

**Verification:** `./gradlew :composeApp:assembleDebug` + `:composeApp:testDebugUnitTest`; run on both platforms and navigate between placeholders.

**Acceptance:** Placeholder navigation works; DI graph resolves; file store unit tests pass.

---

### Phase 4 — Domain & data (pure Kotlin, fully tested)

**Goal:** All business logic ported with exact values and covered by unit tests — no UI.

**Tasks**
1. `domain/model/`: `MealContent` sealed type (`Asks`, `Approves`, `Angered`), `FoodClassificationResult`, `GraceConstants` (§3.1).
2. `domain/food/FoodLabelMatcher`: exact + substring match, `Locale`-insensitive lowercasing; tests covering exact, substring ("fast food"), no-match, empty, case variants.
3. `domain/photo/JpegExifReader`: parse JPEG APP1/Exif orientation (pure Kotlin bytes); tests for all 8 orientations + missing Exif.
4. `domain/photo/ImageTransforms`: `orient(bitmap, orientation)` replicating the original switch (incl. transpose/transverse no-ops), `resizeSquare`, `cropBottomToViewport`.
5. `domain/photo/BlessPhotoUseCase`: load photo → orient → load watermark → size = `min(w,h)/3` → draw at `(wmW/12, H - wmH - wmH/12)` → JPEG q100 → file store. Skiko-based.
6. `domain/photo/CropToViewportUseCase`: `width=min(viewportW,bmpW)`, bottom-anchored crop.
7. `domain/photo/PhotoRepository` (interface) + `data/SkikoPhotoRepository` (decode/encode via Skiko, uses `GraceFileStore`).
8. `platform/FoodClassifier` expect + `classify(bitmap): FoodClassificationResult` (actuals in Phase 5); shared matcher applied in `domain/food/IsPhotoOfMealUseCase`.
9. `commonTest`: matcher, EXIF, transforms math, blessing geometry (assert watermark corner pixel coords), file naming, crop math.

**Deliverable:** Green unit-test suite for all domain logic.

**Verification:** `./gradlew :composeApp:testDebugUnitTest` (and `allTests` if configured) — all pass.

**Acceptance:** Every value in §1.4 has a corresponding passing test.

---

### Phase 5 — Platform services (expect/actual)

**Goal:** Real platform integrations behind the interfaces defined earlier.

**Tasks**
1. `platform/PhotoPicker` (expect/actual):
   - Android: `ACTION_IMAGE_CAPTURE` with `FileProvider` `EXTRA_OUTPUT` (pre-create file), `ACTION_PICK` for gallery; copy picked image into app storage with MIME-derived extension; runtime permissions (CAMERA; READ_MEDIA_IMAGES on 33+, READ_EXTERNAL_STORAGE ≤32); result as a `suspend`/callback returning a path or `null` (cancelled).
   - iOS: `UIImagePickerController` (camera) + `PHPickerViewController` (gallery); copy into app storage; permission prompts via Info.plist strings.
2. `platform/FoodClassifier` actuals:
   - Android: ML Kit `image-labeling` with `0.70` threshold; return labels.
   - iOS: Vision `VNClassifyImageRequest`; return labels.
   - Both feed the shared matcher.
3. `platform/ShareService` (expect/actual): Android `ACTION_SEND` (`image/jpg`, FileProvider URI, chooser title `share_meal_text`); iOS `UIActivityViewController`.
4. `platform/PlatformWebView` (expect/actual composable): Android `WebView` (JS on, overview mode, chrome client, scrollbars overlay) + iOS `WKWebView`; expose `onLoaded`/`onError`; error → load bundled `empty_page.html`.
5. `platform/SystemUi` (expect/actual): set status bar color; keep portrait; no-RTL.
6. `platform/ConnectivityObserver` actuals (Android `ConnectivityManager`; iOS `NWPathMonitor`).
7. `ui/components/`: `TypingIndicator` (3 dots), `AutoSizeText`, ripple-enabled rounded button, `TapFullStrip`, `ErrorGodView`.

**Deliverable:** Platform services compiling and individually smoke-tested.

**Verification:** `./gradlew :composeApp:assembleDebug`; iOS `linkDebugFrameworkIosSimulatorArm64`; manual probe screen to pick a photo, classify a known food image, share a file, open a URL in the WebView.

**Acceptance:** Each service performs its function on both platforms.

---

### Phase 6 — Feature: Splash

**Goal:** Splash screen matches the original timing/animation and lands on Main.

**Tasks**
1. `ui/splash/SplashScreen` + `SplashViewModel` (or stateless with a `LaunchedEffect`).
2. Reproduce the frame animation: `clouds_0..8` with first frame 300ms, rest 100ms, one-shot; then logo alpha `0.1→1.0` and `Y` translate from bottom to `height/2 - height/5` over 1200ms; hold 1100ms; navigate to `GetMeal` with a 1000ms crossfade.
3. Status bar `Primary` background; portrait.

**Verification:** Side-by-side recording against the original app (`apk/app-release.apk`).

**Acceptance:** Visual/timing parity; transitions into GetMeal.

---

### Phase 7 — Feature: GetMeal + Disclaimer/TnC dialog

**Goal:** Entry screen and the first-run TnC gate.

**Tasks**
1. `ui/getmeal/GetMealScreen` + ViewModel: two cards (margins/radius/elevation from dimens), ripple, centered icons, bottom all-caps labels, 500ms scale-in; status bar `PrimaryDark`.
2. Wire "Capture Meal"/"Meal From Gallery" → `PhotoPicker` via ViewModel actions → on success `Navigator.replace(Loading(uri, let_me_see))`; cancelled/failed → snackbar (`no_photo_was_taken` / `no_photo_was_selected` / `permission_not_granted_text`).
3. `ui/tnc/TncDialog` + ViewModel: 2-page `HorizontalPager` + dot indicator; page 0 title "Disclaimer"/action "Next" (arrow), checkbox hidden; page 1 title "Privacy Policy"/action "OK" (disabled until checked), checkbox visible; Exit → finish; OK → persist `disclaimer_tnc_key`, dismiss.
4. `PlatformWebView` per page (URLs from §3.1) + `TypingIndicator` while loading + bundled error page on failure.
5. Show dialog on first GetMeal entry when `disclaimerTncAccepted == false`.

**Verification:** Fresh install → dialog appears once, pages load, checkbox gating works, acceptance persists across restarts; capture/gallery paths reach the Loading placeholder.

**Acceptance:** Behavior identical to §1.4.

---

### Phase 8 — Features: Loading, Photo result, Photo details

**Goal:** The classify → bless → share core, plus the full-screen viewer.

**Tasks**
1. `ui/loading/LoadingScreen` + ViewModel: pulsing `loading_god` (1100ms reverse infinite), message from route args; on start run the action:
   - message == `let_me_see_text` → `IsPhotoOfMealUseCase` → success builds `MealContent.Asks`/`Angered`; failure → snackbar + back to GetMeal.
   - else → `BlessPhotoUseCase` → success `MealContent.Approves`; failure → snackbar + GetMeal.
   - Emit `UiEffect.Interrupted` if a running task is cancelled.
2. `ui/photo/PhotoScreen` + ViewModel:
   - Photo area 60%: bottom-cropped image, `black_gradient` overlay (alpha from dimens), error god composite for `Angered`, tap-full strip (accent, alpha .3, scaleY 700ms).
   - Panel 40%: title (accent, caps), `AutoSizeText` subtitle, two rounded ripple buttons with 500ms scale-in.
   - Button matrix per `MealContent` (§1.4): No→GetMeal; Yes→`Loading(blessing_photo_text)`; Done→GetMeal; Share→`ShareService`; Feel the wraith→wraith pulse (1100ms); Give it another try→GetMeal.
   - Tap photo → `Navigator.push(PhotoDetails(uri))` with fade.
3. `ui/photodetails/PhotoDetailsScreen`: full-screen translucent, Coil crossfade 1000ms, pinch-zoom + pan enabled after load, close button, back pops with fade.

**Verification:** End-to-end on both platforms: food image → Asks → Yes → Approves → Share; non-food → Angered → wraith/try-again; zoom viewer opens/closes.

**Acceptance:** All three `MealContent` states and the viewer match the originals.

---

### Phase 9 — Journey integration & system behaviors

**Goal:** The complete journey plus the cross-cutting behaviors (snackbars, connectivity, back handling).

**Tasks**
1. Wire the full journey through `Navigator`: GetMeal → picker → Loading(check) → Photo(Asks) → Loading(bless) → Photo(Approves) → Share/Details.
2. Back handling: Main back returns to GetMeal before exiting; details pops; dialog dismissed.
3. `ConnectivityObserver` → snackbar `no_internets` on loss.
4. Interruption effects surfaced as snackbar `interrupted_text`.
5. `onIsMealResponseFailure` message by build type (debug raw vs release `something_went_wrong_text`).
6. Status bar colors per screen (GetMeal `PrimaryDark`; Photo result black when a photo is present, `PrimaryDark` otherwise).

**Verification:** Run the whole flow on Android + iOS with airplane-mode toggling, permission denials, cancelled pickers, and mid-flow back presses.

**Acceptance:** Every snackbar/effect triggers exactly as in §1.4.

---

### Phase 10 — Parity QA

**Goal:** Prove 1:1 fidelity and lock it with tests.

**Tasks**
1. Side-by-side screenshot comparison (original `apk/app-release.apk` on a device/emulator vs GraceKMP) for every screen/state; store in `docs/parity/`.
2. Golden-image test for the bless output (watermark pixel coordinates + size) using a fixed input photo.
3. EXIF orientation matrix test (all 8 tags) → rendered bitmap comparison.
4. Classifier parity run: sample image set through Android (ML Kit) and iOS (Vision); record labels + decisions; extend whitelist **additively** only if a clear gap is documented.
5. Edge cases: huge images, PNG/GIF inputs, cancelled permission, no Exif, corrupted file, offline.
6. Animation timing verification (recordings vs constants).

**Deliverable:** `docs/parity/REPORT.md` with results and any accepted deviations (must be zero unless explicitly justified).

**Verification:** All parity checks pass or are documented and approved.

**Acceptance:** Visual + behavioral parity confirmed on both platforms.

---

### Phase 11 — Release preparation

**Goal:** Ship-ready builds matching the original release.

**Tasks**
1. App icons + iOS launch screen; Android adaptive icon if desired (keep legacy `ic_launcher` for parity).
2. Versioning parity: `versionName 1.0.4`, `versionCode 1`; iOS `CFBundleShortVersionString 1.0.4`.
3. Android release config: minify + `proguard-rules.pro` equivalent (KMP/Compose defaults), signing config (keystore via env/local properties — never committed).
4. iOS release config: signing team, `Release` framework, archive instructions.
5. README: prerequisites, build/run, phase history, architecture summary.
6. Optional: CI workflow (GitHub Actions) building both targets.

**Verification:** `./gradlew :composeApp:assembleRelease` produces a signed artifact; `xcodebuild -scheme iosApp -configuration Release` archives; install both and run the full journey.

**Acceptance:** Release builds install and behave identically to the original.

---

## 7. Open questions (to resolve during execution)

1. **Bundle/application id** — keep `com.grace.app` (allows an update path over the original Android app) or use a new id (`com.grace.kmp`)? Default assumption: keep `com.grace.app`.
2. **iOS deployment target** — default assumption: iOS 15+ (comfortably supports Vision `VNClassifyImageRequest`, `PHPickerViewController`, SwiftUI host). Confirm if older support is required.
3. **minSdk** — keep 21 (original) or raise? Default assumption: keep 21; if any library forces higher, raise minimally and note it.
4. **Desktop target** — not included; can be added later at low cost since the UI is shared.

---

## 8. Definition of done (whole project)

- [ ] Runs on Android and iOS with identical UI and behavior.
- [ ] Same assets, strings, colors, fonts, and animation timings as the original.
- [ ] Full journey works: capture/pick → classify → bless → share, plus the error path and the TnC gate.
- [ ] Domain logic covered by unit tests (matcher, EXIF, geometry, naming).
- [ ] No dead code carried over.
- [ ] Release builds produced for both platforms.
- [ ] Parity report completed.
