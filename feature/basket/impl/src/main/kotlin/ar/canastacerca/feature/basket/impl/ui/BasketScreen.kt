package ar.canastacerca.feature.basket.impl.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.canastacerca.core.designsystem.component.AddressChip
import ar.canastacerca.core.designsystem.component.CanastaTopBar
import ar.canastacerca.core.designsystem.component.ChainAvatar
import ar.canastacerca.core.designsystem.component.EmptyState
import ar.canastacerca.core.designsystem.component.InlineError
import ar.canastacerca.core.designsystem.component.ProductImage
import ar.canastacerca.core.designsystem.component.QuantityStepper
import ar.canastacerca.core.designsystem.format.formatDistance
import ar.canastacerca.core.designsystem.format.formatMoney
import ar.canastacerca.core.designsystem.format.formatMoneyDelta
import ar.canastacerca.core.designsystem.format.formatPercent
import ar.canastacerca.core.designsystem.theme.CanastaTheme
import ar.canastacerca.feature.basket.api.BasketItem
import ar.canastacerca.feature.basket.api.BasketRepository
import ar.canastacerca.feature.basket.impl.R
import ar.canastacerca.feature.basket.impl.data.Comparison
import ar.canastacerca.feature.basket.impl.data.StoreQuote
import ar.canastacerca.feature.location.api.Address
import ar.canastacerca.feature.location.api.LocationRepository
import dev.zacsweers.metrox.viewmodel.metroViewModel
import ar.canastacerca.core.designsystem.R as DesignR

@Composable
fun BasketRoute(
    onOpenAddress: () -> Unit,
    onOpenPayments: () -> Unit,
    onOpenCatalog: () -> Unit,
    viewModel: BasketViewModel = metroViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BasketScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenAddress = onOpenAddress,
        onOpenPayments = onOpenPayments,
        onOpenCatalog = onOpenCatalog,
    )
}

@Composable
fun BasketScreen(
    state: BasketUiState,
    onAction: (BasketAction) -> Unit,
    onOpenAddress: () -> Unit,
    onOpenPayments: () -> Unit,
    onOpenCatalog: () -> Unit,
) {
    Scaffold(
        topBar = { CanastaTopBar(title = stringResource(R.string.basket_title)) },
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.basket_empty_title),
                message = stringResource(R.string.basket_empty_message),
                actionLabel = stringResource(R.string.basket_go_catalog),
                onAction = onOpenCatalog,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "context") {
                ContextCard(
                    address = state.address,
                    radiusKm = state.radiusKm,
                    paymentsCount = state.paymentsCount,
                    onOpenAddress = onOpenAddress,
                    onOpenPayments = onOpenPayments,
                    onSetRadius = { onAction(BasketAction.SetRadius(it)) },
                )
            }

            comparisonSection(
                state = state.comparison,
                paymentsCount = state.paymentsCount,
                onRetry = { onAction(BasketAction.Retry) },
                onOpenAddress = onOpenAddress,
                onOpenPayments = onOpenPayments,
            )

            item(key = "items-header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.basket_products, state.items.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onAction(BasketAction.Clear) }) {
                        Text(stringResource(R.string.basket_clear))
                    }
                }
            }
            items(state.items, key = { "item-" + it.id }) { item ->
                BasketItemRow(
                    item = item,
                    onIncrease = { onAction(BasketAction.Increase(item.id)) },
                    onDecrease = { onAction(BasketAction.Decrease(item.id)) },
                    onRemove = { onAction(BasketAction.Remove(item.id)) },
                )
            }
        }
    }
}

