package ar.canastacerca.feature.basket.impl.data

import ar.canastacerca.core.common.AppDispatchers
import ar.canastacerca.core.common.AppResult
import ar.canastacerca.core.common.map
import ar.canastacerca.core.model.Chains
import ar.canastacerca.core.network.safeApiCall
import ar.canastacerca.feature.basket.api.BasketItem
import ar.canastacerca.feature.location.api.Address
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.withContext

/** Сколько стоит корзина в каждом магазине рядом (POST /api/basket). */
interface BasketPricingRepository {
    suspend fun compare(request: CompareRequest): AppResult<Comparison>
}

data class CompareRequest(
    val items: List<BasketItem>,
    val address: Address,
    val radiusKm: Double,
    val paymentIds: Set<String>,
)

@Inject
@ContributesBinding(AppScope::class)
class DefaultBasketPricingRepository(
    private val client: HttpClient,
    private val dispatchers: AppDispatchers,
) : BasketPricingRepository {

    override suspend fun compare(request: CompareRequest): AppResult<Comparison> = withContext(dispatchers.io) {
        val body = BasketRequestDto(
            lat = request.address.lat,
            lng = request.address.lng,
            radiusKm = request.radiusKm,
            items = request.items.map { BasketRequestItemDto(it.id, it.quantity, it.scope.wire, it.name) },
            payments = request.paymentIds.toList(),
        )
        safeApiCall {
            client.post("api/basket") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body<BasketResultDto>()
        }.map { it.toComparison(request.items) }
    }
}

internal fun BasketResultDto.toComparison(items: List<BasketItem>): Comparison {
    val names = items.associate { it.id to it.name }
    val perStore = promos?.perStore.orEmpty()
    val stores = ranking.map { row ->
        val store = row.store
        val chainKey = store?.vtexChain ?: Chains.keyForName(store?.chain ?: store?.name)
        val chainName = store?.chain?.takeIf { it.isNotBlank() } ?: chainKey?.let(Chains::nameOf) ?: store?.name.orEmpty()
        val promo = perStore[row.storeId]
        StoreQuote(
            storeId = row.storeId,
            chainKey = chainKey,
            chainName = chainName,
            branchName = store?.name?.takeIf { it.isNotBlank() && it != chainName },
            address = store?.address?.takeIf { it.isNotBlank() },
            distanceKm = row.distanceKm ?: store?.distanceKm,
            isOnline = store?.kind == "online" || row.storeId.endsWith(":online"),
            total = row.total,
            regularTotal = row.regularTotal,
            found = row.found,
            complete = row.complete,
            missingNames = row.missing.map { names[it] ?: it },
            payToday = promo?.today?.toPayDiscount(),
            payBest = promo?.best?.toPayDiscount(),
        )
    }
    val best = bestSingle?.storeId ?: bestCoverage?.storeId ?: stores.firstOrNull { it.found > 0 }?.storeId
    return Comparison(
        itemCount = itemCount,
        stores = stores.filter { it.found > 0 },
        bestStoreId = best,
        hasCompleteStore = bestSingle != null,
        pair = bestPair?.takeIf { it.worthIt && it.stores.size == 2 }?.let { pair ->
            PairQuote(
                storeNames = pair.stores.map { s -> s.chain?.takeIf { it.isNotBlank() } ?: s.name.orEmpty() },
                total = pair.total,
                saving = pair.savingVsSingle,
                coversMore = pair.coversMore,
            )
        },
        spread = spread?.let { Spread(it.cheapest, it.mostExpensive, it.saving, it.savingPct) },
        unavailableNames = unavailable.map { names[it] ?: it },
        pendingChains = notices.filter { it.code == "CHAIN_PENDING" }.mapNotNull { it.chain }.distinct(),
        usedOnlineFallback = notices.any { it.code == "SEPA_DOWN" },
    )
}

private fun PromoDayDto.toPayDiscount(): PayDiscount? =
    finalTotal?.let { PayDiscount(finalTotal = it, pct = pct, dayName = dayName) }
