package com.grace.app.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Replaces `ChangeFragmentHelper` + `ChangeActivityHelper` (ADR-5).
 *
 * The original used fragment `replace()` — there was no back stack, only the
 * single special case in `Main2Activity.onBackPressed`: if GetMeal was not the
 * current screen, go back to GetMeal; otherwise exit. [PhotoDetails] was a
 * separate Activity, so it *did* stack over the photo screen. Both behaviours
 * are reproduced here.
 */
class Navigator(initial: Screen = Screen.Splash) {

    private val _current = MutableStateFlow(initial)
    val current: StateFlow<Screen> = _current.asStateFlow()

    private val stack = ArrayDeque<Screen>()

    /** Fragment `replace()`: the current screen is discarded. */
    fun replace(screen: Screen) {
        stack.clear()
        _current.value = screen
    }

    /** Activity `startActivity()`: the current screen stays underneath. */
    fun push(screen: Screen) {
        stack.addLast(_current.value)
        _current.value = screen
    }

    /** Returns false when there is nothing to pop, so the caller can exit. */
    fun pop(): Boolean {
        val previous = stack.removeLastOrNull() ?: return false
        _current.value = previous
        return true
    }

    /**
     * `Main2Activity.onBackPressed`: any screen other than GetMeal (or Splash)
     * is replaced by GetMeal; on GetMeal the back press is passed through to the
     * system.
     */
    fun backToGetMeal(): Boolean {
        val current = _current.value
        if (current is Screen.GetMeal || current is Screen.Splash) return false
        stack.clear()
        _current.value = Screen.GetMeal
        return true
    }
}
