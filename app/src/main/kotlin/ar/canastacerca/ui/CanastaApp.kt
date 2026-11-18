package ar.canastacerca.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import ar.canastacerca.core.navigation.EntryProviderInstaller
import ar.canastacerca.core.navigation.Navigator
import ar.canastacerca.core.navigation.TopLevelDestination
import ar.canastacerca.core.navigation.rememberNavigationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Корень UI. :app не знает ни одной фичи: вкладки и экраны приходят из графа
 * как Set<TopLevelDestination> и Set<EntryProviderInstaller>.
 */
@Composable
fun CanastaApp(
    destinations: List<TopLevelDestination>,
    installers: Set<EntryProviderInstaller>,
) {
    val topLevelRoutes = remember(destinations) { destinations.map { it.key } }
    val navigationState = rememberNavigationState(topLevelRoutes)
    val navigator = remember(navigationState) { Navigator(navigationState) }
    val entryProvider = remember(installers, navigator) {
        entryProvider<NavKey> {
            installers.forEach { installer -> with(installer) { install(navigator) } }
        }
    }

    Scaffold(
        // Отступы под системные панели делают экраны фич (их TopAppBar и Scaffold)
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    val badgeFlow: Flow<Int> = remember(destination) { destination.badge ?: flowOf(0) }
                    val badge by badgeFlow.collectAsStateWithLifecycle(initialValue = 0)
                    NavigationBarItem(
                        selected = navigationState.topLevelRoute == destination.key,
                        onClick = { navigator.selectTopLevel(destination.key) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (badge > 0) Badge { Text(badge.toString()) }
                                },
                            ) {
                                Icon(painterResource(destination.icon), contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavDisplay(
            entries = navigationState.toDecoratedEntries(entryProvider),
            onBack = { navigator.goBack() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }
}
