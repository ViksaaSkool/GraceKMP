package com.grace.app.di

import com.grace.app.data.SettingsStore
import com.grace.app.domain.food.IsPhotoOfMealUseCase
import com.grace.app.domain.photo.BlessPhotoUseCase
import com.grace.app.ui.components.GraceSnackbarController
import com.grace.app.ui.getmeal.GetMealViewModel
import com.grace.app.ui.loading.LoadingViewModel
import com.grace.app.ui.navigation.Navigator
import com.grace.app.ui.photo.PhotoViewModel
import com.grace.app.ui.settings.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Shared dependency graph. Replaces Dagger's `AppComponent` + per-screen
 * sub-components + modules (ADR-3).
 */
val appModule: Module = module {

    // App-scoped singletons
    single { Navigator() }
    single { GraceSnackbarController() }
    single { SettingsStore(get()) }

    // Use cases
    factory { IsPhotoOfMealUseCase(get(), get()) }
    factory { BlessPhotoUseCase(get(), get()) }

    // ViewModels (one per screen, mirroring the per-fragment presenter scope)
    viewModel { GetMealViewModel(get(), get(), get(), get(), get()) }
    viewModel { params ->
        LoadingViewModel(
            photoUri = params.get(),
            message = params.get(),
            isPhotoOfMeal = get(),
            blessPhoto = get(),
            navigator = get(),
            snackbar = get()
        )
    }
    viewModel { params ->
        PhotoViewModel(
            content = params.get(),
            photoUri = params.get(),
            navigator = get(),
            shareService = get(),
            snackbar = get(),
            shareChooserTitle = params.get()
        )
    }
    viewModel { params ->
        SettingsViewModel(
            navigator = get(),
            shareService = get(),
            snackbar = get(),
            inviteMessage = params.get(),
            inviteChooserTitle = params.get()
        )
    }
}

/**
 * Platform capabilities: `PhotoPicker`, `FoodClassifier`, `ShareService`,
 * `GraceFileStore`, `ImageProcessor`, `ConnectivityObserver`, `SystemUi`.
 */
expect val platformModule: Module
