# Grace — Data safety

What Grace collects, who receives it, and what the store questionnaires should say.

## At a glance

| Data | Collected | Shared / sold | Purpose |
|---|---|---|---|
| Meal photos (captured/picked) | On device only | Never | Core feature; never uploaded |
| Blessed JPEG | On device only | Never | Core feature; only what you share |
| Purchase state (`remove_ads`) | Yes | RevenueCat | Prevent ads for paying users |
| Anonymous RevenueCat App User ID | Yes | RevenueCat | Purchase ownership/restore |
| Ad identifiers / IDFA | **No** | — | Contextual ads only; Android AD_ID permission removed |
| Ad interaction data | By AdMob | AdMob/Google | Advertising (see below) |
| UMP consent choice | By UMP | UMP/Google | Consent management |
| ML Kit diagnostic data (Android) | By ML Kit | Google | Already disclosed (see Privacy Policy) |

## Purchases

- One-time, non-consumable product `remove_ads_lifetime` via **RevenueCat**, which wraps
  Google Play Billing / Apple StoreKit.
- RevenueCat stores an **anonymous** App User ID (no IDFA, no device fingerprint). It is
  not linked to any account and cannot be used to identify the user.
- Payment processing and refunds are handled entirely by the store (Google Play / App
  Store).
- Restore Purchases re-attaches the entitlement to the store account via RevenueCat's
  "Transfer to new App User ID" behaviour.

## Advertising

- **Contextual / non-personalized / limited ads only.**
- The app **does not request App Tracking Transparency, does not access IDFA, and removes
  the Android AD_ID permission**.
- **UMP consent** is requested via Google's User Messaging Platform on launch. Ads are only
  requested after UMP reports consent may be granted.
- AdMob receives standard ad-serving data (IP, device/app identifiers, ad interactions)
  per Google's policy. This is handled by Google, not by Grace.
- Users can change privacy choices at any time via Settings → Grace Premium → Privacy
  Choices (shown only when UMP requires the entry point).

## Retention

- Photos are retained only for the flow, then deleted locally (see Privacy Policy).
- RevenueCat retains purchase records per its own policy.
- UMP/AdMob consent and advertising data follow Google's retention policies.

## Store questionnaire answers

### Google Play Data Safety (summary)

- **Collected**: app activity (in-app purchase), app info, device or other IDs (purchase
  identifier — not advertising ID), approximate/precise location **no**.
- **Data is transmitted**: yes, to Google (AdMob/ML Kit/UMP) and RevenueCat — but **not**
  sold; ads use consent (non-personalized).
- **Advertising ID**: NOT collected (AD_ID permission removed).

### Apple App Privacy (summary)

- `Purchases` — device ID, app functionality, analytics, **not linked to identity**.
- `Advertising` — **no** (no IDFA, no ATT, non-personalized).
- `Third-party advertising` — AdMob data as above, non-tracking (`NSPrivacyTracking=false`).

Keep this file and the store answers in sync whenever the monetization stack changes.