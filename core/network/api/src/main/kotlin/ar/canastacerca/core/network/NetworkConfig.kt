package ar.canastacerca.core.network

/**
 * Адрес сервера Canasta Cerca и ключ доступа (если сервер открыт через `npm run share`).
 * Значения задаёт :app из gradle.properties (canasta.baseUrl, canasta.accessKey).
 */
data class NetworkConfig(
    val baseUrl: String,
    val accessKey: String?,
)
