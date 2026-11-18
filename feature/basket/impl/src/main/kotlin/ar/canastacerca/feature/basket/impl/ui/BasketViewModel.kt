package ar.canastacerca.feature.basket.impl.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.canastacerca.core.common.AppError
import ar.canastacerca.core.common.AppResult
import ar.canastacerca.feature.basket.api.BasketItem
import ar.canastacerca.feature.basket.api.BasketRepository
import ar.canastacerca.feature.basket.impl.data.BasketPricingRepository
import ar.canastacerca.feature.basket.impl.data.CompareRequest
import ar.canastacerca.feature.basket.impl.data.Comparison
import ar.canastacerca.feature.location.api.Address
import ar.canastacerca.feature.location.api.LocationRepository
import ar.canastacerca.feature.payments.api.PaymentsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class BasketUiState(
    val items: List<BasketItem> = emptyList(),
    val address: Address? = null,
    val radiusKm: Double = LocationRepository.DEFAULT_RADIUS_KM,
    val paymentsCount: Int = 0,
    val comparison: ComparisonState = ComparisonState.Idle,
)

sealed interface ComparisonState {
    /** Корзина пуста. */
    data object Idle : ComparisonState

    data object NeedsAddress : ComparisonState

    /** Считаем; [previous] показываем приглушённо, чтобы экран не прыгал. */
    data class Loading(val previous: Comparison?) : ComparisonState

    data class Ready(val comparison: Comparison) : ComparisonState

    data class Failed(val error: AppError, val previous: Comparison?) : ComparisonState
}

sealed interface BasketAction {
    data class Increase(val id: String) : BasketAction
    data class Decrease(val id: String) : BasketAction
    data class Remove(val id: String) : BasketAction
    data object Clear : BasketAction
    data class SetRadius(val km: Double) : BasketAction
    data object Retry : BasketAction
}

@Inject
@ViewModelKey(BasketViewModel::class)
@ContributesIntoMap(AppScope::class)
class BasketViewModel(
    private val basketRepository: BasketRepository,
    private val locationRepository: LocationRepository,
    private val paymentsRepository: PaymentsRepository,
    private val pricingRepository: BasketPricingRepository,
) : ViewModel() {

    private val comparisonState = MutableStateFlow<ComparisonState>(ComparisonState.Idle)
    private val retryTrigger = MutableStateFlow(0)

    val state: StateFlow<BasketUiState> = combine(
        basketRepository.items,
        locationRepository.address,
        locationRepository.radiusKm,
        paymentsRepository.selected,
        comparisonState,
    ) { items, address, radius, payments, comparison ->
        BasketUiState(
            items = items,
            address = address,
            radiusKm = radius,
            paymentsCount = payments.size,
            comparison = comparison,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BasketUiState())

    init {
        observeInputs()
    }

    fun onAction(action: BasketAction) {
        when (action) {
            is BasketAction.Increase -> changeQuantity(action.id, +1)
            is BasketAction.Decrease -> changeQuantity(action.id, -1)
            is BasketAction.Remove -> viewModelScope.launch { basketRepository.remove(action.id) }
            BasketAction.Clear -> viewModelScope.launch { basketRepository.clear() }
            is BasketAction.SetRadius -> viewModelScope.launch { locationRepository.setRadius(action.km) }
            BasketAction.Retry -> retryTrigger.update { it + 1 }
        }
    }

    private fun changeQuantity(id: String, delta: Int) {
        val current = state.value.items.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { basketRepository.setQuantity(id, current.quantity + delta) }
    }

    private data class Inputs(
        val items: List<BasketItem>,
        val address: Address?,
        val radiusKm: Double,
        val payments: Set<String>,
    )

    /** Любое изменение корзины, адреса, радиуса или способов оплаты — пересчёт (с паузой 600 мс). */
    @OptIn(FlowPreview::class)
    private fun observeInputs() {
        viewModelScope.launch {
            var lastInputs: Inputs? = null
            var pendingRetries = 0
            combine(
                basketRepository.items,
                locationRepository.address,
                locationRepository.radiusKm,
                paymentsRepository.selected,
                retryTrigger,
            ) { items, address, radius, payments, retry -> Inputs(items, address, radius, payments) to retry }
                .distinctUntilChanged()
                .debounce(600)
                .collectLatest { (inputs, _) ->
                    if (inputs != lastInputs) {
                        lastInputs = inputs
                        pendingRetries = 0
                    }
                    val pending = compare(inputs)
                    // Сервер ещё грузит цены некоторых сетей — пересчитываем сами, как веб-версия
                    if (pending && pendingRetries < MAX_PENDING_RETRIES) {
                        pendingRetries++
                        delay(PENDING_RETRY_MS)
                        retryTrigger.update { it + 1 }
                    }
                }
        }
    }

    /** @return true, если в ответе остались сети «ещё загружается». */
    private suspend fun compare(inputs: Inputs): Boolean {
        if (inputs.items.isEmpty()) {
            comparisonState.value = ComparisonState.Idle
            return false
        }
        val address = inputs.address ?: run {
            comparisonState.value = ComparisonState.NeedsAddress
            return false
        }
        val previous = comparisonState.value.lastComparison()
        comparisonState.value = ComparisonState.Loading(previous)
        val request = CompareRequest(inputs.items, address, inputs.radiusKm, inputs.payments)
        return when (val result = pricingRepository.compare(request)) {
            is AppResult.Success -> {
                comparisonState.value = ComparisonState.Ready(result.value)
                result.value.pendingChains.isNotEmpty()
            }
            is AppResult.Failure -> {
                comparisonState.value = ComparisonState.Failed(result.error, previous)
                false
            }
        }
    }

    private fun ComparisonState.lastComparison(): Comparison? = when (this) {
        is ComparisonState.Ready -> this.comparison
        is ComparisonState.Loading -> previous
        is ComparisonState.Failed -> previous
        else -> null
    }

    private companion object {
        const val MAX_PENDING_RETRIES = 3
        const val PENDING_RETRY_MS = 6_000L
    }
}
