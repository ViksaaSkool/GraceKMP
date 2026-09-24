import ComposeApp
import Foundation

#if canImport(GoogleMobileAds)
import GoogleMobileAds
#endif
#if canImport(UserMessagingPlatform)
import UserMessagingPlatform
#endif

/// Bridges Google Mobile Ads + UMP into the shared Kotlin layer.
///
/// Every entry point is guarded by `canImport`, so this file compiles to a no-op until the
/// two Swift packages are added to the Xcode project (see `docs/monetization-setup.md`).
/// While the guard is false `IosMonetizationBridgeHolder.shared.bridge` stays `nil`, the
/// shared consent service reports `canRequestAds = false`, and the app simply never
/// requests an ad — the blessed photo is always shown immediately.
///
/// Deliberately **no** ATT prompt, no `NSUserTrackingUsageDescription`, no IDFA: Grace
/// requests contextual/non-personalized ads only.
final class GraceAdBridge: NSObject, IosAdBridge {

    private var didStart = false

    // MARK: - IosAdBridge

    func start(appId: String, onDone: @escaping (KotlinBoolean) -> Void) {
        #if canImport(GoogleMobileAds)
        guard !didStart else {
            onDone(true)
            return
        }
        didStart = true
        MobileAds.shared.start { _ in
            onDone(true)
        }
        #else
        onDone(false)
        #endif
    }

    func refreshConsent(
        onDone: @escaping (KotlinBoolean, KotlinBoolean) -> Void
    ) {
        #if canImport(UserMessagingPlatform)
        let information = ConsentInformation.shared
        information.requestConsentInfoUpdate(with: RequestParameters()) { _ in
            // A failed refresh still leaves whatever UMP cached last session.
            if information.canRequestAds {
                ConsentForm.loadAndPresentIfRequired(from: nil) { _ in
                    self.publishConsent(information, to: onDone)
                }
            } else {
                self.publishConsent(information, to: onDone)
            }
        }
        #else
        onDone(false, false)
        #endif
    }

    func showPrivacyOptions(
        onDone: @escaping (KotlinBoolean, KotlinBoolean) -> Void
    ) {
        #if canImport(UserMessagingPlatform)
        ConsentForm.presentPrivacyOptionsForm(from: nil) { _ in
            self.publishConsent(ConsentInformation.shared, to: onDone)
        }
        #else
        onDone(false, false)
        #endif
    }

    func preloadInterstitial(unitId: String) {
        #if canImport(GoogleMobileAds)
        let request = Request(adUnitID: unitId)
        InterstitialPrewarmer.shared.prewarm(request)
        #endif
    }

    func showInterstitial(unitId: String, onResult: @escaping (KotlinInt) -> Void) {
        #if canImport(GoogleMobileAds)
        InterstitialAd.load(with: request(for: unitId)) { ad, error in
            guard let ad = ad else {
                onResult(1) // unavailable
                return
            }
            ad.fullScreenContentDelegate = self
            self.pendingResult = onResult
            guard let root = Self.rootViewController() else {
                onResult(2) // failed
                return
            }
            ad.present(fromRootViewController: root)
        }
        #else
        onResult(1) // unavailable: the SDK is not linked yet
        #endif
    }

    // MARK: - Helpers

    #if canImport(UserMessagingPlatform)
    private func publishConsent(
        _ information: ConsentInformation,
        to onDone: (KotlinBoolean, KotlinBoolean) -> Void
    ) {
        let canRequest = information.canRequestAds
        let privacyRequired =
            information.privacyOptionsRequirementStatus == .required
        IosAdConsentBridgeState.shared.canRequestAds = canRequest
        onDone(canRequest, privacyRequired)
    }
    #endif

    #if canImport(GoogleMobileAds)
    private func request(for unitId: String) -> Request {
        Request(adUnitID: unitId)
    }

    private static func rootViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
        let window = scenes.flatMap { $0.windows }.first { $0.isKeyWindow }
        return window?.rootViewController
    }

    private var pendingResult: ((KotlinInt) -> Void)?

    private func settle(_ code: Int) {
        guard let result = pendingResult else { return }
        pendingResult = nil
        result(KotlinInt(int: Int32(code)))
    }
#endif
}

#if canImport(GoogleMobileAds)
extension GraceAdBridge: FullScreenContentDelegate {
    func adDidDismissFullScreenContent(_ ad: FullScreenContentPresenting) {
        settle(0) // displayed and dismissed
    }

    func ad(
        _ ad: FullScreenContentPresenting,
        didFailToPresentFullScreenContentWithError error: Error
    ) {
        settle(2) // failed
    }
}
#endif
