package ar.canastacerca.feature.catalog.impl.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.canastacerca.core.common.AppError
import ar.canastacerca.core.common.AppResult
import ar.canastacerca.feature.catalog.impl.data.CatalogGroup
import ar.canastacerca.feature.catalog.impl.data.CatalogRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class CatalogUiState(
    val isLoading: Boolean = true,
    val groups: List<CatalogGroup> = emptyList(),
    val error: AppError? = null,
)

sealed interface CatalogAction {
    data object Retry : CatalogAction
}

@Inject
@ViewModelKey(CatalogViewModel::class)
@ContributesIntoMap(AppScope::class)
class CatalogViewModel(
    private val repository: CatalogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onAction(action: CatalogAction) {
        when (action) {
            CatalogAction.Retry -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.groups()) {
                is AppResult.Success -> _state.update { it.copy(isLoading = false, groups = result.value) }
                is AppResult.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }
}
