package ar.canastacerca.feature.offers.impl.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.canastacerca.core.common.AppError
import ar.canastacerca.core.common.AppResult
import ar.canastacerca.core.designsystem.component.UiMessage
import ar.canastacerca.core.model.Chains
import ar.canastacerca.feature.basket.api.BasketItem
import ar.canastacerca.feature.basket.api.BasketRepository
import ar.canastacerca.feature.offers.impl.R
import ar.canastacerca.feature.offers.impl.data.OfferItem
import ar.canastacerca.feature.offers.impl.data.OffersRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ChainFilter(val key: String, val name: String, val count: Int)

@Immutable
data class OffersUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val offers: List<OfferItem> = emptyList(),
    val total: Int = 0,
    val chains: List<ChainFilter> = emptyList(),
    val selectedChain: String? = null,
    val building: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val basketIds: Set<String> = emptySet(),
    val error: AppError? = null,
    val message: UiMessage? = null,
)

sealed interface OffersAction {
    data class SelectChain(val chainKey: String?) : OffersAction
    data object Refresh : OffersAction
    data object Retry : OffersAction
    data class ToggleBasket(val item: OfferItem) : OffersAction
    data class MessageShown(val id: Long) : OffersAction
}

@Inject
@ViewModelKey(OffersViewModel::class)
@ContributesIntoMap(AppScope::class)
class OffersViewModel(
    private val offersRepository: OffersRepository,
    private val basketRepository: BasketRepository,
) : ViewModel() {

    /** Часть состояния, которой владеет экран; что лежит в корзине — из общего репозитория. */
    private val content = MutableStateFlow(OffersUiState())

    val state: StateFlow<OffersUiState> = combine(
        content,
        basketRepository.items.map { items -> items.mapTo(HashSet()) { it.id } },
    ) { screen, basketIds ->
        screen.copy(basketIds = basketIds)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OffersUiState())

    private var loadJob: Job? = null

    init {
        load(chain = null, refreshing = false)
    }

    fun onAction(action: OffersAction) {
        when (action) {
            is OffersAction.SelectChain -> if (action.chainKey != content.value.selectedChain) {
                load(action.chainKey, refreshing = false)
            }
            OffersAction.Refresh -> load(content.value.selectedChain, refreshing = true)
            OffersAction.Retry -> load(content.value.selectedChain, refreshing = false)
            is OffersAction.ToggleBasket -> toggleBasket(action.item)
            is OffersAction.MessageShown -> content.update {
                if (it.message?.id == action.id) it.copy(message = null) else it
            }
        }
    }

    private fun load(chain: String?, refreshing: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            content.update {
                it.copy(
                    selectedChain = chain,
                    isLoading = !refreshing,
                    isRefreshing = refreshing,
                    error = null,
                    offers = if (chain != it.selectedChain) emptyList() else it.offers,
                )
            }
            offersRepository.topOffers(chain).collect { result ->
                when (result) {
                    is AppResult.Success -> content.update { current ->
                        val top = result.value
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            offers = top.items,
                            total = top.total,
                            // Счётчики сетей сервер отдаёт по всем акциям дня, даже при фильтре
                            chains = top.counts.entries
                                .sortedByDescending { it.value }
                                .map { (key, count) -> ChainFilter(key, Chains.nameOf(key), count) },
                            building = top.building,
                            progressDone = top.progressDone,
                            progressTotal = top.progressTotal,
                        )
                    }
                    is AppResult.Failure -> content.update {
                        it.copy(isLoading = false, isRefreshing = false, error = result.error)
                    }
                }
            }
        }
    }

    private fun toggleBasket(item: OfferItem) {
        viewModelScope.launch {
            if (item.id in state.value.basketIds) {
                basketRepository.remove(item.id)
                content.update { it.copy(message = UiMessage(R.string.offers_removed)) }
            } else {
                val added = basketRepository.add(
                    BasketItem(id = item.id, name = item.name, imageUrl = item.imageUrl, scope = item.scope),
                )
                val text = if (added) R.string.offers_added else R.string.offers_basket_full
                content.update { it.copy(message = UiMessage(text)) }
            }
        }
    }
}
