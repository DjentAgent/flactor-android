package com.psycode.spotiflac

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.psycode.spotiflac.domain.mode.AppMode
import com.psycode.spotiflac.domain.usecase.mode.ObserveAppModeUseCase
import com.psycode.spotiflac.navigation.Screen
import com.psycode.spotiflac.navigation.SpotiFlacNavGraph
import com.psycode.spotiflac.ui.common.SystemBarOverrideStore
import com.psycode.spotiflac.ui.theme.SpotiflacTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var observeAppModeUseCase: ObserveAppModeUseCase

    private var externalNavNonce = 0L
    private val externalNavRequestState = mutableStateOf<ExternalNavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        externalNavRequestState.value = parseExternalNavRequest(intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ) { isDarkSystem() },
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ) { isDarkSystem() }
        )

        val ic = WindowCompat.getInsetsController(window, window.decorView)
        ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val modeBootstrap by produceState<ModeBootstrapState>(
                initialValue = ModeBootstrapState.Loading
            ) {
                observeAppModeUseCase().collect { mode ->
                    value = ModeBootstrapState.Ready(mode)
                }
            }

            val resolvedStartDestination = when (val state = modeBootstrap) {
                ModeBootstrapState.Loading -> null
                is ModeBootstrapState.Ready -> when (state.mode) {
                    AppMode.Unselected -> Screen.Auth.route
                    AppMode.SpotifyPublic -> Screen.TrackList.route
                    AppMode.ManualTorrent -> Screen.TrackDetailManual.route
                }
            }

            var fixedStartDestination by rememberSaveable { mutableStateOf<String?>(null) }
            LaunchedEffect(resolvedStartDestination) {
                if (fixedStartDestination == null && resolvedStartDestination != null) {
                    fixedStartDestination = resolvedStartDestination
                }
            }

            SpotiflacTheme {
                ApplyEdgeToEdgeFromTheme()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    fixedStartDestination?.let { start ->
                        val externalNavRequest = externalNavRequestState.value
                        SpotiFlacNavGraph(
                            startDestination = start,
                            externalNavigationRoute = externalNavRequest?.route,
                            externalNavigationNonce = externalNavRequest?.nonce ?: 0L
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalNavRequestState.value = parseExternalNavRequest(intent)
    }

    override fun onResume() {
        super.onResume()
        val ic = WindowCompat.getInsetsController(window, window.decorView)
        ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun isDarkSystem(): Boolean {
        val uiMode = resources.configuration.uiMode
        val nightMask = uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun parseExternalNavRequest(intent: Intent?): ExternalNavRequest? {
        val route = intent?.getStringExtra(EXTRA_OPEN_SCREEN_ROUTE)?.takeIf { it.isNotBlank() }
            ?: return null
        externalNavNonce += 1
        return ExternalNavRequest(route = route, nonce = externalNavNonce)
    }

    private data class ExternalNavRequest(
        val route: String,
        val nonce: Long
    )

    companion object {
        const val EXTRA_OPEN_SCREEN_ROUTE = "open_screen_route"
    }
}

private sealed interface ModeBootstrapState {
    data object Loading : ModeBootstrapState
    data class Ready(val mode: AppMode) : ModeBootstrapState
}

@Composable
private fun ApplyEdgeToEdgeFromTheme() {
    val dark = isSystemInDarkTheme()
    val activity = LocalContext.current as ComponentActivity
    val barsOverride = SystemBarOverrideStore.current
    SideEffect {
        if (barsOverride == null) {
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                ) { dark },
                navigationBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                ) { dark }
            )
        } else {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.statusBarColor = barsOverride.statusBarColorArgb
            activity.window.navigationBarColor = barsOverride.navigationBarColorArgb
            val ic = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            ic.isAppearanceLightStatusBars = barsOverride.lightStatusBarIcons
            ic.isAppearanceLightNavigationBars = barsOverride.lightNavigationBarIcons
            ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }
    }
}
