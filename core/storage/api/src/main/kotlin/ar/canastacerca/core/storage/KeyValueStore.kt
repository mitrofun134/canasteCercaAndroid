package ar.canastacerca.core.storage

import kotlinx.coroutines.flow.Flow

/**
 * Простое хранилище строк по ключу. Фичи кладут сюда JSON своих данных
 * (корзина, адрес, способы оплаты) и сами решают, как его разбирать.
 */
interface KeyValueStore {
    fun observe(key: String): Flow<String?>

    suspend fun get(key: String): String?

    /** null удаляет значение. */
    suspend fun put(key: String, value: String?)

    /** Атомарно меняет значение: transform получает текущее и возвращает новое. */
    suspend fun update(key: String, transform: (String?) -> String?)
}
