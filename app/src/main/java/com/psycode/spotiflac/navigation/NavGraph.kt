package com.psycode.spotiflac.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.psycode.spotiflac.domain.mode.AppMode
import com.psycode.spotiflac.navigation.Screen
import com.psycode.spotiflac.ui.screen.auth.AuthScreen
import com.psycode.spotiflac.ui.screen.downloads.DownloadManagerScreen
import com.psycode.spotiflac.ui.screen.downloads.DownloadManagerViewModel
import com.psycode.spotiflac.ui.screen.trackdetail.TrackDetailScreen
import com.psycode.spotiflac.ui.screen.trackdetail.TrackDetailViewModel
import com.psycode.spotiflac.ui.screen.tracklist.TrackListScreen
import com.psycode.spotiflac.ui.screen.tracklist.TrackListViewModel
import com.psycode.spotiflac.ui.screen.settings.SettingsScreen

@Composable
fun SpotiFlacNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String,
    externalNavigationRoute: String? = null,
    externalNavigationNonce: Long = 0L
) {
    var lastHandledExternalNavigationNonce by remember { mutableLongStateOf(0L) }
    LaunchedEffect(externalNavigationRoute, externalNavigationNonce) {
        if (externalNavigationRoute.isNullOrBlank()) return@LaunchedEffect
        if (externalNavigationNonce <= lastHandledExternalNavigationNonce) return@LaunchedEffect
        lastHandledExternalNavigationNonce = externalNavigationNonce
        navController.navigate(externalNavigationRoute) {
            launchSingleTop = true
            restoreState = true
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable(route = Screen.Auth.route) {
                AnimatedDestination {
                    AuthScreen(
                        onSpotifyPublicSearch = {
                            navController.navigate(Screen.TrackList.route) {
                                popUpTo(Screen.Auth.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onManualSearch = {
                            navController.navigate(Screen.TrackDetailManual.route) {
                                popUpTo(Screen.Auth.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }

            composable(route = Screen.TrackList.route) { backStackEntry ->
                val vm: TrackListViewModel = hiltViewModel(backStackEntry)
                AnimatedDestination {
                    TrackListScreen(
                        viewModel = vm,
                        onBack = { },
                        onTrackClick = { track ->
                            navController.navigate(Screen.TrackDetail.createRoute(track.id))
                        },
                        onDownloadsClick = {
                            navController.navigate(Screen.Downloads.route)
                        },
                        onSettingsClick = {
                            navController.navigate(Screen.Settings.route)
                        },
                        onLogout = {
                            navController.navigateToAuthClearingBackStack()
                        }
                    )
                }
            }

            composable(
                route = Screen.TrackDetail.route,
                arguments = listOf(navArgument("trackId") { type = NavType.StringType })
            ) { backStackEntry ->
                val vm: TrackDetailViewModel = hiltViewModel(backStackEntry)
                AnimatedDestination {
                    TrackDetailScreen(
                        trackId = backStackEntry.arguments?.getString("trackId").orEmpty(),
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                        onNavigateToDownloads = { _, _ ->
                            navController.navigate(Screen.Downloads.route)
                        },
                        onOpenDownloads = {
                            navController.navigate(Screen.Downloads.route)
                        }
                    )
                }
            }

            composable(route = Screen.TrackDetailManual.route) { backStackEntry ->
                val vm: TrackDetailViewModel = hiltViewModel(backStackEntry)
                val modeBarVm: com.psycode.spotiflac.ui.common.ModeBarViewModel =
                    hiltViewModel(backStackEntry)

                AnimatedDestination {
                    TrackDetailScreen(
                        trackId = "",
                        manualMode = true,
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                        onNavigateToDownloads = { _, _ ->
                            navController.navigate(Screen.Downloads.route)
                        },
                        onOpenDownloads = {
                            navController.navigate(Screen.Downloads.route)
                        },
                        onOpenSettings = {
                            navController.navigate(Screen.Settings.route)
                        },
                        onLogout = {
                            modeBarVm.clearMode()
                            navController.navigateToAuthClearingBackStack()
                        }
                    )
                }
            }

            composable(route = Screen.Downloads.route) {
                val vm: DownloadManagerViewModel = hiltViewModel()
                val modeBarVm: com.psycode.spotiflac.ui.common.ModeBarViewModel = hiltViewModel()
                val mode by modeBarVm.mode.collectAsState()
                AnimatedDestination {
                    DownloadManagerScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                        onOpenTorrentSearch = {
                            val targetRoute = when (mode) {
                                AppMode.SpotifyPublic -> Screen.TrackList.route
                                AppMode.ManualTorrent -> Screen.TrackDetailManual.route
                                AppMode.Unselected -> Screen.Auth.route
                            }
                            navController.navigate(targetRoute) {
                                popUpTo(Screen.Downloads.route) { inclusive = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenSettings = {
                            navController.navigate(Screen.Settings.route)
                        }
                    )
                }
            }

            composable(route = Screen.Settings.route) {
                AnimatedDestination {
                    SettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedDestination(content: @Composable () -> Unit) {
    val visibleState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)) +
                slideInHorizontally(
                    animationSpec = tween(durationMillis = 260),
                    initialOffsetX = { fullWidth -> (fullWidth * 0.06f).toInt() }
                ),
        exit = ExitTransition.None
    ) {
        content()
    }
}

private fun NavHostController.navigateToAuthClearingBackStack() {
    navigate(Screen.Auth.route) {
        popUpTo(graph.findStartDestination().id) {
            inclusive = true
        }
        launchSingleTop = true
        restoreState = false
    }
}


