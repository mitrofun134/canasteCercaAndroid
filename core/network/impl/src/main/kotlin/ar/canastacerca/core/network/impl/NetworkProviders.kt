package ar.canastacerca.core.network.impl

import android.util.Log
import ar.canastacerca.core.network.NetworkConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * Единственный HttpClient приложения. Фичи получают его через DI и сами описывают свои запросы.
 */
@ContributesTo(AppScope::class)
interface NetworkProviders {

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(config: NetworkConfig): HttpClient = HttpClient(OkHttp) {
        expectSuccess = true

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    coerceInputValues = true
                    isLenient = true
                },
            )
        }
        // Сервер отдаёт списки скидок и рецептов с ETag: повторный запрос получает 304 без тела
        install(HttpCache)
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            // Расчёт корзины опрашивает сайты всех сетей — бывает долгим
            requestTimeoutMillis = 90_000
            socketTimeoutMillis = 90_000
        }
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("CanastaHttp", message)
                }
            }
        }
        defaultRequest {
            url(config.baseUrl)
            // Доступ по ссылке (lib/access.js): сервер ждёт ключ в cookie cc_k
            config.accessKey?.takeIf { it.isNotBlank() }?.let { key ->
                header(HttpHeaders.Cookie, "cc_k=$key")
            }
        }
    }
}
