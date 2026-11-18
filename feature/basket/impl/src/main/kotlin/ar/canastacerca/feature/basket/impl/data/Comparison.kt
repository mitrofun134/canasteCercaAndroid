package ar.canastacerca.feature.basket.impl.data

/** Результат сравнения корзины по магазинам рядом. */
data class Comparison(
    val itemCount: Int,
    /** Магазины, где нашёлся хотя бы один товар; порядок сервера: сначала полные, затем по сумме. */
    val stores: List<StoreQuote>,
    val bestStoreId: String?,
    /** Есть магазин, где нашлись все товары. */
    val hasCompleteStore: Boolean,
    /** Стоит ли делить покупку на два магазина (сервер уже решил, что выгода ≥ 2 %). */
    val pair: PairQuote?,
    val spread: Spread?,
    val unavailableNames: List<String>,
    /** Сети, чьи цены сервер ещё загружает: через несколько секунд стоит пересчитать. */
    val pendingChains: List<String>,
    val usedOnlineFallback: Boolean,
) {
    val best: StoreQuote? get() = stores.firstOrNull { it.storeId == bestStoreId }
}

data class StoreQuote(
    val storeId: String,
    val chainKey: String?,
    val chainName: String,
    val branchName: String?,
    val address: String?,
    val distanceKm: Double?,
    val isOnline: Boolean,
    val total: Double,
    val regularTotal: Double,
    val found: Int,
    val complete: Boolean,
    val missingNames: List<String>,
    /** Цена сегодня со скидкой по выбранным способам оплаты. */
    val payToday: PayDiscount?,
    /** Лучший день недели для этой корзины. */
    val payBest: PayDiscount?,
) {
    /** Сколько экономят акции магазина (2x1, сниженные цены). */
    val storeOffersSaving: Double get() = (regularTotal - total).coerceAtLeast(0.0)
}

data class PayDiscount(
    val finalTotal: Double,
    val pct: Double?,
    val dayName: String?,
)

data class PairQuote(
    val storeNames: List<String>,
    val total: Double,
    val saving: Double?,
    val coversMore: Boolean,
)

data class Spread(
    val cheapest: Double,
    val mostExpensive: Double,
    val saving: Double,
    val savingPct: Double,
)
