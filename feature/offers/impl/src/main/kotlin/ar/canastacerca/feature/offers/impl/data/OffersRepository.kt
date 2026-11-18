package ar.canastacerca.feature.offers.impl.data

import ar.canastacerca.core.common.AppDispatchers
import ar.canastacerca.core.common.AppLanguage
import ar.canastacerca.core.common.AppResult
import ar.canastacerca.core.common.map
import ar.canastacerca.core.model.ChainPrice
import ar.canastacerca.core.model.Chains
import ar.canastacerca.core.model.ItemScope
import ar.canastacerca.core.model.QtyOffer
import ar.canastacerca.core.network.safeApiCall
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.roundToInt

data class TopOffers(
    val items: List<OfferItem>,
    val total: Int,
    /** Сколько акций у каждой сети сегодня (для фильтра по сети). */
    val counts: Map<String, Int>,
    /** Сервер ещё собирает список дня. */
    val building: Boolean,
    val progressDone: Int,
    val progressTotal: Int,
)

data class OfferItem(
    val id: String,
    val name: String,
    val brand: String?,
    val presentation: String?,
    val imageUrl: String?,
    val category: String?,
    val scope: ItemScope,
    val offer: Offer,
    /** Цены в других сетях, где этот товар тоже есть. */
    val otherPrices: List<ChainPrice>,
)

data class Offer(
    val chainKey: String,
    val chainName: String,
    val isQuantityOffer: Boolean,
    val price: Double,
    val listPrice: Double?,
    val unitPrice: Double?,
    val minQty: Int?,
    val text: String?,
    val until: String?,
    val pct: Int,
    val save: Double?,
)

interface OffersRepository {
    /** Топ скидок дня. Пока сервер собирает список, повторяет запрос и отдаёт промежуточные результаты. */
    fun topOffers(chain: String?): Flow<AppResult<TopOffers>>
}

@Inject
@ContributesBinding(AppScope::class)
class DefaultOffersRepository(
    private val client: HttpClient,
    private val dispatchers: AppDispatchers,
) : OffersRepository {

    override fun topOffers(chain: String?): Flow<AppResult<TopOffers>> = flow {
        var polls = 0
        while (true) {
            val result = safeApiCall {
                client.get("api/offers/top") {
                    parameter("n", TOP_N)
                    if (!chain.isNullOrBlank()) parameter("chain", chain)
                }.body<TopOffersDto>()
            }.map { it.toDomain() }
            emit(result)
            val building = (result as? AppResult.Success)?.value?.building == true
            if (!building || ++polls >= MAX_POLLS) break
            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(dispatchers.io)

    private companion object {
        const val TOP_N = 100
        const val MAX_POLLS = 40
        const val POLL_INTERVAL_MS = 4_000L
    }
}

private fun TopOffersDto.toDomain() = TopOffers(
    items = items.mapNotNull { it.toDomain() }.distinctBy { it.id },
    total = total,
    counts = counts,
    building = building,
    progressDone = progress?.done ?: 0,
    progressTotal = progress?.total ?: 0,
)

private fun TopOfferItemDto.toDomain(): OfferItem? {
    val o = offer ?: return null
    val isQty = o.kind == "qty"
    return OfferItem(
        id = id,
        name = name,
        brand = brand?.takeIf { it.isNotBlank() },
        presentation = presentation?.takeIf { it.isNotBlank() },
        imageUrl = image,
        category = AppLanguage.pick(catEs, catRu).takeIf { it.isNotBlank() },
        scope = ItemScope.from(scope),
        offer = Offer(
            chainKey = o.chain,
            chainName = o.chainName ?: Chains.nameOf(o.chain),
            isQuantityOffer = isQty,
            price = o.price,
            listPrice = o.listPrice,
            unitPrice = o.unitPrice,
            minQty = o.minQty,
            text = o.text?.takeIf { it.isNotBlank() },
            until = o.until?.takeIf { it.isNotBlank() },
            pct = o.pct.roundToInt(),
            save = o.save,
        ),
        otherPrices = prices
            .filter { it.chain != o.chain && it.price != null }
            .map { p ->
                ChainPrice(
                    chainKey = p.chain,
                    chainName = p.chainName ?: Chains.nameOf(p.chain),
                    price = p.price,
                    listPrice = p.listPrice,
                    available = true,
                    qtyOffer = p.qtyOffer?.let { q ->
                        q.text?.let { QtyOffer(text = it, minQty = q.minQty ?: 1, unitPrice = q.unitPrice) }
                    },
                    url = null,
                )
            }
            .sortedBy { it.price },
    )
}
