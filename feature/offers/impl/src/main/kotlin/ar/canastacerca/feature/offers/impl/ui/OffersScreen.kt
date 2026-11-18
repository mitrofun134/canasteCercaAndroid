package ar.canastacerca.feature.offers.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
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
import ar.canastacerca.core.designsystem.component.PriceText
import ar.canastacerca.core.designsystem.component.ProductImage
import ar.canastacerca.core.designsystem.component.PromoBadge
import ar.canastacerca.core.designsystem.format.formatMoney
import ar.canastacerca.core.designsystem.theme.CanastaTheme
import ar.canastacerca.feature.offers.impl.R
import ar.canastacerca.feature.offers.impl.data.OfferItem
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun OffersRoute(viewModel: OffersViewModel = metroViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OffersScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OffersScreen(
    state: OffersUiState,
    onAction: (OffersAction) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState) { onAction(OffersAction.MessageShown(it)) }

    Scaffold(
        topBar = { CanastaTopBar(title = stringResource(R.string.offers_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            ChainFilterRow(
                chains = state.chains,
                selected = state.selectedChain,
                onSelect = { onAction(OffersAction.SelectChain(it)) },
            )
            when {
                state.isLoading && state.offers.isEmpty() && !state.building -> LoadingState()
                state.error != null && state.offers.isEmpty() ->
                    ErrorState(state.error, onRetry = { onAction(OffersAction.Retry) })
                else -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { onAction(OffersAction.Refresh) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    OffersList(state = state, onAction = onAction)
                }
            }
        }
    }
}

@Composable
private fun ChainFilterRow(
    chains: List<ChainFilter>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    if (chains.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.offers_all_chains)) },
            )
        }
        items(chains, key = { it.key }) { chain ->
            FilterChip(
                selected = selected == chain.key,
                onClick = { onSelect(chain.key) },
                label = { Text(stringResource(R.string.offers_chain_count, chain.name, chain.count)) },
            )
        }
    }
}

@Composable
private fun OffersList(state: OffersUiState, onAction: (OffersAction) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.building) {
            item(key = "building") { BuildingBanner(state.progressDone, state.progressTotal) }
        }
        state.error?.let { error ->
            item(key = "error") { InlineError(error, onRetry = { onAction(OffersAction.Retry) }) }
        }
        if (state.offers.isEmpty() && !state.building && !state.isLoading) {
            item(key = "empty") { EmptyState(title = stringResource(R.string.offers_empty)) }
        }
        if (state.offers.isNotEmpty() && state.selectedChain == null) {
            item(key = "subtitle") {
                Text(
                    stringResource(R.string.offers_subtitle, state.offers.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.offers, key = { it.id }) { offer ->
            OfferCard(
                item = offer,
                inBasket = offer.id in state.basketIds,
                onToggleBasket = { onAction(OffersAction.ToggleBasket(offer)) },
            )
        }
    }
}

@Composable
private fun OfferCard(
    item: OfferItem,
    inBasket: Boolean,
    onToggleBasket: () -> Unit,
) {
    val offer = item.offer
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(12.dp)) {
            Box {
                ProductImage(item.imageUrl, contentDescription = item.name, size = 76.dp)
                if (offer.pct > 0) {
                    DiscountBadge(offer.pct, Modifier.align(Alignment.TopStart))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                listOfNotNull(item.brand, item.presentation, item.category)
                    .distinct()
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
                ChainLabel(offer.chainKey, offer.chainName)
                if (offer.isQuantityOffer) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PromoBadge(offer.text ?: "${offer.minQty ?: 2}x")
                        PriceText(offer.price)
                    }
                    val unit = offer.unitPrice
                    val minQty = offer.minQty
                    if (unit != null && minQty != null) {
                        Text(
                            stringResource(R.string.offers_unit_price, formatMoney(unit), minQty),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    PriceText(offer.price, listPrice = offer.listPrice)
                }
                offer.save?.takeIf { it >= 1 }?.let { save ->
                    Text(
                        stringResource(R.string.offers_save, formatMoney(save)),
                        style = MaterialTheme.typography.labelMedium,
                        color = CanastaTheme.extraColors.good,
                    )
                }
                if (item.otherPrices.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.offers_also_in,
                            item.otherPrices.take(3).joinToString { "${it.chainName} ${formatMoney(it.price)}" },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            BasketToggleButton(inBasket = inBasket, onClick = onToggleBasket)
        }
    }
}
