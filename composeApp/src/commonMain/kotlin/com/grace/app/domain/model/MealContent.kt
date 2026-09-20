package com.grace.app.domain.model

/**
 * Semantic replacement for the original `MealPhoto` model, which carried raw
 * Android string-resource ids. Keeping the state semantic lets the ViewModels
 * stay platform-agnostic while Compose resolves `Res.string.*`.
 *
 * Mapping (LoadingFragment.java:146-193, PhotoFragment.java:215-262):
 *  - [Asks]     → photo of a meal, offer to bless it (No / Yes)
 *  - [Approves] → blessed photo (Done / Share)
 *  - [Angered]  → not a meal (Feel the wraith / Give it another try)
 */
enum class MealContent {
    Asks,
    Approves,
    Angered;

    val hasPhoto: Boolean get() = this != Angered

    /** PhotoFragment.handlePhotoClick: the viewer opens for every state but Angered. */
    val photoIsZoomable: Boolean get() = this != Angered

    /** PhotoFragment.handleLeftButtonClick */
    val leftIsDone: Boolean get() = this == Approves
    val leftIsNo: Boolean get() = this == Asks
    val leftIsFeelWraith: Boolean get() = this == Angered

    /** PhotoFragment.handleRightButtonClick */
    val rightIsYes: Boolean get() = this == Asks
    val rightIsShare: Boolean get() = this == Approves
    val rightIsAnotherTry: Boolean get() = this == Angered
}
