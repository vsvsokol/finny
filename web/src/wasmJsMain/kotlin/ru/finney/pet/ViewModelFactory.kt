package ru.finney.pet

import androidx.lifecycle.viewmodel.CreationExtras

/** Как на Android (app/.../ViewModelFactory.kt), только контейнер — один на страницу. */
@Suppress("UnusedReceiverParameter")
fun CreationExtras.appContainer(): AppContainer = AppContainer.instance
