package com.psycode.spotiflac.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class SystemBarOverride(
    val statusBarColorArgb: Int,
    val navigationBarColorArgb: Int,
    val lightStatusBarIcons: Boolean,
    val lightNavigationBarIcons: Boolean
)

object SystemBarOverrideStore {
    var current: SystemBarOverride? by mutableStateOf(null)
}
