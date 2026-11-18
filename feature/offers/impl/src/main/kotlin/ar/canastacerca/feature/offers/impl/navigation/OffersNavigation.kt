package ar.canastacerca.feature.offers.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import ar.canastacerca.core.navigation.EntryProviderInstaller
import ar.canastacerca.core.navigation.Navigator
import ar.canastacerca.core.navigation.TopLevelDestination
import ar.canastacerca.feature.offers.api.OffersKey
import ar.canastacerca.feature.offers.impl.R
import ar.canastacerca.feature.offers.impl.ui.OffersRoute
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import ar.canastacerca.core.designsystem.R as DesignR

@Inject
@ContributesIntoSet(AppScope::class)
class OffersEntryInstaller : EntryProviderInstaller {
    override fun EntryProviderScope<NavKey>.install(navigator: Navigator) {
        entry<OffersKey> { OffersRoute() }
    }
}

/** Скидки — стартовая вкладка (order = 0): через неё пользователь выходит из приложения. */
@Inject
@ContributesIntoSet(AppScope::class)
class OffersTopLevelDestination : TopLevelDestination {
    override val key: NavKey = OffersKey
    override val order: Int = 0
    override val label: Int = R.string.offers_tab
    override val icon: Int = DesignR.drawable.ic_offers
}
