package ar.canastacerca.feature.catalog.impl.data

import ar.canastacerca.core.common.AppDispatchers
import ar.canastacerca.core.common.AppLanguage
import ar.canastacerca.core.common.AppResult
import ar.canastacerca.core.common.map
import ar.canastacerca.core.model.ChainPrice
import ar.canastacerca.core.model.Chains
import ar.canastacerca.core.model.ItemScope
import ar.canastacerca.core.model.QtyOffer
import ar.canastacerca.core.network.safeApiCall
import ar.canastacerca.feature.catalog.api.CategoryKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CatalogGroup(
    val id: String,
    val name: String,
    val icon: String?,
    val categories: List<CatalogCategory>,
)

data class CatalogCategory(val id: String, val name: String)

data class CategoryPage(
    val products: List<Product>,
    val scope: ItemScope,
    /** Сети, которые ещё не ответили (сервер ждёт их в фоне). */
    val pendingChains: List<String>,
    val building: Boolean,
    val progressDone: Int,
    val progressTotal: Int,
)

data class Product(
    val id: String,
    val name: String,
    val brand: String?,
    val presentation: String?,
    val imageUrl: String?,
    /** Сначала доступные, по возрастанию цены. */
    val prices: List<ChainPrice>,
    val minPrice: Double?,
    val minChainName: String?,
    val unitPrice: Double?,
    val unitLabel: String?,
    val popularity: Double,
    val scope: ItemScope,
    val glutenFree: Boolean,
    val prescription: Boolean,
) {
    val chainsCount: Int get() = prices.size
}

interface CatalogRepository {
    suspend fun groups(): AppResult<List<CatalogGroup>>

    /** Товары подраздела. Раздел «Всё» сервер собирает в фоне — тогда запрос повторяется. */
    fun category(categoryId: String): Flow<AppResult<CategoryPage>>
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultCatalogRepository(
    private val client: HttpClient,
    private val dispatchers: AppDispatchers,
) : CatalogRepository {

    private val groupsMutex = Mutex()
    private var cachedGroups: List<CatalogGroup>? = null

    override suspend fun groups(): AppResult<List<CatalogGroup>> = groupsMutex.withLock {
        cachedGroups?.let { return@withLock AppResult.Success(it) }
        val result = withContext(dispatchers.io) {
            safeApiCall { client.get("api/catalog/categories").body<CategoriesDto>() }
                .map { dto -> dto.groups.map { it.toDomain() } }
        }
        if (result is AppResult.Success) cachedGroups = result.value
        result
    }

    override fun category(categoryId: String): Flow<AppResult<CategoryPage>> = flow {
        var polls = 0
        while (true) {
            val result = safeApiCall {
                if (categoryId == CategoryKey.ALL) {
                    client.get("api/catalog/all").body<CategoryPageDto>()
                } else {
                    client.get("api/catalog") { parameter("cat", categoryId) }.body<CategoryPageDto>()
                }
            }.map { it.toDomain() }
            emit(result)
            val building = (result as? AppResult.Success)?.value?.building == true
            if (!building || ++polls >= MAX_POLLS) break
            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(dispatchers.io)

    private companion object {
        const val MAX_POLLS = 30
        const val POLL_INTERVAL_MS = 4_000L
    }
}

private fun GroupDto.toDomain() = CatalogGroup(
    id = id,
    name = AppLanguage.pick(es, ru),
    icon = icon,
    categories = cats.map { CatalogCategory(it.id, AppLanguage.pick(it.es, it.ru)) },
)

private fun CategoryPageDto.toDomain(): CategoryPage {
    val pageScope = ItemScope.from(category?.scope)
    return CategoryPage(
        products = items.map { it.toDomain(pageScope) }.distinctBy { it.id },
        scope = pageScope,
        pendingChains = pending.mapNotNull { it.chainName ?: it.chain?.let(Chains::nameOf) },
        building = building,
        progressDone = progress?.done ?: 0,
        progressTotal = progress?.total ?: 0,
    )
}

private fun ProductDto.toDomain(pageScope: ItemScope): Product {
    val chainPrices = prices.map { p ->
        ChainPrice(
            chainKey = p.chain,
            chainName = p.chainName ?: Chains.nameOf(p.chain),
            price = p.price,
            listPrice = p.listPrice,
            available = p.available && p.price != null,
            qtyOffer = p.qtyOffer?.let { q ->
                q.text?.takeIf { it.isNotBlank() }?.let { text ->
                    QtyOffer(text = text, minQty = q.minQty ?: 1, unitPrice = q.unitPrice)
                }
            },
            url = p.url,
        )
    }.sortedWith(compareByDescending<ChainPrice> { it.available }.thenBy { it.price ?: Double.MAX_VALUE })
    return Product(
        id = id,
        name = name,
        brand = brand?.takeIf { it.isNotBlank() },
        presentation = presentation?.takeIf { it.isNotBlank() },
        imageUrl = image,
        prices = chainPrices,
        minPrice = minPrice,
        minChainName = minChain,
        unitPrice = unitPrice,
        unitLabel = unitLabel,
        popularity = pop ?: 0.0,
        scope = scope?.let { ItemScope.from(it) } ?: pageScope,
        glutenFree = nutrition?.certs?.contains("sin_tacc") == true,
        prescription = rx,
    )
}
