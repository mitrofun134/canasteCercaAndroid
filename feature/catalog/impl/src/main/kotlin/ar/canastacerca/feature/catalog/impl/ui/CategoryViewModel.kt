package ar.canastacerca.feature.catalog.impl.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.canastacerca.core.common.AppError
import ar.canastacerca.core.common.AppResult
import ar.canastacerca.core.designsystem.component.UiMessage
import ar.canastacerca.feature.basket.api.BasketItem
import ar.canastacerca.feature.basket.api.BasketRepository
import ar.canastacerca.feature.catalog.api.CategoryKey
import ar.canastacerca.feature.catalog.impl.R
import ar.canastacerca.feature.catalog.impl.data.CatalogRepository
import ar.canastacerca.feature.catalog.impl.data.Product
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer

enum class ProductSort { Popular, Price, UnitPrice }

@Immutable
data class CategoryUiState(
    val title: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** Уже отфильтрованные поиском и отсортированные товары. */
    val products: List<Product> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val sort: ProductSort = ProductSort.Popular,
    val expandedId: String? = null,
    val pendingChains: List<String> = emptyList(),
    val building: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val basketIds: Set<String> = emptySet(),
    val error: AppError? = null,
    val message: UiMessage? = null,
)

sealed interface CategoryAction {
    data class QueryChanged(val text: String) : CategoryAction
    data class SortChanged(val sort: ProductSort) : CategoryAction
    data class ToggleExpanded(val id: String) : CategoryAction
    data class ToggleBasket(val product: Product) : CategoryAction
    data object Refresh : CategoryAction
    data object Retry : CategoryAction
    data class MessageShown(val id: Long) : CategoryAction
}

/**
 * ViewModel с аргументом навигации: ключ [CategoryKey] приходит через assisted injection Metro
 * (ManualViewModelAssistedFactory), остальные зависимости — из графа.
 */
@AssistedInject
class CategoryViewModel(
    @Assisted private val key: CategoryKey,
    private val repository: CatalogRepository,
    private val basketRepository: BasketRepository,
) : ViewModel() {

    private data class Content(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val products: List<Product> = emptyList(),
        val pendingChains: List<String> = emptyList(),
        val building: Boolean = false,
        val progressDone: Int = 0,
        val progressTotal: Int = 0,
        val error: AppError? = null,
    )

    private data class Controls(
        val query: String = "",
        val sort: ProductSort = ProductSort.Popular,
        val expandedId: String? = null,
        val message: UiMessage? = null,
    )

    private val content = MutableStateFlow(Content())
    private val controls = MutableStateFlow(Controls())

    val state: StateFlow<CategoryUiState> = combine(
        content,
        controls,
        basketRepository.items.map { items -> items.mapTo(HashSet()) { it.id } },
    ) { c, ui, basketIds ->
        CategoryUiState(
            title = key.title,
            isLoading = c.isLoading,
            isRefreshing = c.isRefreshing,
            products = c.products.filterBy(ui.query).applySort(ui.sort),
            totalCount = c.products.size,
            query = ui.query,
            sort = ui.sort,
            expandedId = ui.expandedId,
            pendingChains = c.pendingChains,
            building = c.building,
            progressDone = c.progressDone,
            progressTotal = c.progressTotal,
            basketIds = basketIds,
            error = c.error,
            message = ui.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryUiState(title = key.title))

    private var loadJob: Job? = null

    init {
        load(refreshing = false)
    }

    fun onAction(action: CategoryAction) {
        when (action) {
            is CategoryAction.QueryChanged -> controls.update { it.copy(query = action.text) }
            is CategoryAction.SortChanged -> controls.update { it.copy(sort = action.sort) }
            is CategoryAction.ToggleExpanded -> controls.update {
                it.copy(expandedId = if (it.expandedId == action.id) null else action.id)
            }
            is CategoryAction.ToggleBasket -> toggleBasket(action.product)
            CategoryAction.Refresh -> load(refreshing = true)
            CategoryAction.Retry -> load(refreshing = false)
            is CategoryAction.MessageShown -> controls.update {
                if (it.message?.id == action.id) it.copy(message = null) else it
            }
        }
    }

    private fun load(refreshing: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            content.update { it.copy(isLoading = !refreshing, isRefreshing = refreshing, error = null) }
            repository.category(key.categoryId).collect { result ->
                when (result) {
                    is AppResult.Success -> content.update {
                        val page = result.value
                        Content(
                            isLoading = false,
                            isRefreshing = false,
                            products = page.products,
                            pendingChains = page.pendingChains,
                            building = page.building,
                            progressDone = page.progressDone,
                            progressTotal = page.progressTotal,
                        )
                    }
                    is AppResult.Failure -> content.update {
                        it.copy(isLoading = false, isRefreshing = false, error = result.error)
                    }
                }
            }
        }
    }

    private fun toggleBasket(product: Product) {
        viewModelScope.launch {
            val message = if (product.id in state.value.basketIds) {
                basketRepository.remove(product.id)
                R.string.catalog_removed
            } else {
                val added = basketRepository.add(
                    BasketItem(id = product.id, name = product.name, imageUrl = product.imageUrl, scope = product.scope),
                )
                if (added) R.string.catalog_added else R.string.catalog_basket_full
            }
            controls.update { it.copy(message = UiMessage(message)) }
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(key: CategoryKey): CategoryViewModel
    }
}

private fun List<Product>.filterBy(query: String): List<Product> {
    val q = query.normalized()
    if (q.isBlank()) return this
    return filter { p -> listOfNotNull(p.name, p.brand, p.presentation).any { it.normalized().contains(q) } }
}

private fun List<Product>.applySort(sort: ProductSort): List<Product> = when (sort) {
    ProductSort.Popular -> sortedByDescending { it.popularity }
    ProductSort.Price -> sortedBy { it.minPrice ?: Double.MAX_VALUE }
    ProductSort.UnitPrice -> sortedBy { it.unitPrice ?: Double.MAX_VALUE }
}

/** «Café» и «cafe» — одно и то же: без регистра и диакритики. */
private fun String.normalized(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .trim()
