package ar.canastacerca.feature.recipes.impl.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.canastacerca.core.designsystem.component.CanastaTopBar
import ar.canastacerca.core.designsystem.component.DiscountBadge
import ar.canastacerca.core.designsystem.component.EmptyState
import ar.canastacerca.core.designsystem.component.ErrorState
import ar.canastacerca.core.designsystem.component.LoadingState
import ar.canastacerca.core.designsystem.component.MessageEffect
import ar.canastacerca.core.designsystem.component.PriceText
import ar.canastacerca.core.designsystem.component.ProductImage
import ar.canastacerca.core.designsystem.format.formatMoney
import ar.canastacerca.core.designsystem.format.formatQuantity
import ar.canastacerca.core.designsystem.theme.CanastaTheme
import ar.canastacerca.feature.recipes.api.RecipeKey
import ar.canastacerca.feature.recipes.impl.R
import ar.canastacerca.feature.recipes.impl.data.Dish
import ar.canastacerca.feature.recipes.impl.data.DishIngredient
import ar.canastacerca.feature.recipes.impl.data.DishOption
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
fun RecipeRoute(
    key: RecipeKey,
    onBack: () -> Unit,
    viewModel: RecipeViewModel = assistedMetroViewModel<RecipeViewModel, RecipeViewModel.Factory> {
        create(key)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RecipeScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
fun RecipeScreen(
    state: RecipeUiState,
    onAction: (RecipeAction) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState) { onAction(RecipeAction.MessageShown(it)) }

    Scaffold(
        topBar = { CanastaTopBar(title = state.title, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.selectedOption != null) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { onAction(RecipeAction.AddToBasket) },
                        enabled = !state.isAdding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.recipes_add_all))
                    }
                }
            }
        },
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .fillMaxSize()
        val dish = state.dish
        val option = state.selectedOption
        when {
            dish != null && option != null -> RecipeContent(
                dish = dish,
                option = option,
                padding = padding,
                onSelectChain = { onAction(RecipeAction.SelectChain(it)) },
            )
            state.isLoading -> LoadingState(modifier)
            state.error != null ->
                ErrorState(state.error, onRetry = { onAction(RecipeAction.Retry) }, modifier = modifier)
            else -> EmptyState(title = stringResource(R.string.recipes_not_found), modifier = modifier)
        }
    }
}

@Composable
private fun RecipeContent(
    dish: Dish,
    option: DishOption,
    padding: PaddingValues,
    onSelectChain: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "chains") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(dish.options, key = { it.chainKey }) { o ->
                    FilterChip(
                        selected = o.chainKey == option.chainKey,
                        onClick = { onSelectChain(o.chainKey) },
                        label = { Text("${o.chainName} · ${formatMoney(o.total)}") },
                    )
                }
            }
        }
        item(key = "summary") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.recipes_meta, dish.serves, dish.ingredientCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    PriceText(option.total, listPrice = option.regular)
                    if (option.save >= 1) {
                        Text(
                            stringResource(R.string.recipes_save, formatMoney(option.save)),
                            color = CanastaTheme.extraColors.good,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Text(
                        stringResource(R.string.recipes_on_offer, option.discounted, option.count),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!option.complete) {
                        Text(
                            stringResource(R.string.recipes_missing, option.count - option.found),
                            style = MaterialTheme.typography.bodySmall,
                            color = CanastaTheme.extraColors.warn,
                        )
                    }
                }
            }
        }
        item(key = "ingredients-title") {
            Text(
                stringResource(R.string.recipes_ingredients, option.chainName),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        items(option.ingredients, key = { it.id }) { ingredient ->
            IngredientRow(ingredient)
        }
    }
}

@Composable
private fun IngredientRow(ingredient: DishIngredient) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductImage(ingredient.imageUrl, contentDescription = ingredient.name, size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ingredient.name, style = MaterialTheme.typography.bodyLarge)
            val product = ingredient.productName
            val packs = ingredient.packs
            val subtitle = when {
                !ingredient.found -> stringResource(R.string.recipes_not_found_item)
                product != null && packs != null && packs > 1 -> stringResource(R.string.recipes_packs, formatQuantity(packs), product)
                else -> product
            }
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ingredient.found) MaterialTheme.colorScheme.onSurfaceVariant else CanastaTheme.extraColors.warn,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            ingredient.offerPct?.let { DiscountBadge(it) }
            if (ingredient.found) {
                Text(formatMoney(ingredient.cost), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
