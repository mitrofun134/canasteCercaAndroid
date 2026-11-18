package ar.canastacerca.di

import android.app.Application
import ar.canastacerca.BuildConfig
import ar.canastacerca.core.network.NetworkConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * Корневой граф приложения. Сам граф почти пустой: всё остальное фичи вносят через
 * `@ContributesBinding` / `@ContributesIntoSet` / `@ContributesIntoMap(AppScope::class)`,
 * а Metro собирает вклады всех модулей на этапе компиляции.
 *
 * - [ViewModelGraph] — map ViewModel и их assisted-фабрик для MetroViewModelFactory;
 * - [MetroAppComponentProviders] — Activity создаются графом (конструкторная инъекция).
 */
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph, MetroAppComponentProviders {

    @Provides
    fun provideNetworkConfig(): NetworkConfig = NetworkConfig(
        baseUrl = BuildConfig.CANASTA_BASE_URL.let { if (it.endsWith("/")) it else "$it/" },
        accessKey = BuildConfig.CANASTA_ACCESS_KEY.takeIf { it.isNotBlank() },
    )

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
