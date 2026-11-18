package ar.canastacerca.feature.location.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import ar.canastacerca.core.navigation.EntryProviderInstaller
import ar.canastacerca.core.navigation.Navigator
import ar.canastacerca.feature.location.api.AddressPickerKey
import ar.canastacerca.feature.location.impl.ui.AddressPickerRoute
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Выбор адреса — не вкладка: открывается поверх текущей вкладки из скидок или корзины. */
@Inject
@ContributesIntoSet(AppScope::class)
class LocationEntryInstaller : EntryProviderInstaller {
    override fun EntryProviderScope<NavKey>.install(navigator: Navigator) {
        entry<AddressPickerKey> {
            AddressPickerRoute(onBack = navigator::goBack)
        }
    }
}
