package ar.canastacerca.core.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow

/**
 * Вклад фичи в общий entryProvider. Каждый impl-модуль кладёт свою реализацию
 * в Set через `@ContributesIntoSet(AppScope::class)`, а :app собирает их в NavDisplay,
 * ничего не зная о самих фичах.
 */
interface EntryProviderInstaller {
    fun EntryProviderScope<NavKey>.install(navigator: Navigator)
}

/**
 * Вкладка нижней панели. Фича сама объявляет, что она — раздел верхнего уровня;
 * порядок вкладок задаёт [order].
 */
interface TopLevelDestination {
    val key: NavKey
    val order: Int

    @get:StringRes
    val label: Int

    @get:DrawableRes
    val icon: Int

    /** Число на значке вкладки (например, товары в корзине). */
    val badge: Flow<Int>?
        get() = null
}
