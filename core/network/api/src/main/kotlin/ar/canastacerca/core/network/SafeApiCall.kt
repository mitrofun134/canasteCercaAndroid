package ar.canastacerca.core.network

import ar.canastacerca.core.common.AppError
import ar.canastacerca.core.common.AppResult
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Выполняет запрос и переводит исключения в [AppError]. Отмену корутины не глотает.
 * Клиент настроен с expectSuccess = true, поэтому ответы 4xx/5xx приходят как [ResponseException].
 */
suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResponseException) {
        val status = e.response.status
        if (status == HttpStatusCode.Unauthorized) {
            AppResult.Failure(AppError.Unauthorized)
        } else {
            val body = runCatching { e.response.bodyAsText() }.getOrNull()
            AppResult.Failure(AppError.Server(status.value, body?.let(::serverMessage)))
        }
    } catch (e: IOException) {
        AppResult.Failure(AppError.Network)
    } catch (e: Exception) {
        AppResult.Failure(AppError.Unknown(e.message))
    }

/** Сервер отвечает ошибками вида {"error":"Escribí al menos 2 letras"}. */
private fun serverMessage(body: String): String? =
    Regex("\"error\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(body)?.groupValues?.get(1)
