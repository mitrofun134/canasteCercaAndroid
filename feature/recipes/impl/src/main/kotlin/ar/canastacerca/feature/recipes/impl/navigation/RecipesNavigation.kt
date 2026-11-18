package ar.canastacerca.feature.recipes.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import ar.canastacerca.core.navigation.EntryProviderInstaller
import ar.canastacerca.core.navigation.Navigator
import ar.canastacerca.core.navigation.TopLevelDestination
import ar.canastacerca.feature.recipes.api.RecipeKey
import ar.canastacerca.feature.recipes.api.RecipesKey
import ar.canastacerca.feature.recipes.impl.R
import ar.canastacerca.feature.recipes.impl.ui.RecipeRoute
import ar.canastacerca.feature.recipes.impl.ui.RecipesRoute
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import ar.canastacerca.core.designsystem.R as DesignR

@Inject
@ContributesIntoSet(AppScope::class)
class RecipesEntryInstaller : EntryProviderInstaller {
    override fun EntryProviderScope<NavKey>.install(navigator: Navigator) {
        entry<RecipesKey> {
            RecipesRoute(onOpenRecipe = navigator::navigate)
        }
        entry<RecipeKey> { key ->
            RecipeRoute(key = key, onBack = navigator::goBack)
        }
    }
}

@Inject
@ContributesIntoSet(AppScope::class)
class RecipesTopLevelDestination : TopLevelDestination {
    override val key: NavKey = RecipesKey
    override val order: Int = 2
    override val label: Int = R.string.recipes_tab
    override val icon: Int = DesignR.drawable.ic_recipes
}
