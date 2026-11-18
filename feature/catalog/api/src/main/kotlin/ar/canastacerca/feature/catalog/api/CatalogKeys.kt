package ar.canastacerca.feature.catalog.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Вкладка «Каталог»: разделы и подразделы. */
@Serializable
data object CatalogKey : NavKey

/** Товары одного подраздела. [categoryId] = "all" — раздел «Всё» (самые покупаемые). */
@Serializable
data class CategoryKey(
    val categoryId: String,
    val title: String,
) : NavKey {
    companion object {
        const val ALL = "all"
    }
}
