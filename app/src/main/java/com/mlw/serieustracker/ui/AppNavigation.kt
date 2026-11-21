package com.mlw.serieustracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mlw.serieustracker.ui.screens.DetailScreen
import com.mlw.serieustracker.ui.screens.MainScreen
import com.mlw.serieustracker.ui.screens.SearchScreen
import com.mlw.serieustracker.ui.screens.SettingsScreen
import com.mlw.serieustracker.ui.theme.findActivity

private const val MAIN = "main"
private const val SEARCH = "search"
private const val SETTINGS = "settings"
private const val SHOW = "show"

@Composable
fun AppNavigation(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val density = LocalDensity.current
    val navigationBarHeight = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    BlackNavigationBarStyle()

    // The themed surface keeps the background correct while screens animate (no white flash in dark mode).
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // Screens end above the system navigation bar (buttons or gesture handle), so nothing, such as the
            // highlighted tab, is drawn underneath it. The inset is consumed here so screens do not pad for it twice.
            Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.navigationBars)) {
                NavHost(navController = nav, startDestination = MAIN) {
                    composable(MAIN) {
                        MainScreen(
                            vm = vm,
                            onSearch = { nav.navigate(SEARCH) },
                            onSettings = { nav.navigate(SETTINGS) },
                            onOpenShow = { key -> nav.navigate("$SHOW/$key") },
                        )
                    }
                    composable(SEARCH) { SearchScreen(vm, onBack = { nav.popBackStack() }) }
                    composable(SETTINGS) { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
                    composable("$SHOW/{key}", arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                        DetailScreen(vm, key = entry.arguments?.getString("key").orEmpty(), onBack = { nav.popBackStack() })
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(navigationBarHeight).background(Color.Black))
        }
    }
}

/** The strip under the screens is black, so the navigation buttons or gesture handle must be drawn in light colours. */
@Composable
private fun BlackNavigationBarStyle() {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
    }
}
