package ar.canastacerca.feature.payments.impl.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.canastacerca.core.common.AppError
import ar.canastacerca.core.common.AppResult
import ar.canastacerca.feature.payments.api.PaymentsRepository
import ar.canastacerca.feature.payments.impl.data.PaymentKind
import ar.canastacerca.feature.payments.impl.data.PaymentMethod
import ar.canastacerca.feature.payments.impl.data.PaymentMethodsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class PaymentGroup(val kind: PaymentKind, val methods: List<PaymentMethod>)

@Immutable
data class PaymentsUiState(
    val isLoading: Boolean = true,
    val groups: List<PaymentGroup> = emptyList(),
    val selected: Set<String> = emptySet(),
    val error: AppError? = null,
)

sealed interface PaymentsAction {
    data class Toggle(val id: String) : PaymentsAction
    data object ClearAll : PaymentsAction
    data object Retry : PaymentsAction
}

@Inject
@ViewModelKey(PaymentsViewModel::class)
@ContributesIntoMap(AppScope::class)
class PaymentsViewModel(
    private val methodsRepository: PaymentMethodsRepository,
    private val paymentsRepository: PaymentsRepository,
) : ViewModel() {

    private data class Content(
        val isLoading: Boolean = true,
        val groups: List<PaymentGroup> = emptyList(),
        val error: AppError? = null,
    )

    private val content = MutableStateFlow(Content())

    val state: StateFlow<PaymentsUiState> = combine(content, paymentsRepository.selected) { c, selected ->
        PaymentsUiState(isLoading = c.isLoading, groups = c.groups, selected = selected, error = c.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentsUiState())

    init {
        load()
    }

    fun onAction(action: PaymentsAction) {
        when (action) {
            is PaymentsAction.Toggle -> viewModelScope.launch { paymentsRepository.toggle(action.id) }
            PaymentsAction.ClearAll -> viewModelScope.launch { paymentsRepository.setSelected(emptySet()) }
            PaymentsAction.Retry -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            content.update { it.copy(isLoading = true, error = null) }
            when (val result = methodsRepository.methods()) {
                is AppResult.Success -> content.value = Content(
                    isLoading = false,
                    groups = result.value
                        .groupBy { it.kind }
                        .toSortedMap(compareBy<PaymentKind> { it.ordinal })
                        .map { (kind, methods) -> PaymentGroup(kind, methods.sortedBy { it.name }) },
                )
                is AppResult.Failure -> content.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }
}
