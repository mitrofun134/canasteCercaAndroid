package ar.canastacerca.core.common

/** Ошибка, понятная UI-слою: без исключений Ktor и сериализации. */
sealed interface AppError {
    /** Нет сети или сервер недоступен. */
    data object Network : AppError

    /** Сервер открыт по ссылке с ключом (npm run share), а ключа нет или он неверный. */
    data object Unauthorized : AppError

    /** Сервер ответил ошибкой; message — текст из поля `error` ответа, если есть. */
    data class Server(val status: Int, val message: String?) : AppError

    data class Unknown(val message: String?) : AppError
}

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value

fun <T> AppResult<T>.errorOrNull(): AppError? = (this as? AppResult.Failure)?.error
