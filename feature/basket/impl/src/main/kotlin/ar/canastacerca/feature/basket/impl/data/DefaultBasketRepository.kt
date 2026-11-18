package ar.canastacerca.feature.basket.impl.data

import ar.canastacerca.core.model.ItemScope
import ar.canastacerca.core.storage.KeyValueStore
import ar.canastacerca.feature.basket.api.BasketItem
import ar.canastacerca.feature.basket.api.BasketRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultBasketRepository(
    private val store: KeyValueStore,
) : BasketRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override val items: Flow<List<BasketItem>> = store.observe(KEY).map(::decode)

    override suspend fun add(item: BasketItem): Boolean {
        var accepted = false
        mutate { current ->
            when {
                current.any { it.id == item.id } -> current.also { accepted = true }
                current.size >= BasketRepository.MAX_ITEMS -> current
                else -> (current + item).also { accepted = true }
            }
        }
        return accepted
    }

    override suspend fun addAll(items: List<BasketItem>): Int {
        var added = 0
        mutate { current ->
            val ids = current.mapTo(HashSet()) { it.id }
            val fresh = items.filter { ids.add(it.id) }
                .take((BasketRepository.MAX_ITEMS - current.size).coerceAtLeast(0))
            added = fresh.size
            current + fresh
        }
        return added
    }

    override suspend fun remove(id: String) = mutate { current -> current.filterNot { it.id == id } }

    override suspend fun setQuantity(id: String, quantity: Int) = mutate { current ->
        if (quantity <= 0) {
            current.filterNot { it.id == id }
        } else {
            val q = quantity.coerceAtMost(BasketRepository.MAX_QUANTITY)
            current.map { if (it.id == id) it.copy(quantity = q) else it }
        }
    }

    override suspend fun clear() {
        store.put(KEY, null)
    }

    private suspend fun mutate(transform: (List<BasketItem>) -> List<BasketItem>) {
        store.update(KEY) { raw -> encode(transform(decode(raw))) }
    }

    private fun decode(raw: String?): List<BasketItem> =
        raw?.let { runCatching { json.decodeFromString(ListSerializer(StoredItem.serializer()), it) }.getOrNull() }
            ?.map { it.toDomain() }
            .orEmpty()

    private fun encode(items: List<BasketItem>): String =
        json.encodeToString(ListSerializer(StoredItem.serializer()), items.map { StoredItem.from(it) })

    @Serializable
    private data class StoredItem(
        val id: String,
        val name: String,
        val image: String? = null,
        val qty: Int = 1,
        val scope: String = ItemScope.Super.wire,
    ) {
        fun toDomain() = BasketItem(id = id, name = name, imageUrl = image, quantity = qty, scope = ItemScope.from(scope))

        companion object {
            fun from(item: BasketItem) = StoredItem(item.id, item.name, item.imageUrl, item.quantity, item.scope.wire)
        }
    }

    private companion object {
        const val KEY = "basket.items"
    }
}
