package ar.canastacerca

import android.app.Application
import ar.canastacerca.di.AppGraph
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.android.MetroApplication

class CanastaApplication : Application(), MetroApplication {

    /** Граф создаётся лениво: MetroAppComponentFactory может обратиться к нему раньше onCreate. */
    val appGraph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(this)
    }

    override val appComponentProviders: MetroAppComponentProviders
        get() = appGraph
}
