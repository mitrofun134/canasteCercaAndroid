package ar.canastacerca.feature.recipes.impl.data

import ar.canastacerca.core.common.AppDispatchers
import ar.canastacerca.core.common.AppLanguage
import ar.canastacerca.core.common.AppResult
import ar.canastacerca.core.common.map
import ar.canastacerca.core.model.Chains
import ar.canastacerca.core.network.safeApiCall
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.math.roundToInt

data class DishDeals(
    val dishes: List<Dish>,
    val building: Boolean,
    val progressDone: Int,
    val progressTotal: Int,
)

data class Dish(
    val id: String,
    val name: String,
    val icon: String?,
    val serves: Int,
    val ingredientCount: Int,
    /** Варианты по сетям: сначала полные (все ингредиенты нашлись), затем по цене. */
    val options: List<DishOption>,
    val bestChainKey: String?,
) {
    val best: DishOption? get() = options.firstOrNull { it.chainKey == bestChainKey } ?: options.firstOrNull()
}

data class DishOption(
    val chainKey: String,
    val chainName: String,
    val total: Double,
    val regular: Double,
    val save: Double,
    val found: Int,
    val count: Int,
    val discounted: Int,
    val ingredients: List<DishIngredient>,
) {
    val complete: Boolean get() = found >= count
}

data class DishIngredient(
    /** gen:… — «обобщённый товар» сервера; его же кладём в корзину. */
    val id: String,
    val name: String,
    val found: Boolean,
    val productName: String?,
    val imageUrl: String?,
    val packs: Double?,
    val cost: Double?,
    val regular: Double?,
    val offerPct: Int?,
)

/** Рецепты из сегодняшних скидок: GET /api/offers/dishes (lib/dishdeals.js). */
interface RecipesRepository {
    fun dishDeals(): Flow<AppResult<DishDeals>>

    /** Блюдо из последнего загруженного списка, иначе загружает список. */
    suspend fun dish(id: String): AppResult<Dish?>
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultRecipesRepository(
    private val client: HttpClient,
    private val dispatchers: AppDispatchers,
) : RecipesRepository {

    private val cache = MutableStateFlow<DishDeals?>(null)

    override fun dishDeals(): Flow<AppResult<DishDeals>> = flow {
        var polls = 0
        while (true) {
            val result = fetch()
            emit(result)
            val building = (result as? AppResult.Success)?.value?.building == true
            if (!building || ++polls >= MAX_POLLS) break
            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(dispatchers.io)

    override suspend fun dish(id: String): AppResult<Dish?> {
        cache.value?.dishes?.firstOrNull { it.id == id }?.let { return AppResult.Success(it) }
        return withContext(dispatchers.io) { fetch() }.map { deals -> deals.dishes.firstOrNull { it.id == id } }
    }

    private suspend fun fetch(): AppResult<DishDeals> {
        val result = safeApiCall { client.get("api/offers/dishes").body<DishDealsDto>() }.map { it.toDomain() }
        if (result is AppResult.Success && result.value.dishes.isNotEmpty()) cache.value = result.value
        return result
    }

    private companion object {
        const val MAX_POLLS = 40
        const val POLL_INTERVAL_MS = 5_000L
    }
}

// --- DTO ---

@Serializable
private data class DishDealsDto(
    val dishes: List<DishDto> = emptyList(),
    val building: Boolean = false,
    val progress: ProgressDto? = null,
)

@Serializable
private data class ProgressDto(val done: Int = 0, val total: Int = 0)

@Serializable
private data class DishDto(
    val id: String,
    val rank: Int = 0,
    val icon: String? = null,
    val es: String = "",
    val ru: String? = null,
    val serves: Int = 4,
    val count: Int = 0,
    val byChain: Map<String, DishChainDto> = emptyMap(),
    val best: String? = null,
)

@Serializable
private data class DishChainDto(
    val total: Double = 0.0,
    val regular: Double = 0.0,
    val save: Double = 0.0,
    val found: Int = 0,
    val n: Int = 0,
    val discounted: Int = 0,
    val missing: List<JsonElement> = emptyList(),
    val items: List<DishItemDto> = emptyList(),
)

@Serializable
private data class DishItemDto(
    val id: String,
    val nameEs: String = "",
    val nameRu: String? = null,
    val found: Boolean = true,
    val product: String? = null,
    val image: String? = null,
    val packs: Double? = null,
    val cost: Double? = null,
    val regular: Double? = null,
    val offer: DishOfferDto? = null,
)

@Serializable
private data class DishOfferDto(val kind: String? = null, val pct: Double? = null)

private fun DishDealsDto.toDomain() = DishDeals(
    dishes = dishes.sortedBy { it.rank }.map { it.toDomain() }.distinctBy { it.id },
    building = building,
    progressDone = progress?.done ?: 0,
    progressTotal = progress?.total ?: 0,
)

private fun DishDto.toDomain(): Dish {
    val options = byChain.map { (chain, c) ->
        DishOption(
            chainKey = chain,
            chainName = Chains.nameOf(chain),
            total = c.total,
            regular = c.regular,
            save = c.save,
            found = c.found,
            count = if (c.n > 0) c.n else count,
            discounted = c.discounted,
            ingredients = c.items.map { it.toDomain() }.distinctBy { it.id },
        )
    }
        .filter { it.found > 0 }
        .sortedWith(compareByDescending<DishOption> { it.complete }.thenBy { it.total })
    return Dish(
        id = id,
        name = AppLanguage.pick(es, ru),
        icon = icon,
        serves = serves,
        ingredientCount = count,
        options = options,
        bestChainKey = best,
    )
}

private fun DishItemDto.toDomain() = DishIngredient(
    id = id,
    name = AppLanguage.pick(nameEs, nameRu),
    found = found,
    productName = product,
    imageUrl = image,
    packs = packs,
    cost = cost,
    regular = regular,
    offerPct = offer?.pct?.roundToInt()?.takeIf { it > 0 },
)
