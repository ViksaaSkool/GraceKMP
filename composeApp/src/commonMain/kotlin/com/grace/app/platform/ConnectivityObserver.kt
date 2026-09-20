package com.grace.app.platform

import kotlinx.coroutines.flow.Flow

/**
 * Replaces `BaseActivity.NetworkStateReceiver`. Emits the current connectivity
 * on subscription and then on every change. `Main2Activity` showed the
 * `no_internets` snackbar whenever the value became false.
 */
interface ConnectivityObserver {
    fun observe(): Flow<Boolean>
}
