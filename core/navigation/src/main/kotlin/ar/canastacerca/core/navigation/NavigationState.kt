package ar.canastacerca.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * Состояние навигации с отдельным back stack на каждую вкладку (рецепт «multiple back stacks» Navigation 3).
 *
 * - Стеки переживают поворот экрана и смерть процесса (rememberNavBackStack + @Serializable ключи).
 * - У каждого стека свои декораторы: при переключении вкладок не теряются ни ViewModel, ни скролл.
 * - Выход из приложения всегда через стартовую вкладку («exit through home»).
 */
@Stable
class NavigationState internal constructor(
    val topLevelRoutes: List<NavKey>,
    private val currentIndex: MutableIntState,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    val startRoute: NavKey get() = topLevelRoutes.first()

    var topLevelRoute: NavKey
        get() = topLevelRoutes[currentIndex.intValue.coerceIn(topLevelRoutes.indices)]
        set(value) {
            val index = topLevelRoutes.indexOf(value)
            if (index >= 0) currentIndex.intValue = index
        }

    fun stackOf(route: NavKey): NavBackStack<NavKey> =
        backStacks[route] ?: error("Нет back stack для $route")

    /** Ключи → NavEntry с декораторами. В списке — стартовая вкладка и, если выбрана другая, она поверх. */
    @Composable
    fun toDecoratedEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val decorated = backStacks.mapValues { (_, stack) ->
            val decorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>(),
            )
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = decorators,
                entryProvider = entryProvider,
            )
        }
        val inUse = if (topLevelRoute == startRoute) listOf(startRoute) else listOf(startRoute, topLevelRoute)
        return inUse.flatMap { decorated[it].orEmpty() }
    }
}

@Composable
fun rememberNavigationState(topLevelRoutes: List<NavKey>): NavigationState {
    require(topLevelRoutes.isNotEmpty()) { "Нужна хотя бы одна вкладка" }
    val currentIndex = rememberSaveable { mutableIntStateOf(0) }
    val backStacks = topLevelRoutes.associateWith { route -> rememberNavBackStack(route) }
    return remember(topLevelRoutes) {
        NavigationState(topLevelRoutes, currentIndex, backStacks)
    }
}
