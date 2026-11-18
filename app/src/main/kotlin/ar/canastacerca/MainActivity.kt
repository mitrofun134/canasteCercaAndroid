package ar.canastacerca

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelProvider
import ar.canastacerca.core.designsystem.theme.CanastaTheme
import ar.canastacerca.core.navigation.EntryProviderInstaller
import ar.canastacerca.core.navigation.TopLevelDestination
import ar.canastacerca.ui.CanastaApp
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.ActivityKey
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

/**
 * Единственная Activity. Её создаёт граф Metro (metrox-android), поэтому всё нужное
 * приходит в конструктор: фабрика ViewModel, вклады фич в навигацию и вкладки.
 */
@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey(MainActivity::class)
@Inject
class MainActivity(
    private val viewModelFactory: MetroViewModelFactory,
    private val entryInstallers: Set<EntryProviderInstaller>,
    private val topLevelDestinations: Set<TopLevelDestination>,
) : ComponentActivity() {

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = viewModelFactory

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val destinations = topLevelDestinations.sortedBy { it.order }
        setContent {
            CanastaTheme {
                CompositionLocalProvider(LocalMetroViewModelFactory provides viewModelFactory) {
                    CanastaApp(destinations = destinations, installers = entryInstallers)
                }
            }
        }
    }
}
