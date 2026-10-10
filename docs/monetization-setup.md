# Monetization setup

This document is the **required-inputs checklist** for Grace's monetization: Remove Ads
purchases (RevenueCat) + a full-screen AdMob interstitial after every second successful
blessing, with Google UMP consent.

Everything in here is a placeholder until you do the store/console work and fill in the
values. Debug builds are deliberately usable with **Google's official sample AdMob IDs** and
a **dummy purchase backend**, so the whole flow can be developed and demonstrated before any
account exists.

---

## 1. What the app does (current behaviour)

| Scenario | Behaviour |
|---|---|
| Customer has **no** `remove_ads` entitlement | 1st, 3rd, 5th… blessing → result immediately. 2nd, 4th… blessing → preloaded interstitial, then result + a one-shot "Remove Ads" bottom sheet |
| Customer **owns** `remove_ads` | Never loads or shows an ad. Result immediately. |
| Entitlement **unresolved** (offline, SDK not configured, first launch) | Shows the result immediately, does **not** count the blessing, no ad. Fail-safe. |
| Interstitial not cached / no fill / consent denied / offline | Shows the result immediately. An ad failure must never block the blessed photo. |

The decision logic lives in `domain/monetization/` and is fully unit-tested; platform code
only implements the three seams:

- `PurchasesRepository` — RevenueCat (real) or a dummy for development.
- `AdConsentService` — Google UMP.
- `InterstitialAdService` — Google Mobile Ads.

---

## 2.1 Three tiers of RevenueCat backend

`createPurchasesRepository` picks the backend from whatever key Gradle injects, so there is
no runtime switch and no build flavour. Adding a key to `local.properties` is the only
control:

| Key in `local.properties` | Backend | Requires |
|---|---|---|
| absent, or `REPLACE_ME` | `DummyPurchasesRepository` (`$0.99`, instant success) | nothing |
| `test_…` | RevenueCat **Test Store** | a free RevenueCat account only |
| `appl_…` / `goog_…` | the real store sandbox | App Store Connect / Play Console |

Debug builds now read `GRACE_REVENUECAT_KEY_ANDROID` (falling back to the placeholder), so
the real `RevenueCatPurchasesRepository` is reachable without touching a build script.
AdMob still forces Google's sample IDs in debug — a production ad unit must never receive
development traffic.

### Test Store setup (recommended for development)

Test Store exercises the **real** `RevenueCatPurchasesRepository`: real `CustomerInfo`, real
entitlement resolution, real dashboard rows — with no store accounts. The SDK shows a modal
where you can simulate success, failure, or cancellation. Requires KMP SDK ≥ 2.2.2; this
repo pins 3.10.1.

1. RevenueCat dashboard → project for Grace (Android + iOS apps).
2. **Apps and providers → Test configuration → create Test Store**, copy the `test_…` key.
3. **Product catalog** → create a Test Store product with id `remove_ads_lifetime`.
4. **Offerings** → attach it to the `default` offering as a package.
5. Create the entitlement **`remove_ads`**.
6. `GRACE_REVENUECAT_KEY_ANDROID=test_…` in `local.properties`, rebuild, install.

`Purchases.logLevel` is already set to `DEBUG` in debug builds, so Logcat/Xcode carries the
full SDK trace (product lookup, entitlement resolution, purchase, restore).

> ⚠️ A `test_…` key must never reach a release build — the SDK crashes on purpose when it
> finds one. `preReleaseBuild` refuses it first with a dedicated message.

---

## 2. Placeholder inventory (what to populate)

| Item | Where it lives | Development value | Production value |
|---|---|---|---|
| Android AdMob **app ID** | `composeApp/build.gradle.kts` → `GRACE_ADMOB_APP_ID_ANDROID` + manifest `${adMobAppId}` | `ca-app-pub-3940256099942544~3347511713` (sample) | AdMob console → App settings |
| Android interstitial **unit ID** | `GRACE_ADMOB_INTERSTITIAL_UNIT_ID_ANDROID` + `BuildConfig` | `ca-app-pub-3940256099942544/1033173712` (sample) | AdMob console → Ad units |
| iOS AdMob **app ID** | `iosApp/iosApp/Info.plist` → `GADApplicationIdentifier` | `ca-app-pub-3940256099942544~1458002511` (sample) | AdMob console → App settings |
| iOS interstitial **unit ID** | `Info.plist` → `GraceAdMobInterstitialUnitId` | `ca-app-pub-3940256099942544/1033173712` (sample) | AdMob console → Ad units |
| AdMob **publisher ID** (`app-ads.txt`) | `blessameal.com/app-ads.txt` | TODO comment only | `pub-…` from AdMob |
| RevenueCat **API key** (Android) | `GRACE_REVENUECAT_KEY_ANDROID` | `REPLACE_ME` (purchases disabled → dummy backend) | RevenueCat dashboard → Projects → your project |
| RevenueCat **API key** (iOS) | `Info.plist` → `RevenueCatApiKey` | empty (purchases disabled → dummy backend) | RevenueCat dashboard (same key) |
| RevenueCat **entitlement** | `GraceConstants.ENTITLEMENT_REMOVE_ADS` | `remove_ads` | keep as `remove_ads` |
| RevenueCat **offering** | `GraceConstants.OFFERING_DEFAULT` | `default` | keep as `default` |
| Product ID (both stores) | `GraceConstants.PRODUCT_REMOVE_ADS_LIFETIME` | `remove_ads_lifetime` | keep as `remove_ads_lifetime` |
| Preview price | `DummyPurchasesRepository.DUMMY_LOCALIZED_PRICE` | `$0.99` | n/a — the real price comes from `StoreProduct.price.formatted` |

