package com.grace.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Gives each screen its own `ViewModelStoreOwner` so that `koinViewModel()`
 * returns a fresh ViewModel per visit — mirroring how the original created a
 * new fragment + presenter on every `replace()`. Without this, revisiting the
 * Loading screen would reuse the previous ViewModel and its `working` guard
 * would suppress the second run.
 */
@Composable
fun ScreenScope(key: Any, content: @Composable () -> Unit) {
    val owner = remember(key) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}
