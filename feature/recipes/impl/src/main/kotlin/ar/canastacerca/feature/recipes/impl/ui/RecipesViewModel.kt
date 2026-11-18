package ar.canastacerca.feature.recipes.impl.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.canastacerca.core.common.AppError
import ar.canastacerca.core.common.AppResult
import ar.canastacerca.feature.recipes.impl.data.Dish
import ar.canastacerca.feature.recipes.impl.data.RecipesRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class RecipesUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val dishes: List<Dish> = emptyList(),
    val building: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val error: AppError? = null,
)

sealed interface RecipesAction {
    data object Refresh : RecipesAction
    data object Retry : RecipesAction
}

@Inject
@ViewModelKey(RecipesViewModel::class)
@ContributesIntoMap(AppScope::class)
class RecipesViewModel(
    private val repository: RecipesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipesUiState())
    val state: StateFlow<RecipesUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(refreshing = false)
    }

    fun onAction(action: RecipesAction) {
        when (action) {
            RecipesAction.Refresh -> load(refreshing = true)
            RecipesAction.Retry -> load(refreshing = false)
        }
    }

    private fun load(refreshing: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = !refreshing, isRefreshing = refreshing, error = null) }
            repository.dishDeals().collect { result ->
                when (result) {
                    is AppResult.Success -> _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            dishes = result.value.dishes,
                            building = result.value.building,
                            progressDone = result.value.progressDone,
                            progressTotal = result.value.progressTotal,
                        )
                    }
                    is AppResult.Failure -> _state.update {
                        it.copy(isLoading = false, isRefreshing = false, error = result.error)
                    }
                }
            }
        }
    }
}