### How values are injected

**Android** reads `local.properties` (gitignored) or environment variables:

```properties
GRACE_ADMOB_APP_ID_ANDROID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
GRACE_ADMOB_INTERSTITIAL_UNIT_ID_ANDROID=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
GRACE_REVENUECAT_KEY_ANDROID=appl_XXXXXXXXXXXXXXXX
```

Debug always uses the sample IDs (a release value must never leak into development
traffic). **Release builds refuse to assemble** while any value is still a sample or the
key is missing:

```bash
./gradlew :composeApp:assembleRelease
# → "Grace release build refused: monetization is still placeholder-configured."

# Local R8/shrinker verification only — never for a real release:
./gradlew :composeApp:assembleRelease -PallowPlaceholderMonetization=true
```

**iOS** reads `Info.plist`. Replace the three sample/empty values in
`iosApp/iosApp/Info.plist` (`GADApplicationIdentifier`, `GraceAdMobInterstitialUnitId`,
`RevenueCatApiKey`) with the production values. `Config.xcconfig` carries commented
placeholders (`ADMOB_APP_ID_IOS`, `ADMOB_INTERSTITIAL_UNIT_IOS`, `REVENUECAT_API_KEY_IOS`)
that you can wire through a build phase if you prefer environment-driven values over
checked-in plist values.

---

## 3. One-time store + dashboard configuration

### 3.1 RevenueCat

1. Create a project in the RevenueCat dashboard with an **Android** and an **iOS** app
   entry (bundle/package id `com.grace.app`).
2. Grab both **public SDK keys** (`appl_…`). They are public by design — safe to embed.
3. Create the entitlement **`remove_ads`**.
4. Create the offering **`default`** and attach one package with product id
   **`remove_ads_lifetime`**.
5. **Restore behaviour** → set **"Transfer to new App User ID"** (Project settings →
   General). This is what lets a reinstall restore the purchase onto the new anonymous
   App User ID. Grace uses RevenueCat's anonymous ID — no custom `appUserId`, no IDFA, no
   device fingerprint.
6. Never consume the Android product (non-consumable, one-time).

### 3.2 Apple App Store Connect

1. Paid applications agreement, tax and banking.
2. App → In-App Purchases → **Non-Consumable**, product id `remove_ads_lifetime`.
3. Price: the closest localised tier to **USD $0.99**. The app never hardcodes a price —
   it displays whatever the store returns.
4. Sandbox testers (Users and Access → Sandbox Testers).

### 3.3 Google Play Console

1. Payments profile.
2. App → Monetize → Products → **One-time product** `remove_ads_lifetime` at ~$0.99
   (localised tier).
3. Internal testing track + testers for purchase/restore testing.

### 3.4 AdMob + UMP

1. Register **two apps** (Android + iOS) in AdMob and copy their app IDs + one
   interstitial ad unit each.
2. **Privacy & messaging**: create the consent message(s) UMP should show for EEA/UK and
   US states.
3. Copy the **publisher ID** (`pub-…`) for `app-ads.txt`.
4. Age ratings: Grace is **not** child-directed; keep AdMob "audience" settings and the
   store age ratings agreeing (the app never sets child-directed treatment).
5. Apple: the current `SKAdNetworkItems` list is already in `Info.plist` (Google +
   third-party buyers, as of the GMA SDK). Re-verify against the AdMob "SDK" page before
   release.

### 3.5 Consent (UMP)

The app runs the standard Google consent flow: `requestConsentInfoUpdate` on every launch,
then presents a form only when required, then publishes `canRequestAds` /
`privacyOptionsRequired`. Ads are requested **only** after `canRequestAds`.

**Privacy choices**: UMP is configured with publisher-rendered "Privacy Choices", so a
"Privacy Choices" row appears in Settings → Grace Premium only when UMP says it is
required.

---

## 4. Advertising privacy posture (do not change casually)

