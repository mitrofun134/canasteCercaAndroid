package ar.canastacerca.core.navigation

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavKey

/**
 * Единственная точка, которая меняет [NavigationState]. Фичи получают его в
 * [EntryProviderInstaller.install] и передают экранам как обычные лямбды.
 */
@Stable
class Navigator(private val state: NavigationState) {

    val currentTopLevel: NavKey get() = state.topLevelRoute

    /** Ключ вкладки переключает вкладку, любой другой ключ кладётся на стек текущей вкладки. */
    fun navigate(route: NavKey) {
        if (route in state.backStacks) {
            selectTopLevel(route)
        } else {
            state.stackOf(state.topLevelRoute).add(route)
        }
    }

    /** Нажатие на вкладку; повторное нажатие на открытую вкладку возвращает к её корню. */
    fun selectTopLevel(route: NavKey) {
        if (route == state.topLevelRoute) {
            popToRoot(route)
        } else {
            state.topLevelRoute = route
        }
    }

    fun goBack() {
        val stack = state.stackOf(state.topLevelRoute)
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        } else {
            state.topLevelRoute = state.startRoute
        }
    }

    private fun popToRoot(route: NavKey) {
        val stack = state.stackOf(route)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}
