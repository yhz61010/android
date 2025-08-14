/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.leovp.mvvm.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Returns a [ViewModelProvider.Factory] which will return the result of [create] when it's
 * [ViewModelProvider.Factory.create] function is called.
 *
 * If the created [ViewModel] does not match the requested class, an [IllegalArgumentException]
 * exception is thrown.
 */
fun <VM : ViewModel> viewModelProviderFactoryOf(
    create: () -> VM
): ViewModelProvider.Factory = SimpleFactory(create)

/**
 * This needs to be a named class currently to workaround a compiler issue: b/163807311
 */
private class SimpleFactory<VM : ViewModel>(
    private val create: () -> VM
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val vm = create()
        if (modelClass.isInstance(vm)) {
            @Suppress("UNCHECKED_CAST")
            return vm as T
        }
        throw IllegalArgumentException("Can not create ViewModel for class: $modelClass")
    }
}

/**
 * Usage:
 * In your own view model:
 * ```
 * class HomeViewModel(
 *     private val savedStateHandle: SavedStateHandle,
 *     private val useCase: UseCase
 * ) :
 *     BaseViewModel<UiState, BaseAction<UiState>>(
 *         useCase = useCase,
 *         initialState = initialState
 *     ) {
 *     companion object {
 *         val USE_CASE_KEY = object : CreationExtras.Key<UseCase> {}
 *
 *         val Factory: ViewModelProvider.Factory = viewModelFactoryWithSavedState(
 *             key1 = USE_CASE_KEY
 *         ) { savedStateHandle, useCase ->
 *             HomeViewModel(useCase = useCase, savedStateHandle = savedStateHandle)
 *         }
 *     }
 * }
 * ```
 *
 * In your navigation graph or something others:
 * ```
 * composable(route = Screen.Home.route) { backStackEntry ->
 *     val ctx = LocalContext.current
 *     AppTheme {
 *         val homeVM: HomeViewModel = viewModel(
 *             viewModelStoreOwner = backStackEntry,
 *             factory = HomeViewModel.Factory,
 *             extras = backStackEntry.defaultViewModelCreationExtras + MutableCreationExtras().apply {
 *                 set(USE_CASE_KEY, UseCase(RepositoryImpl()))
 *             },
 *         )
 *     }
 * }
 * ```
 */
inline fun <reified VM : ViewModel, reified Param> viewModelFactoryWithSavedState(
    key: CreationExtras.Key<Param>,
    crossinline create: (savedStateHandle: SavedStateHandle, param: Param) -> VM
): ViewModelProvider.Factory {
    return viewModelFactory {
        initializer {
            val savedStateHandle = createSavedStateHandle()
            val param = this[key] ?: error("Missing ViewModel parameter for key: $key")
            create(savedStateHandle, param)
        }
    }
}

/**
 * Usage:
 * In your own view model:
 * ```
 * class YourViewModel(
 *     private val savedStateHandle: SavedStateHandle,
 *     private val useCase: YourUseCase,
 *     initialState: UiState = Loading,
 * ) :
 *     BaseViewModel<UiState, BaseAction<UiState>>(
 *         useCase = useCase,
 *         initialState = initialState
 *     ) {
 *     companion object {
 *         val USE_CASE_KEY = object : CreationExtras.Key<YourUseCase> {}
 *         val TYPE = object : CreationExtras.Key<YourTypeModel> {}
 *
 *         val Factory: ViewModelProvider.Factory = viewModelFactoryWithSavedState(
 *             key1 = USE_CASE_KEY,
 *             key2 = TYPE,
 *         ) { savedStateHandle, useCase, type ->
 *             savedStateHandle["type"] = type
 *             YourViewModel(useCase = useCase, savedStateHandle = savedStateHandle)
 *         }
 *     }
 * }
 * ```
 *
 * In your navigation graph or something others:
 * ```
 * composable(route = Screen.Info.route) { backStackEntry ->
 *     val ctx = LocalContext.current
 *     AppTheme {
 *         val homeVM: YourViewModel = viewModel(
 *             viewModelStoreOwner = backStackEntry,
 *             factory = YourViewModel.Factory,
 *             extras = backStackEntry.defaultViewModelCreationExtras + MutableCreationExtras().apply {
 *                 set(USE_CASE_KEY, YourUseCase(YourRepositoryImpl()))
 *             },
 *         )
 *     }
 * }
 * ```
 */
inline fun <reified VM : ViewModel, reified Param1, reified Param2> viewModelFactoryWithSavedState(
    key1: CreationExtras.Key<Param1>,
    key2: CreationExtras.Key<Param2>,
    crossinline create: (savedStateHandle: SavedStateHandle, param1: Param1, param2: Param2) -> VM
): ViewModelProvider.Factory {
    return viewModelFactory {
        initializer {
            val savedStateHandle = createSavedStateHandle()
            val param1 = this[key1] ?: error("Missing ViewModel parameter for key1: $key1")
            val param2 = this[key2] ?: error("Missing ViewModel parameter for key2: $key2")
            create(savedStateHandle, param1, param2)
        }
    }
}

inline fun <reified VM : ViewModel, reified Param1, reified Param2, reified Param3> viewModelFactoryWithSavedState(
    key1: CreationExtras.Key<Param1>,
    key2: CreationExtras.Key<Param2>,
    key3: CreationExtras.Key<Param3>,
    crossinline create: (savedStateHandle: SavedStateHandle, param1: Param1, param2: Param2, param3: Param3) -> VM
): ViewModelProvider.Factory {
    return viewModelFactory {
        initializer {
            val savedStateHandle = createSavedStateHandle()
            val param1 = this[key1] ?: error("Missing ViewModel parameter for key1: $key1")
            val param2 = this[key2] ?: error("Missing ViewModel parameter for key2: $key2")
            val param3 = this[key3] ?: error("Missing ViewModel parameter for key3: $key3")
            create(savedStateHandle, param1, param2, param3)
        }
    }
}
