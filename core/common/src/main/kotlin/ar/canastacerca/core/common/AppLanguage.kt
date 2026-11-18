package ar.canastacerca.core.common

import java.util.Locale

/**
 * Сервер отдаёт названия на испанском и русском (`es` / `ru`, `nameEs` / `nameRu`).
 * Интерфейс приложения — на языке системы: русский, если он выбран, иначе испанский.
 */
object AppLanguage {
    val isRussian: Boolean
        get() = Locale.getDefault().language == "ru"

    fun pick(es: String?, ru: String?): String =
        if (isRussian && !ru.isNullOrBlank()) ru else es.orEmpty()
}
