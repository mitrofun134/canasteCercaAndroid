package ar.canastacerca.feature.catalog.impl.data

import kotlinx.serialization.Serializable

// GET /api/catalog/categories → lib/catalog.js categories()

@Serializable
data class CategoriesDto(val groups: List<GroupDto> = emptyList())

@Serializable
data class GroupDto(
    val id: String,
    val es: String = "",
    val ru: String? = null,
    val icon: String? = null,
    val food: Boolean = true,
    val pharmacy: Boolean = false,
    val cats: List<CatDto> = emptyList(),
)

@Serializable
data class CatDto(
    val id: String,
    val es: String = "",
    val ru: String? = null,
    val scope: String? = null,
)

// GET /api/catalog?cat=yerba → lib/catalog.js category() / finish()
// GET /api/catalog/all       → lib/popular.js page()

@Serializable
data class CategoryPageDto(
    val category: CategoryInfoDto? = null,
    val items: List<ProductDto> = emptyList(),
    val pending: List<PendingDto> = emptyList(),
    val building: Boolean = false,
    val progress: ProgressDto? = null,
)

@Serializable
data class CategoryInfoDto(
    val id: String = "",
    val es: String = "",
    val ru: String? = null,
    val scope: String? = null,
    val pharmacy: Boolean = false,
)

@Serializable
data class PendingDto(val chain: String? = null, val chainName: String? = null)

@Serializable
data class ProgressDto(val done: Int = 0, val total: Int = 0)

@Serializable
data class ProductDto(
    val id: String,
    val name: String = "",
    val brand: String? = null,
    val presentation: String? = null,
    val image: String? = null,
    val prices: List<ProductPriceDto> = emptyList(),
    val minPrice: Double? = null,
    val minChain: String? = null,
    val unitPrice: Double? = null,
    val unitLabel: String? = null,
    val nutrition: NutritionDto? = null,
    val rx: Boolean = false,
    val pop: Double? = null,
    val chains: Int = 0,
    /** Есть у товаров раздела «Всё». */
    val scope: String? = null,
)

@Serializable
data class ProductPriceDto(
    val chain: String,
    val chainName: String? = null,
    val price: Double? = null,
    val available: Boolean = true,
    val url: String? = null,
    val listPrice: Double? = null,
    val qtyOffer: QtyOfferDto? = null,
)

@Serializable
data class QtyOfferDto(
    val text: String? = null,
    val minQty: Int? = null,
    val unitPrice: Double? = null,
)

/** lib/nutrition.js: знаки «EXCESO EN…» (seals) и сертификаты (certs: sin_tacc, vegano…). */
@Serializable
data class NutritionDto(
    val known: Boolean = false,
    val seals: List<String> = emptyList(),
    val certs: List<String> = emptyList(),
)
