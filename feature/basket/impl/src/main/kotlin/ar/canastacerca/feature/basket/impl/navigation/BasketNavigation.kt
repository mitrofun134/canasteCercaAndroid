package ar.canastacerca.feature.basket.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import ar.canastacerca.core.navigation.EntryProviderInstaller
import ar.canastacerca.core.navigation.Navigator
import ar.canastacerca.core.navigation.TopLevelDestination
import ar.canastacerca.feature.basket.api.BasketKey
import ar.canastacerca.feature.basket.api.BasketRepository
import ar.canastacerca.feature.basket.impl.R
import ar.canastacerca.feature.basket.impl.ui.BasketRoute
import ar.canastacerca.feature.catalog.api.CatalogKey
import ar.canastacerca.feature.location.api.AddressPickerKey
import ar.canastacerca.feature.payments.api.PaymentsKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ar.canastacerca.core.designsystem.R as DesignR

@Inject
@ContributesIntoSet(AppScope::class)
class BasketEntryInstaller : EntryProviderInstaller {
    override fun EntryProviderScope<NavKey>.install(navigator: Navigator) {
        entry<BasketKey> {
            BasketRoute(
                // Ключи чужих фич берём из их api-модулей — реализации корзине не видны
                onOpenAddress = { navigator.navigate(AddressPickerKey) },
                onOpenPayments = { navigator.navigate(PaymentsKey) },
                onOpenCatalog = { navigator.navigate(CatalogKey) },
            )
        }
    }
}

@Inject
@ContributesIntoSet(AppScope::class)
class BasketTopLevelDestination(
    basketRepository: BasketRepository,
) : TopLevelDestination {
    override val key: NavKey = BasketKey
    override val order: Int = 3
    override val label: Int = R.string.basket_tab
    override val icon: Int = DesignR.drawable.ic_basket
    override val badge: Flow<Int> = basketRepository.items.map { it.size }
}
