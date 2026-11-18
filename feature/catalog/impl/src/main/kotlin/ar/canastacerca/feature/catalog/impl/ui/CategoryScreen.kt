package ar.canastacerca.feature.catalog.impl.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.canastacerca.core.designsystem.component.BasketToggleButton
import ar.canastacerca.core.designsystem.component.BuildingBanner
import ar.canastacerca.core.designsystem.component.CanastaTopBar
import ar.canastacerca.core.designsystem.component.ChainLabel
import ar.canastacerca.core.designsystem.component.DiscountBadge
import ar.canastacerca.core.designsystem.component.EmptyState
import ar.canastacerca.core.designsystem.component.ErrorState
import ar.canastacerca.core.designsystem.component.InlineError
import ar.canastacerca.core.designsystem.component.LoadingState
import ar.canastacerca.core.designsystem.component.MessageEffect
import ar.canastacerca.core.designsystem.component.ProductImage
import ar.canastacerca.core.designsystem.component.PromoBadge
import ar.canastacerca.core.designsystem.format.formatMoney
import ar.canastacerca.core.designsystem.theme.CanastaTheme
import ar.canastacerca.core.model.ChainPrice
import ar.canastacerca.feature.catalog.api.CategoryKey
import ar.canastacerca.feature.catalog.impl.R
import ar.canastacerca.feature.catalog.impl.data.Product
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import ar.canastacerca.core.designsystem.R as DesignR

@Composable
fun CategoryRoute(
    key: CategoryKey,
    onBack: () -> Unit,
    viewModel: CategoryViewModel = assistedMetroViewModel<CategoryViewModel, CategoryViewModel.Factory> {
        create(key)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CategoryScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    state: CategoryUiState,
    onAction: (CategoryAction) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState) { onAction(CategoryAction.MessageShown(it)) }

    Scaffold(
        topBar = { CanastaTopBar(title = state.title, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Controls(state = state, onAction = onAction)
            when {
                state.isLoading && state.totalCount == 0 && !state.building -> LoadingState()
                state.error != null && state.totalCount == 0 ->
                    ErrorState(state.error, onRetry = { onAction(CategoryAction.Retry) })
                else -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { onAction(CategoryAction.Refresh) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ProductList(state = state, onAction = onAction)
                }
            }
        }
    }
}

@Composable
private fun Controls(state: CategoryUiState, onAction: (CategoryAction) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onAction(CategoryAction.QueryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.catalog_search_hint)) },
            leadingIcon = { Icon(painterResource(DesignR.drawable.ic_search), contentDescription = null) },
            singleLine = true,
        )
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ProductSort.entries, key = { it.name }) { sort ->
            FilterChip(
                selected = state.sort == sort,
                onClick = { onAction(CategoryAction.SortChanged(sort)) },
                label = {
                    Text(
                        stringResource(
                            when (sort) {
                                ProductSort.Popular -> R.string.catalog_sort_popular
                                ProductSort.Price -> R.string.catalog_sort_price
                                ProductSort.UnitPrice -> R.string.catalog_sort_unit
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun ProductList(state: CategoryUiState, onAction: (CategoryAction) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.building) {
            item(key = "building") { BuildingBanner(state.progressDone, state.progressTotal) }
        }
        state.error?.let { error ->
            item(key = "error") { InlineError(error, onRetry = { onAction(CategoryAction.Retry) }) }
        }
        if (state.pendingChains.isNotEmpty()) {
            item(key = "pending") {
                Text(
                    stringResource(R.string.catalog_pending, state.pendingChains.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = CanastaTheme.extraColors.warn,
                )
            }
        }
        if (state.products.isEmpty() && !state.isLoading && !state.building) {
            item(key = "empty") {
                EmptyState(
                    title = if (state.query.isNotBlank()) {
                        stringResource(R.string.catalog_no_matches, state.query)
                    } else {
                        stringResource(R.string.catalog_empty)
                    },
                )
            }
        }
        items(state.products, key = { it.id }) { product ->
            ProductCard(
                product = product,
                expanded = state.expandedId == product.id,
                inBasket = product.id in state.basketIds,
                onToggleExpanded = { onAction(CategoryAction.ToggleExpanded(product.id)) },
                onToggleBasket = { onAction(CategoryAction.ToggleBasket(product)) },
            )
        }
    }
}

@Composable
private fun ProductCard(
    product: Product,
    expanded: Boolean,
    inBasket: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleBasket: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row {
                ProductImage(product.imageUrl, contentDescription = product.name, size = 72.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        product.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    listOfNotNull(product.brand, product.presentation)
                        .joinToString(" · ")
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(formatMoney(product.minPrice), style = CanastaTheme.priceStyle)
                        product.minChainName?.let {
                            Text(
                                stringResource(R.string.catalog_from_chain, it),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                    }
                    val details = buildList {
                        val unitPrice = product.unitPrice
                        val unitLabel = product.unitLabel
                        if (unitPrice != null && unitLabel != null) {
                            add(stringResource(R.string.catalog_unit_price, formatMoney(unitPrice), unitLabel))
                        }
                        if (product.chainsCount > 1) add(stringResource(R.string.catalog_chains, product.chainsCount))
                        if (product.glutenFree) add(stringResource(R.string.catalog_gluten_free))
                        if (product.prescription) add(stringResource(R.string.catalog_rx))
                    }
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    BasketToggleButton(inBasket = inBasket, onClick = onToggleBasket)
                    Icon(
                        painterResource(if (expanded) DesignR.drawable.ic_expand_less else DesignR.drawable.ic_expand_more),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    product.prices.forEach { price -> ChainPriceRow(price) }
                }
            }
        }
    }
}

@Composable
private fun ChainPriceRow(price: ChainPrice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChainLabel(price.chainKey, price.chainName, Modifier.weight(1f))
        price.qtyOffer?.let { PromoBadge(it.text) }
        price.discountPct?.takeIf { it > 0 }?.let { DiscountBadge(it) }
        if (price.available) {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(price.price), style = MaterialTheme.typography.titleSmall)
                val listPrice = price.listPrice
                val current = price.price
                if (listPrice != null && current != null && listPrice > current) {
                    Text(
                        formatMoney(listPrice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
            }
        } else {
            Text(
                stringResource(R.string.catalog_no_stock),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
