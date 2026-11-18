package ar.canastacerca.core.model

/** Цена товара в одной сети. */
data class ChainPrice(
    val chainKey: String,
    val chainName: String,
    val price: Double?,
    /** Зачёркнутая цена, если сеть снизила цену. */
    val listPrice: Double?,
    val available: Boolean,
    val qtyOffer: QtyOffer?,
    val url: String?,
) {
    val discountPct: Int?
        get() = if (price != null && listPrice != null && listPrice > price) {
            (((listPrice - price) / listPrice) * 100).toInt()
        } else {
            null
        }
}

/** Акция «больше — дешевле»: 2x1, 3x2, «2do al 70%». */
data class QtyOffer(
    val text: String,
    val minQty: Int,
    /** Цена одной штуки, если взять minQty. */
    val unitPrice: Double?,
)

/** Где продаётся товар: супермаркеты, аптеки или и там и там (сервер: super / pharmacy / all). */
enum class ItemScope(val wire: String) {
    Super("super"),
    Pharmacy("pharmacy"),
    All("all"),
    ;

    companion object {
        fun from(wire: String?): ItemScope = entries.firstOrNull { it.wire == wire } ?: Super
    }
}
