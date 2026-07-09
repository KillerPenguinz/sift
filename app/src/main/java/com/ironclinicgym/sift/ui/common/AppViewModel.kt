package com.ironclinicgym.sift.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironclinicgym.sift.SiftApp
import com.ironclinicgym.sift.di.AppContainer

/**
 * Builds a ViewModel from the manual DI [AppContainer] (the Hilt-free path). Keeps ViewModels
 * thin: they receive the container and delegate to the core services within it.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as SiftApp).container
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
