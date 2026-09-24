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

Platform (manual, requires store accounts):

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