- **Contextual / non-personalized ads only.** Android sets
  `RequestConfiguration.publisherPrivacyPersonalizationState = DISABLED` before
  initialization; iOS keeps `NSPrivacyTracking=false`.
- **No ad is requested before the SDK is up.** `AdMobBootstrap.awaitInitialized()` /
  `MobileAds.shared.start()` complete before UMP is asked anything — UMP answers from state
  the Mobile Ads SDK owns, and querying it early fails permanently (consent is refreshed once
  per process, so a lost race means no ad for the whole session).
- **Demo ad IDs fall open, production IDs do not.** Google's sample IDs (`…9942544…`) are not
  registered with UMP, so it can never return a decision for them. When
  `MonetizationConfig.usesSampleAdIds` is true *and* UMP's answer is inconclusive (init
  failure, network error, or "no form and still cannot request ads"), the consent services
  publish `canRequestAds = true`. A positive UMP answer is always honoured. With production
  IDs the gate stays strict — inconclusive still means no ad.
- **No ATT.** No `NSUserTrackingUsageDescription`, no `ATTrackingManager`, no IDFA access.
- **Android AD_ID permission is removed** from the merged manifest
  (`tools:node="remove"`). Verify with `./gradlew :composeApp:processReleaseManifest` and
  inspecting `AndroidManifest.xml` for `AD_ID` before submitting to Play.
- **UMP is the only consent store** — the app never persists or interprets consent strings
  itself.

---

## 5. What stays a placeholder vs. what must be done before release

| Item | Must do before release |
|---|---|
| AdMob production IDs (both platforms) | ✅ replace placeholders |
| RevenueCat keys (both platforms) | ✅ replace placeholders |
| `app-ads.txt` with real `pub-…` | ✅ replace the TODO; verify `curl https://blessameal.com/app-ads.txt` |
| Store products live in App Store / Play | ✅ submit `remove_ads_lifetime` |
| RevenueCat restore behaviour = Transfer | ✅ set in dashboard |
| **Privacy Policy** on blessameal.com | ✅ updated (ads, purchases, consent, RevenueCat, billing) |
| **Terms & Conditions** on blessameal.com | ✅ updated (purchase/refund/restore wording) |
| Play **Data Safety** + Apple **App Privacy** answers | ✅ must reflect ads, purchases, identifiers |
| PrivacyInfo.xcprivacy | ✅ added (verify it merges into the built app) |
| `docs/data-safety.md` | ✅ kept in sync |

### Still intentionally placeholders

- **iOS Google Mobile Ads + UMP Swift packages are not yet linked.** The bridge in
  `iosApp/iosApp/GraceAdBridge.swift` compiles to a no-op behind
  `#if canImport(GoogleMobileAds)` / `#if canImport(UserMessagingPlatform)`, so today iOS
  never requests an ad (blessed photos always appear immediately). To activate:

  ```bash
  cd iosApp
  # Xcode → File → Add Package Dependencies…
  #   https://github.com/googleads/swift-package-manager-google-mobile-ads.git
  #   https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git
  ```
  Once linked, the bridge starts itself automatically (no code changes needed).

- **Dummy purchase backend** (`DummyPurchasesRepository`): used while the RevenueCat key is
  a placeholder, reports a `Free` entitlement with a `$0.99` dummy price so the ad cadence
  and prompt can be exercised. Release builds refuse to ship it (see §2).

---

## 6. Testing checklist

### In-app: the debug developer panel

**Debug builds only** — Settings has a **Developer** panel below Grace Premium, gated on
`MonetizationDebug.enabled` (`isDebugBuild`, a compile-time constant `false` in release, so
R8 strips the whole surface). It exists so both flows are verifiable with no store account.

| Row | Effect |
|---|---|
| Force next interstitial | Next blessing is ad-eligible regardless of cadence. Consumed once; does **not** advance the counter, so the real cadence is unaffected. |
| Force Free / Force Purchased | Overrides the entitlement the coordinator sees, whatever the backend reports. Bypasses `DummyPurchasesRepository`, Test Store, and the real repository alike. |
| Clear entitlement override | Defers to the real backend again. |
| Reset blessing counter | `freeBlessingCount = 0`, so the next blessing is #1 and the 2nd is ad-eligible. |
| Simulate reinstall | Clears `removeAdsEntitlementCached` and refreshes — exercises Restore without clearing app data. |
| UMP geography | Cycles `Off → EEA → US → Off`, feeding `ConsentDebugSettings`. |
| Reset all | Every switch plus both persisted values back to zero. |
| Diagnostics rows | Backend tier, entitlement (+ override marker), blessing count, consent state. |

### Why no ad appeared (fixed 2026-10-10)

Three defects, each independently enough to suppress every ad:

