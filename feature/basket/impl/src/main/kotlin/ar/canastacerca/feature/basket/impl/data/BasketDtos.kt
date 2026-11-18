package ar.canastacerca.feature.basket.impl.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// POST /api/basket — server.js → lib/sources.js basket() + lib/basket.js computeBasket() + lib/promos.js apply()

@Serializable
data class BasketRequestDto(
    val lat: Double,
    val lng: Double,
    val radiusKm: Double,
    val items: List<BasketRequestItemDto>,
    val payments: List<String>,
    /** promo — применять акции магазина, если выполнено условие (2x1, 3x2…). */
    val mode: String = "promo",
)

@Serializable
data class BasketRequestItemDto(
    val id: String,
    val qty: Int,
    val scope: String,
    val name: String,
)

@Serializable
data class BasketResultDto(
    val itemCount: Int = 0,
    val storeCount: Int = 0,
    val unavailable: List<String> = emptyList(),
    val ranking: List<StoreTotalDto> = emptyList(),
    val bestSingle: StoreTotalDto? = null,
    val bestCoverage: StoreTotalDto? = null,
    val bestPair: PairDto? = null,
    val spread: SpreadDto? = null,
    val notices: List<NoticeDto> = emptyList(),
    val sourceUsed: String? = null,
    val promos: PromosDto? = null,
)

@Serializable
data class StoreTotalDto(
    val storeId: String,
    val store: StoreDto? = null,
    val distanceKm: Double? = null,
    val total: Double = 0.0,
    val regularTotal: Double = 0.0,
    val found: Int = 0,
    val complete: Boolean = false,
    val missing: List<String> = emptyList(),
)

@Serializable
data class StoreDto(
    val id: String,
    /** Название сети («Día», «Jumbo»). */
    val chain: String? = null,
    /** Название филиала. */
    val name: String? = null,
    val address: String? = null,
    val distanceKm: Double? = null,
    /** branch | online */
    val kind: String? = null,
    /** Ключ сети на сайте (jumbo, dia…) — есть у филиалов OSM и онлайн-магазинов. */
    val vtexChain: String? = null,
)

@Serializable
data class PairDto(
    val stores: List<StoreDto> = emptyList(),
    val total: Double = 0.0,
    val found: Int = 0,
    val complete: Boolean = false,
    val savingVsSingle: Double? = null,
    val coversMore: Boolean = false,
    val worthIt: Boolean = false,
)

@Serializable
data class SpreadDto(
    val cheapest: Double = 0.0,
    val mostExpensive: Double = 0.0,
    val saving: Double = 0.0,
    val savingPct: Double = 0.0,
)

@Serializable
data class NoticeDto(
    /** SEPA_DOWN | CHAIN_ERROR | CHAIN_PENDING | GEN_NEEDS_WEB */
    val code: String,
    val chain: String? = null,
    val message: String? = null,
)

@Serializable
data class PromosDto(
    val perStore: Map<String, StorePromoDto> = emptyMap(),
)

@Serializable
data class StorePromoDto(
    val total: Double? = null,
    val today: PromoDayDto? = null,
    val best: PromoDayDto? = null,
)

@Serializable
data class PromoDayDto(
    @SerialName("final") val finalTotal: Double? = null,
    val pct: Double? = null,
    val discount: Double? = null,
    val dayName: String? = null,
    val offset: Int? = null,
)
