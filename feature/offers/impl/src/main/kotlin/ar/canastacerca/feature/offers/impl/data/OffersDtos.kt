package ar.canastacerca.feature.offers.impl.data

import kotlinx.serialization.Serializable

// GET /api/offers/top?n=100&chain=dia → lib/storeoffers.js top()

@Serializable
data class TopOffersDto(
    val items: List<TopOfferItemDto> = emptyList(),
    val total: Int = 0,
    val counts: Map<String, Int> = emptyMap(),
    val updatedAt: Long? = null,
    val building: Boolean = false,
    val verifying: Boolean = false,
    val progress: ProgressDto? = null,
)

@Serializable
data class ProgressDto(val done: Int = 0, val total: Int = 0)

@Serializable
data class TopOfferItemDto(
    val id: String,
    val name: String = "",
    val brand: String? = null,
    val presentation: String? = null,
    val image: String? = null,
    val catEs: String? = null,
    val catRu: String? = null,
    val scope: String? = null,
    val prices: List<OfferPriceDto> = emptyList(),
    val offer: OfferDto? = null,
)

@Serializable
data class OfferPriceDto(
    val chain: String,
    val chainName: String? = null,
    val price: Double? = null,
    val listPrice: Double? = null,
    val qtyOffer: QtyOfferDto? = null,
)

@Serializable
data class QtyOfferDto(
    val text: String? = null,
    val minQty: Int? = null,
    val factor: Double? = null,
    val unitPrice: Double? = null,
    val until: String? = null,
)

@Serializable
data class OfferDto(
    val chain: String,
    val chainName: String? = null,
    /** unit — сниженная цена; qty — «больше — дешевле» (2x1, 3x2…). */
    val kind: String = "unit",
    val price: Double,
    val listPrice: Double? = null,
    val unitPrice: Double? = null,
    val minQty: Int? = null,
    val text: String? = null,
    val until: String? = null,
    val pct: Double = 0.0,
    val url: String? = null,
    val save: Double? = null,
)