1. **`AdMobBootstrap` never initialized the SDK.** It called the static
   `MobileAds.setRequestConfiguration(...)` *before* `MobileAds.initialize(...)`, believing
   that installed the privacy configuration early. In GMA Next-Gen that call requires the SDK
   to already be initialized, so it threw
   `MobileAds.initialize must be called before using the Google Mobile Ads SDK`. Initialization
   was abandoned, and every later call — preloader start, `pollAd`, `load` — failed with the
   same error. The configuration rides along inside `InitializationConfig.Builder` anyway, so
   nothing can be requested ahead of it. **Do not reintroduce the static call.**
2. **Android had no on-demand load path.** `showIfReady()` only polled the preloader, so an
   empty cache returned `Unavailable` on every blessing forever. It now falls back to
   `InterstitialAd.load(...)` and presents that.
3. **Preload ran only at launch**, when consent might not have arrived yet. The coordinator now
   re-preloads as soon as `canRequestAds` turns true.

Debug verification (logcat tag `APPTAG`):

```
Google Mobile Ads initialized (appId=ca-app-pub-3940256099942544~3347511713)
UMP state: canRequestAds=true ... sampleAds=true
Interstitial preload for ca-app-pub-3940256099942544/1033173712: started=true
```

Settings → Developer also shows a **"Last ad attempt"** row (`SkippedCadence`, `Unavailable`,
`Failed`, `Shown`, `Forced`, …), which distinguishes "odd blessing, no ad due" from "eligible
but the SDK had nothing".

> UMP honours `setDebugGeography` only if the app id is registered as a debug app in the
> AdMob console; with Google's sample IDs it may silently stay `Off`. The row shows the
> *requested* value. With sample IDs, "Consent: granted" can also mean "UMP was inconclusive,
> demo inventory allowed anyway" (§4) — check logcat for `UMP state:` to tell which.
>
> iOS shows "iOS ads: Swift packages not linked" — the GMA/UMP SPM packages are still
> absent, so `IosInterstitialAdService` permanently returns `Unavailable` there.

### Suggested manual sequence

| Test | Steps |
|---|---|
| Interstitial appears | Developer → **Force next interstitial** → bless a photo → ad → Remove Ads prompt |
| Every-second cadence | **Reset blessing counter** → bless twice; only the 2nd shows an ad |
| Purchase (dummy or Test Store) | Developer → **Force Free** → Settings → Remove Ads → success; state becomes Purchased |
| Purchase suppresses ads | Then **Force next interstitial** → next blessing shows no ad |
| Restore after reinstall | **Simulate reinstall** → Settings → Restore → `NothingToRestore`; re-purchase, reinstall-simulate, Restore → `Success` |
| Consent form | Developer → UMP geography: **EEA** → relaunch → UMP form appears |

Automated (already in `commonTest`, run on Android JVM + iOS simulator):

- Paid customers never see/load an ad, never increment the counter.
- Unresolved entitlement suppresses ads and counting.
- Free customers get an interstitial only on even-numbered blessings.
- Consent-denied, unavailable and failed ads never block the result.
- Only an actually-dismissed ad triggers the Remove Ads prompt.
- Purchase success activates the entitlement; cancellation is not an error.
- Restore re-activates after "reinstall" (dummy backend + RevenueCat sandbox).
- Versioned legal acceptance: legacy Boolean migrates to v1 and v2 re-prompts.
- `AdFrequencyPolicy`, `SettingsStore`, `MonetizationConfig` are pinned by tests.

Platform (manual). Purchase testing needs no store accounts if you use a Test Store key
(§2.1); these still require the real stores before launch:

- Android Play internal track: purchase → reinstall → Restore.
- iOS StoreKit sandbox / TestFlight: purchase → delete → restore.
- EEA/UK consent simulation (UMP debug geography) and US privacy-options on both platforms.
- Offline blessing (entitlement cache suppresses ad for known-paid users).
- Release: R8 build (`-PallowPlaceholderMonetization=true` locally), Xcode archive, and a
  final merged-manifest/plist inspection for AD_ID, Billing, AdMob app id and privacy
  manifests.

---

## 7. Conceptual constants

| Constant | Value | Meaning |
|---|---|---|
| `ENTITLEMENT_REMOVE_ADS` | `remove_ads` | RevenueCat entitlement |
| `OFFERING_DEFAULT` | `default` | RevenueCat offering |
| `PRODUCT_REMOVE_ADS_LIFETIME` | `remove_ads_lifetime` | Store product id |
| `REQUIRED_POLICY_VERSION` | `2` | Legal docs version customers must accept (bumped for ads/purchases) |
| `DISCLAIMER_TNC_KEY` | `disclaimer_tnc_key` | Legacy Boolean kept for migration |

These live in `core/GraceConstants.kt` with a test each.