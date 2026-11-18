package ar.canastacerca.feature.basket.api

import androidx.navigation3.runtime.NavKey
import ar.canastacerca.core.model.ItemScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Вкладка «Корзина»: список и где его купить дешевле. */
@Serializable
data object BasketKey : NavKey

/**
 * Товар в корзине. [id] — штрихкод (EAN), `vtex:…` для товаров без штрихкода
 * или `gen:…` для ингредиентов блюд («мясо для миланесы, 1 кг»).
 */
data class BasketItem(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val quantity: Int = 1,
    val scope: ItemScope = ItemScope.Super,
)

/** Корзина общая для всех фич: каталог, скидки и рецепты кладут сюда товары. */
interface BasketRepository {
    val items: Flow<List<BasketItem>>

    /** Добавляет товар; если он уже есть — ничего не меняет. false — корзина заполнена. */
    suspend fun add(item: BasketItem): Boolean

    /** Добавляет то, чего ещё нет. Возвращает, сколько товаров добавлено. */
    suspend fun addAll(items: List<BasketItem>): Int

    suspend fun remove(id: String)
    suspend fun setQuantity(id: String, quantity: Int)
    suspend fun clear()

    companion object {
        /** Сервер считает не больше 40 товаров за раз (MAX_ITEMS в server.js). */
        const val MAX_ITEMS: Int = 40
        const val MAX_QUANTITY: Int = 99
    }
}
