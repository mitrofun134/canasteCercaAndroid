package ar.canastacerca.feature.payments.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import ar.canastacerca.core.navigation.EntryProviderInstaller
import ar.canastacerca.core.navigation.Navigator
import ar.canastacerca.feature.payments.api.PaymentsKey
import ar.canastacerca.feature.payments.impl.ui.PaymentsRoute
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

@Inject
@ContributesIntoSet(AppScope::class)
class PaymentsEntryInstaller : EntryProviderInstaller {
    override fun EntryProviderScope<NavKey>.install(navigator: Navigator) {
        entry<PaymentsKey> {
            PaymentsRoute(onDone = navigator::goBack)
        }
    }
}