@Composable
private fun ContextCard(
    address: Address?,
    radiusKm: Double,
    paymentsCount: Int,
    onOpenAddress: () -> Unit,
    onOpenPayments: () -> Unit,
    onSetRadius: (Double) -> Unit,
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AddressChip(label = address?.label, onClick = onOpenAddress)
                AssistChip(
                    onClick = onOpenPayments,
                    label = {
                        Text(
                            if (paymentsCount > 0) {
                                stringResource(R.string.basket_payments_n, paymentsCount)
                            } else {
                                stringResource(R.string.basket_payments_none)
                            },
                            maxLines = 1,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painterResource(DesignR.drawable.ic_payments),
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LocationRepository.RADIUS_OPTIONS_KM.forEach { km ->
                    FilterChip(
                        selected = km == radiusKm,
                        onClick = { onSetRadius(km) },
                        label = { Text(stringResource(R.string.basket_radius, formatDistance(km).orEmpty())) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.comparisonSection(
    state: ComparisonState,
    paymentsCount: Int,
    onRetry: () -> Unit,
    onOpenAddress: () -> Unit,
    onOpenPayments: () -> Unit,
) {
    when (state) {
        ComparisonState.Idle -> Unit
        ComparisonState.NeedsAddress -> item(key = "need-address") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.basket_need_address), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.size(8.dp))
                    TextButton(onClick = onOpenAddress) { Text(stringResource(R.string.basket_pick_address)) }
                }
            }
        }
        is ComparisonState.Loading -> {
            item(key = "comparing") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.basket_comparing), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            state.previous?.let { comparisonContent(it, paymentsCount, onOpenPayments, dimmed = true) }
        }
        is ComparisonState.Ready -> comparisonContent(state.comparison, paymentsCount, onOpenPayments, dimmed = false)
        is ComparisonState.Failed -> {
            item(key = "compare-error") { InlineError(state.error, onRetry = onRetry) }
            state.previous?.let { comparisonContent(it, paymentsCount, onOpenPayments, dimmed = true) }
        }
    }
}

private fun LazyListScope.comparisonContent(
    comparison: Comparison,
    paymentsCount: Int,
    onOpenPayments: () -> Unit,
    dimmed: Boolean,
) {
    val alpha = if (dimmed) 0.5f else 1f
    if (comparison.usedOnlineFallback) {
        item(key = "online-fallback") { Notice(stringResource(R.string.basket_online_prices)) }
    }
    comparison.best?.let { best ->
        item(key = "best") {
            BestStoreCard(
                store = best,
                itemCount = comparison.itemCount,
                isComplete = comparison.hasCompleteStore,
                paymentsCount = paymentsCount,
                onOpenPayments = onOpenPayments,
                modifier = Modifier.alpha(alpha),
            )
        }
    }
    comparison.spread?.takeIf { it.saving > 0 }?.let { spread ->
        item(key = "spread") {
            Text(
                text = stringResource(
                    R.string.basket_spread,
                    formatMoney(spread.saving),
                    formatPercent(spread.savingPct, decimals = 1),
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.alpha(alpha),
            )
        }
    }
    comparison.pair?.let { pair ->
        item(key = "pair") {
            Card(modifier = Modifier.alpha(alpha)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.basket_pair_title), style = MaterialTheme.typography.titleSmall)
                    val stores = pair.storeNames.joinToString(" + ")
                    val saving = pair.saving
                    Text(
                        text = when {
                            pair.coversMore -> stringResource(R.string.basket_pair_covers_more, stores)
                            saving != null -> stringResource(R.string.basket_pair_saving, stores, formatMoney(saving))
                            else -> stores
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(formatMoney(pair.total), style = CanastaTheme.priceStyle)
                }
            }
        }
    }
    if (comparison.unavailableNames.isNotEmpty()) {
        item(key = "unavailable") {
            Notice(stringResource(R.string.basket_unavailable, comparison.unavailableNames.joinToString(", ")))
        }
    }
    if (comparison.pendingChains.isNotEmpty()) {
        item(key = "pending") {
            Notice(stringResource(R.string.basket_pending, comparison.pendingChains.joinToString(", ")))
        }
    }
    if (comparison.stores.isNotEmpty()) {
        item(key = "stores-title") {
            Text(stringResource(R.string.basket_stores_title), style = MaterialTheme.typography.titleMedium)
        }
        val cheapestComplete = comparison.stores.firstOrNull { it.complete }?.total
        items(comparison.stores, key = { "store-" + it.storeId }) { store ->
            StoreRow(
                store = store,
                cheapestComplete = cheapestComplete,
                itemCount = comparison.itemCount,
                modifier = Modifier.alpha(alpha),
            )
        }
    }
}

@Composable
private fun BestStoreCard(
    store: StoreQuote,
    itemCount: Int,
    isComplete: Boolean,
    paymentsCount: Int,
    onOpenPayments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(if (isComplete) R.string.basket_best_title else R.string.basket_best_partial),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChainAvatar(store.chainKey, store.chainName, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(store.chainName, style = MaterialTheme.typography.titleMedium)
                    storeSubtitle(store)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Text(formatMoney(store.total), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.basket_found, store.found, itemCount),
                style = MaterialTheme.typography.bodySmall,
            )
            if (store.storeOffersSaving >= 1) {
                Text(
                    stringResource(R.string.basket_store_offers_saving, formatMoney(store.storeOffersSaving)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CanastaTheme.extraColors.good,
                )
            }
            val today = store.payToday
            when {
                today != null -> Text(
                    stringResource(R.string.basket_pay_today, formatMoney(today.finalTotal)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CanastaTheme.extraColors.good,
                )
                paymentsCount == 0 -> TextButton(onClick = onOpenPayments) {
                    Text(stringResource(R.string.basket_pay_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
            val bestDay = store.payBest
            val bestDayName = bestDay?.dayName
            if (bestDay != null && bestDayName != null && bestDay.finalTotal < (today?.finalTotal ?: store.total)) {
                Text(
                    stringResource(R.string.basket_pay_best, bestDayName, formatMoney(bestDay.finalTotal)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StoreRow(
    store: StoreQuote,
    cheapestComplete: Double?,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChainAvatar(store.chainKey, store.chainName, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(store.chainName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            storeSubtitle(store)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMoney(store.total), style = MaterialTheme.typography.titleSmall)
            when {
                !store.complete -> Text(
                    stringResource(R.string.basket_missing, itemCount - store.found),
                    style = MaterialTheme.typography.bodySmall,
                    color = CanastaTheme.extraColors.warn,
                )
                cheapestComplete != null && store.total > cheapestComplete -> {
                    val delta = store.total - cheapestComplete
                    Text(
                        text = formatMoneyDelta(delta) + " (+" + formatPercent(delta / cheapestComplete * 100, decimals = 1) + ")",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            store.payToday?.let { today ->
                Text(
                    "→ " + formatMoney(today.finalTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = CanastaTheme.extraColors.good,
                )
            }
        }
    }
}

@Composable
private fun storeSubtitle(store: StoreQuote): String? {
    val parts = listOfNotNull(
        store.branchName,
        store.address,
        formatDistance(store.distanceKm),
        if (store.isOnline) stringResource(R.string.basket_online) else null,
    )
    return parts.distinct().joinToString(" · ").ifBlank { null }
}

@Composable
private fun Notice(text: String) {
    Surface(
        color = CanastaTheme.extraColors.warnContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BasketItemRow(
    item: BasketItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProductImage(item.imageUrl, contentDescription = item.name, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = item.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            QuantityStepper(
                quantity = item.quantity,
                onDecrease = onDecrease,
                onIncrease = onIncrease,
                canIncrease = item.quantity < BasketRepository.MAX_QUANTITY,
            )
            IconButton(onClick = onRemove) {
                Icon(painterResource(DesignR.drawable.ic_delete), contentDescription = stringResource(R.string.basket_remove))
            }
        }
    }
}
