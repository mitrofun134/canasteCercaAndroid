package ar.canastacerca.feature.catalog.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import ar.canastacerca.core.navigation.EntryProviderInstaller
import ar.canastacerca.core.navigation.Navigator
import ar.canastacerca.core.navigation.TopLevelDestination
import ar.canastacerca.feature.catalog.api.CatalogKey
import ar.canastacerca.feature.catalog.api.CategoryKey
import ar.canastacerca.feature.catalog.impl.R
import ar.canastacerca.feature.catalog.impl.ui.CatalogRoute
import ar.canastacerca.feature.catalog.impl.ui.CategoryRoute
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import ar.canastacerca.core.designsystem.R as DesignR

@Inject
@ContributesIntoSet(AppScope::class)
class CatalogEntryInstaller : EntryProviderInstaller {
    override fun EntryProviderScope<NavKey>.install(navigator: Navigator) {
        entry<CatalogKey> {
            CatalogRoute(onOpenCategory = navigator::navigate)
        }
        entry<CategoryKey> { key ->
            CategoryRoute(key = key, onBack = navigator::goBack)
        }
    }
}

@Inject
@ContributesIntoSet(AppScope::class)
class CatalogTopLevelDestination : TopLevelDestination {
    override val key: NavKey = CatalogKey
    override val order: Int = 1
    override val label: Int = R.string.catalog_tab
    override val icon: Int = DesignR.drawable.ic_catalog
}
