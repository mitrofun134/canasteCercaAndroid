package ar.canastacerca.feature.recipes.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.canastacerca.core.designsystem.component.BuildingBanner
import ar.canastacerca.core.designsystem.component.CanastaTopBar
import ar.canastacerca.core.designsystem.component.ChainLabel
import ar.canastacerca.core.designsystem.component.EmptyState
import ar.canastacerca.core.designsystem.component.ErrorState
import ar.canastacerca.core.designsystem.component.InlineError
import ar.canastacerca.core.designsystem.component.LoadingState
import ar.canastacerca.core.designsystem.component.PriceText
import ar.canastacerca.core.designsystem.format.formatMoney
import ar.canastacerca.core.designsystem.theme.CanastaTheme
import ar.canastacerca.feature.recipes.api.RecipeKey
import ar.canastacerca.feature.recipes.impl.R
import ar.canastacerca.feature.recipes.impl.data.Dish
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun RecipesRoute(
    onOpenRecipe: (RecipeKey) -> Unit,
    viewModel: RecipesViewModel = metroViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RecipesScreen(state = state, onAction = viewModel::onAction, onOpenRecipe = onOpenRecipe)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    state: RecipesUiState,
    onAction: (RecipesAction) -> Unit,
    onOpenRecipe: (RecipeKey) -> Unit,
) {
    Scaffold(
        topBar = { CanastaTopBar(title = stringResource(R.string.recipes_title)) },
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .fillMaxSize()
        when {
            state.isLoading && state.dishes.isEmpty() && !state.building -> LoadingState(modifier)
            state.error != null && state.dishes.isEmpty() ->
                ErrorState(state.error, onRetry = { onAction(RecipesAction.Retry) }, modifier = modifier)
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { onAction(RecipesAction.Refresh) },
                modifier = modifier,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "intro") {
                        Text(
                            stringResource(R.string.recipes_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.building) {
                        item(key = "building") { BuildingBanner(state.progressDone, state.progressTotal) }
                    }
                    state.error?.let { error ->
                        item(key = "error") { InlineError(error, onRetry = { onAction(RecipesAction.Retry) }) }
                    }
                    if (state.dishes.isEmpty() && !state.building && !state.isLoading) {
                        item(key = "empty") { EmptyState(title = stringResource(R.string.recipes_empty)) }
                    }
                    items(state.dishes, key = { it.id }) { dish ->
                        DishCard(dish = dish, onClick = { onOpenRecipe(RecipeKey(dish.id, dish.name)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DishCard(dish: Dish, onClick: () -> Unit) {
    val best = dish.best
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(dish.icon ?: "🍽️", fontSize = 36.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(dish.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.recipes_meta, dish.serves, dish.ingredientCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (best != null) {
                    ChainLabel(best.chainKey, best.chainName)
                    PriceText(best.total, listPrice = best.regular)
                    val details = buildList {
                        if (best.discounted > 0) add(stringResource(R.string.recipes_on_offer, best.discounted, best.count))
                        if (best.save >= 1) add(stringResource(R.string.recipes_save, formatMoney(best.save)))
                    }
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = CanastaTheme.extraColors.good,
                        )
                    }
                }
            }
        }
    }
}
