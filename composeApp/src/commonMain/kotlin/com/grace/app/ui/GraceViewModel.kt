package com.grace.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/**
 * Replaces `BasePresenter`/`PresenterLoader`/`PresenterFactory` (ADR-2).
 * Exposes the lifecycle-scoped coroutine scope that feature ViewModels use.
 */
abstract class GraceViewModel : ViewModel() {
    protected val scope: CoroutineScope get() = viewModelScope
}
