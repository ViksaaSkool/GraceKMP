package com.grace.app.ui.navigation

import com.grace.app.domain.model.MealContent

/**
 * The original app had exactly one fragment container (`main_frame_layout`) and
 * replaced its content, plus one translucent Activity on top for the viewer.
 * This sealed type mirrors that: one current screen, with [PhotoDetails] pushed
 * over [Photo] (ADR-5).
 */
sealed interface Screen {
    data object Splash : Screen
    data object GetMeal : Screen
    data class Loading(val photoUri: String, val message: LoadingMessage) : Screen
    data class Photo(
        val content: MealContent,
        val photoUri: String?,
        /**
         * One-shot flag: `true` when an interstitial was actually presented and closed
         * for the blessing that produced this result. Consumed by `PhotoViewModel` on
         * first composition so the Remove Ads prompt cannot reappear on recreation.
         */
        val showRemoveAdsPrompt: Boolean = false
    ) : Screen
    data class PhotoDetails(val photoUri: String) : Screen

    /** Pushed over [GetMeal]; back pops to it. */
    data object Settings : Screen

    /** A legal document, pushed over [Settings] and rendered in the in-app web view. */
    data class Legal(val page: LegalPage) : Screen
}

/**
 * The legal documents reachable from Settings. Kept as an enum so [Screen]
 * stays free of resource lookups: the title and URL are resolved in the UI.
 */
enum class LegalPage {
    PrivacyPolicy,
    TermsAndConditions,
    Disclaimer
}

/**
 * The loading message was overloaded in the original as a control-flow signal:
 * `LoadingFragment.takePhotoAction()` compared `mLoadingMessage` against
 * `R.string.let_me_see_text` to decide between "classify" and "bless".
 * Modelling it as an enum keeps that decision explicit and type-safe.
 */
enum class LoadingMessage {
    LetMeSee,
    BlessingPhoto
}
