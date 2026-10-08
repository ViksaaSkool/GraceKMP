import ComposeApp
import Foundation

/// One-shot wiring of the Swift monetization bridge into the shared Kotlin layer.
///
/// Runs before `MainViewController()` is created so the first
/// `MonetizationCoordinator.start()` observes the installed bridge (or `nil` while the
/// Google packages are still unlinked, which is the safe "no ads" state).
enum GraceMonetizationBootstrap {

    private static var installed = false

    static func installIfNeeded() {
        guard !installed else { return }
        installed = true

        // Placeholder-aware: while RevenueCat is unset this keeps the dummy repository,
        // and the AdMob IDs stay Google's samples. Production values arrive through
        // `IosMonetizationBuildConfig.configure(...)` — see docs/monetization-setup.md.
        IosMonetizationBuildConfig.shared.configure(
            appId: Bundle.main.object(forInfoDictionaryKey: "GADApplicationIdentifier")
                as? String ?? "",
            interstitialUnitId:
                Bundle.main.object(forInfoDictionaryKey: "GraceAdMobInterstitialUnitId")
                as? String ?? "",
            revenueCatApiKey:
                Bundle.main.object(forInfoDictionaryKey: "RevenueCatApiKey") as? String
        )

        IosMonetizationBridgeHolder.shared.bridge = GraceAdBridge()
    }
}
